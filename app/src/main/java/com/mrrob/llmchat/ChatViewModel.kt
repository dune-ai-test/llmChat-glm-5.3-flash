package com.mrrob.llmchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrrob.llmchat.data.ApiError
import com.mrrob.llmchat.data.ApiErrorKind
import com.mrrob.llmchat.data.ConnectionEntity
import com.mrrob.llmchat.data.ConversationEntity
import com.mrrob.llmchat.data.MessageEntity
import com.mrrob.llmchat.speech.TtsPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * Everything about one conversation: transcript, streaming generation,
 * stop/regenerate/variants, edit-and-continue branching, drafts and
 * read-aloud.
 */
class ChatViewModel(
    private val app: AppViewModel,
    val conversationId: String
) : ViewModel() {

    private val repo = app.repo
    private val client = app.client
    private val settingsStore = app.settingsStore

    val settings = app.settings
    val online = app.online

    /** Connections → their models, for the model sheet. */
    fun sheetModels(): List<Pair<ConnectionEntity, List<String>>> =
        app.connections.value
            .filter { it.enabled }
            .map { conn -> conn to (repo.modelsOf(conn).ifEmpty { listOfNotNull(conn.activeModel.ifBlank { null }) }) }
            .filter { it.second.isNotEmpty() }

    private val _conversation = MutableStateFlow<ConversationEntity?>(null)
    val conversation: StateFlow<ConversationEntity?> = _conversation.asStateFlow()

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _streaming = MutableStateFlow<StreamingState?>(null)
    val streaming: StateFlow<StreamingState?> = _streaming.asStateFlow()

    private val _generating = MutableStateFlow(false)
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    private val _draft = MutableStateFlow("")
    val draft: StateFlow<String> = _draft.asStateFlow()

    val repo = app.repo
    val connections get() = app.connections.value

    fun isGeneratingNow(): Boolean = _generating.value
    fun showCodeLineNumbers(): Boolean = app.settings.value.codeLineNumbers

    private var draftJob: Job? = null
    fun saveDraftDebounce(text: String) {
        draftJob?.cancel()
        draftJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            repo.setDraft(conversationId, text)
        }
    }

    fun rate(message: MessageEntity, rating: Int) {
        viewModelScope.launch { repo.updateMessage(message.copy(rating = rating)) }
    }

    fun rename(title: String) {
        viewModelScope.launch {
            repo.renameConversation(conversationId, title)
            repo.conversation(conversationId)?.let { _conversation.value = it }
        }
    }

    data class StreamingState(
        val targetUserId: Long,
        val text: String,
        val model: String,
        val error: ApiError? = null
    )

    private var generationJob: Job? = null
    private var tts: TtsPlayer? = null

    /** The draft restored from the conversation on first open. */
    val initialDraft: String get() = _conversation.value?.draft.orEmpty()

    init {
        app.activeChatConversationId = conversationId
        viewModelScope.launch {
            repo.conversation(conversationId)?.let { conv ->
                _conversation.value = conv
                _draft.value = conv.draft
            }
        }
        viewModelScope.launch {
            repo.observeMessages(conversationId).collect { _messages.value = it }
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (app.activeChatConversationId == conversationId) app.activeChatConversationId = null
        tts?.shutdown()
    }

    // ── Sending ─────────────────────────────────────────────────────────────────

    fun activeConnection(): ConnectionEntity? {
        val conv = _conversation.value
        conv?.connectionId?.let { cid ->
            app.connections.value.firstOrNull { it.id == cid }?.let { return it }
        }
        return app.connections.value.firstOrNull { it.isDefault && it.enabled }
            ?: app.connections.value.firstOrNull { it.enabled }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val conv = _conversation.value ?: return
        val connection = activeConnection()
        if (connection == null) {
            _streaming.value = StreamingState(
                targetUserId = 0,
                text = "Add a connection in Settings first.",
                model = "",
                error = ApiError(ApiErrorKind.NETWORK, "No connection configured yet.")
            )
            return
        }
        viewModelScope.launch {
            val offline = !online.value
            val userMsg = MessageEntity(
                conversationId = conv.id,
                role = "user",
                text = trimmed,
                status = if (offline) "QUEUED" else "OK"
            )
            val id = repo.insertMessage(userMsg)
            repo.touchConversation(conv.id, trimmed.take(80))
            repo.setDraft(conv.id, "")
            if (offline) return@launch // the app-level queue flushes on reconnect
            generateFor(userMessageId = id, connection = connection)
        }
    }

    /** Stream (or complete) a reply for a specific already-inserted user message. */
    private fun generateFor(userMessageId: Long, connection: ConnectionEntity) {
        val conv = _conversation.value ?: return
        generationJob?.cancel()
        val model = conv.model.ifBlank { connection.activeModel }
        generationJob = viewModelScope.launch {
            val history = _messages.value.filter { it.id <= userMessageId && it.status != "FAILED" }
            val wire = repo.wireMessages(history)
            _generating.value = true
            _streaming.value = StreamingState(userMessageId, "", model)
            try {
                val result = if (settingsStore.settings.value.streaming) {
                    client.streamChat(repo.resolveApi(connection), model, wire, conv.systemPrompt) { delta ->
                        _streaming.update { it?.copy(text = it.text + delta) }
                    }
                } else {
                    client.complete(repo.resolveApi(connection), model, wire, conv.systemPrompt)
                }
                _streaming.value = null
                _generating.value = false
                repo.insertMessage(
                    MessageEntity(
                        conversationId = conv.id,
                        role = "assistant",
                        text = result.text,
                        model = model,
                        latencyMs = result.latencyMs,
                        tokensIn = result.tokensIn,
                        tokensOut = result.tokensOut
                    )
                )
                repo.touchConversation(conv.id, result.text.take(80))
                autoTitle(connection, model, result.text)
                maybeAutoSpeak(result.text)
            } catch (e: ApiError) {
                _generating.value = false
                _streaming.value = StreamingState(userMessageId, "", model, e)
            } catch (e: Exception) {
                _generating.value = false
                _streaming.value = StreamingState(
                    userMessageId,
                    "",
                    model,
                    ApiError(ApiErrorKind.NETWORK, e.message ?: "Something went wrong.")
                )
            }
        }
    }

    fun stopGenerating() {
        generationJob?.cancel()
        _generating.value = false
        val state = _streaming.value
        val conv = _conversation.value
        if (state != null && conv != null && state.text.isNotBlank()) {
            viewModelScope.launch {
                repo.insertMessage(
                    MessageEntity(
                        conversationId = conv.id,
                        role = "assistant",
                        text = state.text.trim(),
                        model = state.model
                    )
                )
                repo.touchConversation(conv.id, state.text.take(80))
            }
        }
        _streaming.value = null
    }

    fun clearGenerationError() {
        _streaming.value = null
        _generating.value = false
    }

    fun retryGeneration() {
        val state = _streaming.value ?: return
        val connection = activeConnection() ?: return
        _streaming.value = null
        generateFor(state.targetUserId, connection)
    }

    private suspend fun autoTitle(connection: ConnectionEntity, model: String, assistantText: String) {
        val conv = _conversation.value ?: return
        val s = settingsStore.settings.value
        if (!s.autoTitle || conv.title != "New conversation") return
        val firstUser = _messages.value.firstOrNull { it.role == "user" }?.text ?: return
        repo.renameConversation(conv.id, repo.generateTitle(connection, model, firstUser, assistantText))
        repo.conversation(conv.id)?.let { _conversation.value = it }
    }

    private fun maybeAutoSpeak(text: String) {
        val s = settingsStore.settings.value
        if (s.voiceAutoPlay && s.voiceTts && _conversation.value?.voice == true) {
            speak(text)
        }
    }

    // ── Regenerate with variants ────────────────────────────────────────────────

    /**
     * Variant storage: [MessageEntity.text] is the active variant,
     * [MessageEntity.variantsJson] holds the others. [mergedVariants]
     * reconstructs the full list in display order.
     */
    private fun mergedVariants(m: MessageEntity): List<String> {
        val others = try {
            val arr = JSONArray(m.variantsJson)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
        val total = others.size + 1
        val idx = m.activeVariant.coerceIn(0, total - 1)
        val out = ArrayList<String>(total)
        var j = 0
        for (i in 0 until total) {
            if (i == idx) out.add(m.text) else { out.add(others[j]); j++ }
        }
        return out
    }

    fun variantCount(message: MessageEntity): Int = mergedVariants(message).size
    fun variantIndex(message: MessageEntity): Int = message.activeVariant.coerceAtLeast(0) + 1

    fun cycleVariant(message: MessageEntity, direction: Int) {
        val all = mergedVariants(message)
        if (all.size < 2) return
        val next = ((message.activeVariant + direction) % all.size + all.size) % all.size
        val newActive = all[next]
        val others = all.filterIndexed { i, _ -> i != next }
        viewModelScope.launch {
            repo.updateMessage(
                message.copy(
                    text = newActive,
                    variantsJson = JSONArray(others).toString(),
                    activeVariant = next
                )
            )
        }
    }

    fun regenerate(message: MessageEntity) {
        val conv = _conversation.value ?: return
        val connection = activeConnection() ?: return
        generationJob?.cancel()
        val model = message.model.ifBlank { conv.model }
        generationJob = viewModelScope.launch {
            val wire = repo.wireMessages(_messages.value.filterNot { it.id == message.id })
            _generating.value = true
            _streaming.value = StreamingState(message.id, "", model)
            try {
                val result = client.complete(repo.resolveApi(connection), model, wire, conv.systemPrompt)
                _streaming.value = null
                _generating.value = false
                val merged = mergedVariants(message) + result.text
                repo.updateMessage(
                    message.copy(
                        text = result.text,
                        variantsJson = JSONArray(merged.dropLast(1)).toString(),
                        activeVariant = merged.lastIndex,
                        latencyMs = result.latencyMs,
                        tokensIn = result.tokensIn,
                        tokensOut = result.tokensOut
                    )
                )
            } catch (e: ApiError) {
                _generating.value = false
                _streaming.value = _streaming.value?.copy(error = e)
            } catch (e: Exception) {
                _generating.value = false
                _streaming.value = _streaming.value?.copy(
                    error = ApiError(ApiErrorKind.NETWORK, e.message ?: "Something went wrong.")
                )
            }
        }
    }

    // ── Edit / delete / branch ──────────────────────────────────────────────────

    fun deleteMessage(message: MessageEntity) {
        viewModelScope.launch { repo.deleteMessage(message) }
    }

    fun editUserMessageInPlace(message: MessageEntity, newText: String) {
        val conv = _conversation.value ?: return
        viewModelScope.launch {
            repo.messagesOf(conv.id)
                .filter { it.id != message.id && it.createdAt >= message.createdAt }
                .forEach { repo.deleteMessage(it) }
            repo.updateMessage(message.copy(text = newText.trim(), status = "OK"))
            repo.touchConversation(conv.id, newText.trim().take(80))
            val connection = activeConnection()
            if (connection != null && online.value) {
                generateFor(message.id, connection)
            }
        }
    }

    fun editAndBranch(message: MessageEntity, newText: String) {
        viewModelScope.launch {
            val branch = repo.createBranch(conversationId, message.id, newText.trim())
            if (branch != null) app.goChat(branch.id)
        }
    }

    fun saveDraft(draft: String) {
        viewModelScope.launch { repo.setDraft(conversationId, draft) }
    }

    // ── Model switching ─────────────────────────────────────────────────────────

    fun switchModel(model: String, connectionId: String = "") {
        viewModelScope.launch {
            val conv = _conversation.value ?: return@launch
            val newConnId = connectionId.ifBlank { conv.connectionId }
            repo.updateConversation(
                conv.copy(
                    model = model,
                    connectionId = newConnId,
                    updatedAt = System.currentTimeMillis()
                )
            )
            repo.insertMessage(
                MessageEntity(conversationId = conv.id, role = "system", text = "Model switched to $model")
            )
            repo.conversation(conv.id)?.let { _conversation.value = it }
        }
    }

    // ── Read aloud / share ──────────────────────────────────────────────────────

    fun speak(text: String) {
        val engine = tts ?: TtsPlayer(app.appContext).also { tts = it }
        engine.rate = settingsStore.settings.value.speakingRate
        engine.speak(text)
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun shareText(): String {
        val conv = _conversation.value ?: return ""
        val sb = StringBuilder()
        sb.appendLine(conv.title)
        sb.appendLine()
        _messages.value.forEach { m ->
            val label = when (m.role) {
                "user" -> "You"
                "assistant" -> m.model.ifBlank { "Assistant" }
                else -> ""
            }
            sb.appendLine(if (label.isNotBlank()) "$label: ${m.text}" else m.text)
            sb.appendLine()
        }
        return sb.toString()
    }
}
