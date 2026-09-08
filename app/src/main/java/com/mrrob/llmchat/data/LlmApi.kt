package com.mrrob.llmchat.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Minimal client for OpenAI-compatible endpoints:
 *   GET  {base}/models
 *   POST {base}/chat/completions
 *
 * The base URL may be given with or without a trailing "/v1" — "/v1" is
 * appended automatically when missing.
 */
class LlmApi(private val settings: StateFlow<SettingsRepository.Settings>) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    /** Fetches the model list from `GET {base}/models`. */
    suspend fun listModels(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(joinUrl(baseUrl, "/models"))
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .header("Accept", "application/json")
                .get()
                .build()

            client.newCall(request).await().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw ApiException(errorMessage(response.code, body))
                val array = JSONObject(body).optJSONArray("data") ?: JSONArray()
                (0 until array.length())
                    .mapNotNull { array.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
                    .distinct()
                    .sorted()
            }
        }

    /** Sends the transcript to `POST {base}/chat/completions` and returns the reply text. */
    suspend fun chat(messages: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        val config = settings.value
        val payload = JSONObject().apply {
            put("model", config.model)
            put("messages", JSONArray().apply {
                messages.forEach { message ->
                    put(
                        JSONObject()
                            .put("role", if (message.role == ChatMessage.Role.USER) "user" else "assistant")
                            .put("content", message.text)
                    )
                }
            })
            put("stream", false)
            put("temperature", 0.7)
        }

        val request = Request.Builder()
            .url(joinUrl(config.baseUrl, "/chat/completions"))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).await().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(errorMessage(response.code, body))
            val choices = JSONObject(body).optJSONArray("choices")
            val content = choices?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (content.isBlank()) throw ApiException("The server returned an empty response.")
            content.trim()
        }
    }

    private fun joinUrl(baseUrl: String, path: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) throw ApiException("Server URL is not set. Add it in Settings.")
        val withV1 = if (trimmed.substringAfterLast('/').equals("v1", ignoreCase = true)) trimmed else "$trimmed/v1"
        return withV1 + path
    }

    private fun errorMessage(code: Int, body: String): String {
        val detail = try {
            JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf(String::isNotBlank)
        } catch (_: Exception) {
            null
        }
        return detail ?: "Request failed with HTTP $code."
    }

    private fun mapError(e: IOException): Exception = when (e) {
        is UnknownHostException -> ApiException("Could not reach the server. Check the URL and your connection.")
        is SocketTimeoutException -> ApiException("The server took too long to respond.")
        else -> ApiException("Network error: ${e.message ?: "unknown"}.")
    }

    class ApiException(message: String) : Exception(message)

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(mapError(e))
            }
        })
        continuation.invokeOnCancellation { cancel() }
    }
}
