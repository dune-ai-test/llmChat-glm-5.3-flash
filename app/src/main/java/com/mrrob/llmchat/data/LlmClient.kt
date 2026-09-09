package com.mrrob.llmchat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One message in the wire format (OpenAI chat style). */
data class WireMessage(val role: String, val content: String)

/** A connection with its secret resolved, ready to be used by [LlmClient]. */
data class ResolvedApi(
    val name: String,
    val baseUrl: String,
    val apiKey: String,
    val endpointPath: String = "/chat/completions",
    val customHeaders: Map<String, String> = emptyMap(),
    val apiVersion: String = ""
)

data class ChatResult(
    val text: String,
    val latencyMs: Long,
    val tokensIn: Int = -1,
    val tokensOut: Int = -1,
    val finishReason: String = ""
)

enum class ApiErrorKind { INVALID_KEY, RATE_LIMIT, MODEL_NOT_FOUND, NETWORK, TIMEOUT, SERVER, INVALID_RESPONSE, CONTEXT_TOO_LARGE, OFFLINE }

class ApiError(
    val kind: ApiErrorKind,
    override val message: String,
    val detail: String = "",
    val retryAfterSec: Int? = null
) : Exception(message)

enum class TestStep { CONNECT, AUTHENTICATE, CHECK_MODEL }
enum class TestStepState { RUNNING, DONE, FAILED }

data class TestOutcome(val latencyMs: Long, val models: List<String>)

/**
 * Provider-agnostic OpenAI-compatible client. Handles auth, headers,
 * timeout, streaming (SSE), model listing and the staged connection test,
 * translating every failure into a friendly [ApiError].
 */
class LlmClient(private val settings: SettingsStore) {

    private val clients = ConcurrentHashMap<Int, OkHttpClient>()

    private fun client(): OkHttpClient {
        val timeout = settings.settings.value.requestTimeoutSec.coerceIn(5, 600)
        return clients.getOrPut(timeout) {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(timeout.toLong(), TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun chatUrl(api: ResolvedApi): String {
        val base = api.baseUrl.trim().trimEnd('/')
        val withV1 = if (base.substringAfterLast('/').equals("v1", true)) base else "$base/v1"
        var path = api.endpointPath.trim()
        if (path.isEmpty()) path = "/chat/completions"
        if (!path.startsWith("/")) path = "/$path"
        if (path.startsWith("/v1/")) path = path.removePrefix("/v1")
        return withV1 + path
    }

    private fun modelsUrl(api: ResolvedApi): String = chatUrl(api).removeSuffix("/chat/completions") + "/models"

    private fun newBuilder(api: ResolvedApi): Request.Builder {
        val builder = Request.Builder()
        builder.header("Accept", "application/json")
        if (api.apiKey.isNotBlank()) builder.header("Authorization", "Bearer ${api.apiKey}")
        if (api.apiVersion.isNotBlank()) builder.header("OpenAI-Beta", api.apiVersion)
        api.customHeaders.forEach { (k, v) -> if (k.isNotBlank()) builder.header(k, v) }
        return builder
    }

    private fun buildPayload(
        api: ResolvedApi,
        model: String,
        messages: List<WireMessage>,
        stream: Boolean,
        systemPrompt: String?
    ): JSONObject {
        val s = settings.settings.value
        val payload = JSONObject()
        payload.put("model", model)
        payload.put("messages", JSONArray().apply {
            val system = systemPrompt?.takeIf { it.isNotBlank() } ?: s.systemPrompt.takeIf { it.isNotBlank() }
            if (system != null) put(JSONObject().put("role", "system").put("content", system))
            messages.forEach { m -> put(JSONObject().put("role", m.role).put("content", m.content)) }
        })
        payload.put("stream", stream)
        if (s.temperature >= 0) payload.put("temperature", s.temperature.toDouble())
        if (s.maxTokens > 0) payload.put("max_tokens", s.maxTokens)
        // Merge user-defined raw parameters (only explicitly enabled ones).
        try {
            if (s.rawParams.isNotBlank()) {
                val raw = JSONObject(s.rawParams)
                raw.keys().forEach { key -> payload.put(key, raw.get(key)) }
            }
        } catch (_: Exception) {
            // Invalid raw JSON is ignored; Advanced screen surfaces it.
        }
        return payload
    }

    /** Non-streaming chat completion. */
    suspend fun complete(
        api: ResolvedApi,
        model: String,
        messages: List<WireMessage>,
        systemPrompt: String? = null
    ): ChatResult =
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            val request = newBuilder(api)
                .url(chatUrl(api))
                .post(buildPayload(api, model, messages, stream = false, systemPrompt = systemPrompt).toString().toRequestBody(jsonMedia))
                .build()
            val response = client().newCall(request).await()
            val body = response.use { it.body?.string().orEmpty() }
            if (!response.isSuccessful) throw httpError(response.code, body, response.header("retry-after"))
            val json = try { JSONObject(body) } catch (_: Exception) {
                throw ApiError(ApiErrorKind.INVALID_RESPONSE, "The server returned a response we couldn't read.")
            }
            val choices = json.optJSONArray("choices")
                ?: throw ApiError(ApiErrorKind.INVALID_RESPONSE, "The response is missing the result.")
            val first = choices.optJSONObject(0)
                ?: throw ApiError(ApiErrorKind.INVALID_RESPONSE, "The response is missing the result.")
            val content = first.optJSONObject("message")?.optString("content").orEmpty()
            val usage = json.optJSONObject("usage")
            if (content.isBlank()) throw ApiError(ApiErrorKind.INVALID_RESPONSE, "The model returned an empty reply.")
            ChatResult(
                text = content.trim(),
                latencyMs = System.currentTimeMillis() - startedAt,
                tokensIn = usage?.optInt("prompt_tokens", -1) ?: -1,
                tokensOut = usage?.optInt("completion_tokens", -1) ?: -1,
                finishReason = first.optString("finish_reason")
            )
        }

    /**
     * Streaming chat completion via SSE. [onDelta] receives incremental text
     * on a background thread; the caller must marshal to the UI thread.
     */
    suspend fun streamChat(
        api: ResolvedApi,
        model: String,
        messages: List<WireMessage>,
        systemPrompt: String? = null,
        onDelta: (String) -> Unit
    ): ChatResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val request = newBuilder(api)
            .url(chatUrl(api))
            .post(buildPayload(api, model, messages, stream = true, systemPrompt = systemPrompt).toString().toRequestBody(jsonMedia))
            .build()
        val response = client().newCall(request).await()
        if (!response.isSuccessful) {
            val errBody = response.body?.string().orEmpty()
            response.close()
            throw httpError(response.code, errBody, response.header("retry-after"))
        }
        val builder = StringBuilder()
        var tokensIn = -1
        var tokensOut = -1
        var finishReason = ""
        response.use { resp ->
            val source = resp.body?.source()
                ?: throw ApiError(ApiErrorKind.INVALID_RESPONSE, "The server returned no data.")
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") break
                try {
                    val json = JSONObject(data)
                    json.optJSONArray("choices")?.optJSONObject(0)?.let { choice ->
                        choice.optJSONObject("delta")?.optString("content")?.takeIf { it.isNotEmpty() }?.let {
                            builder.append(it)
                            onDelta(it)
                        }
                        choice.optString("finish_reason").takeIf { it.isNotBlank() }?.let { finishReason = it }
                    }
                    json.optJSONObject("usage")?.let { usage ->
                        tokensIn = usage.optInt("prompt_tokens", tokensIn)
                        tokensOut = usage.optInt("completion_tokens", tokensOut)
                    }
                } catch (_: Exception) {
                    // Ignore keep-alive and partial lines.
                }
            }
        }
        val text = builder.toString()
        if (text.isBlank()) throw ApiError(ApiErrorKind.INVALID_RESPONSE, "The model returned an empty reply.")
        ChatResult(text.trim(), System.currentTimeMillis() - startedAt, tokensIn, tokensOut, finishReason)
    }

    /** `GET {base}/models` → sorted ids. */
    suspend fun listModels(api: ResolvedApi): List<String> = withContext(Dispatchers.IO) {
        val request = newBuilder(api).url(modelsUrl(api)).get().build()
        val response = client().newCall(request).await()
        val body = response.use { it.body?.string().orEmpty() }
        if (!response.isSuccessful) throw httpError(response.code, body, response.header("retry-after"))
        val array = try { JSONObject(body).optJSONArray("data") ?: JSONArray() } catch (_: Exception) {
            throw ApiError(ApiErrorKind.INVALID_RESPONSE, "The model list came back unreadable.")
        }
        (0 until array.length())
            .mapNotNull { array.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
            .distinct()
            .sorted()
    }

    /**
     * Staged connection test driving the checklist UI:
     * 1. CONNECT — reachable endpoint responds
     * 2. AUTHENTICATE — key accepted (models list)
     * 3. CHECK_MODEL — tiny completion round-trip
     */
    suspend fun test(
        api: ResolvedApi,
        model: String,
        onStep: (TestStep, TestStepState, String?) -> Unit
    ): TestOutcome = withContext(Dispatchers.IO) {
        onStep(TestStep.CONNECT, TestStepState.RUNNING, "GET /models")
        val models = try {
            listModels(api)
        } catch (e: ApiError) {
            onStep(TestStep.CONNECT, TestStepState.FAILED, null)
            onStep(TestStep.AUTHENTICATE, TestStepState.FAILED, null)
            onStep(TestStep.CHECK_MODEL, TestStepState.FAILED, null)
            throw when (e.kind) {
                ApiErrorKind.NETWORK, ApiErrorKind.TIMEOUT, ApiErrorKind.OFFLINE -> e
                ApiErrorKind.INVALID_KEY -> {
                    onStep(TestStep.CONNECT, TestStepState.DONE, null)
                    ApiError(e.kind, "The server rejected your key (unauthorized).", e.detail)
                }
                else -> e
            }
        }
        onStep(TestStep.CONNECT, TestStepState.DONE, null)
        onStep(TestStep.AUTHENTICATE, TestStepState.RUNNING, null)
        onStep(TestStep.AUTHENTICATE, TestStepState.DONE, "${models.size} models visible")
        if (model.isBlank()) {
            onStep(TestStep.CHECK_MODEL, TestStepState.FAILED, null)
            throw ApiError(ApiErrorKind.MODEL_NOT_FOUND, "Choose a model to test.")
        }
        onStep(TestStep.CHECK_MODEL, TestStepState.RUNNING, "model: $model")
        val started = System.currentTimeMillis()
        try {
            complete(
                api, model,
                listOf(WireMessage("user", "Reply with the single word: OK"))
            )
        } catch (e: ApiError) {
            onStep(TestStep.CHECK_MODEL, TestStepState.FAILED, null)
            throw if (e.kind == ApiErrorKind.MODEL_NOT_FOUND ||
                (e.detail.contains("model", true) && e.kind == ApiErrorKind.INVALID_RESPONSE)
            ) ApiError(ApiErrorKind.MODEL_NOT_FOUND, "The model didn't respond on this endpoint.", e.detail) else e
        }
        onStep(TestStep.CHECK_MODEL, TestStepState.DONE, "${System.currentTimeMillis() - started} ms")
        TestOutcome(System.currentTimeMillis() - started, models)
    }

    private fun httpError(code: Int, body: String, retryAfter: String?): ApiError {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (_: Exception) {
            body.take(140)
        }
        val retrySec = retryAfter?.toIntOrNull()
        return when (code) {
            401, 403 -> ApiError(ApiErrorKind.INVALID_KEY, "The server rejected your key (unauthorized).", detail)
            404 -> {
                if (detail.contains("model", true)) {
                    ApiError(ApiErrorKind.MODEL_NOT_FOUND, "That model isn't available on this endpoint.", detail)
                } else {
                    ApiError(ApiErrorKind.NETWORK, "The endpoint path was not found (404).", detail)
                }
            }
            429 -> ApiError(ApiErrorKind.RATE_LIMIT, "The provider is rate-limiting.", detail, retrySec)
            in 500..599 -> ApiError(ApiErrorKind.SERVER, "The server had a problem ($code).", detail)
            else -> {
                if (detail.contains("context length", true) || detail.contains("maximum context", true)) {
                    ApiError(ApiErrorKind.CONTEXT_TOO_LARGE, "The conversation is longer than the model's context window.", detail)
                } else {
                    ApiError(ApiErrorKind.SERVER, "Request failed with HTTP $code.", detail)
                }
            }
        }
    }

    private fun ioError(e: IOException): ApiError = when (e) {
        is UnknownHostException -> ApiError(ApiErrorKind.NETWORK, "Could not resolve the server's address. Check the URL.")
        is SocketTimeoutException -> ApiError(ApiErrorKind.TIMEOUT, "The server took too long to respond.")
        else -> ApiError(ApiErrorKind.NETWORK, "Network error: ${e.message ?: "unknown"}.")
    }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(ioError(e))
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }
}
