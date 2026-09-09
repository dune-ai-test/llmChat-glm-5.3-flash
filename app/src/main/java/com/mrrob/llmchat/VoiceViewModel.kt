package com.mrrob.llmchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrrob.llmchat.data.ApiError
import com.mrrob.llmchat.data.ConnectionEntity
import com.mrrob.llmchat.data.ConversationEntity
import com.mrrob.llmchat.data.MessageEntity
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The voice session state machine. The screen owns the microphone and the
 * TTS engine; this view model owns the conversation, the phase and the
 * generation round-trips so transcripts persist exactly like text chats.
 */
class VoiceViewModel(private val app: AppViewModel) : ViewModel() {

    enum class Phase { READY, LISTENING, PROCESSING, SPEAKING }

    private val _phase = MutableStateFlow(Phase.READY)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _transcript = MutableStateFlow<List<MessageEntity>>(emptyList())
    val transcript: StateFlow<List<MessageEntity>> = _transcript.asStateFlow()

    private val _conversation = MutableStateFlow<ConversationEntity?>(null)
    val conversation: StateFlow<ConversationEntity?> = _conversation.asStateFlow()

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _error = MutableStateFlow<ApiError?>(null)
    val error: StateFlow<ApiError?> = _error.asStateFlow()

    private var conversationId: String? = null
    private var generationJob: Job? = null

    val settings get() = app.settings
    val online get() = app.online

    fun connection(): ConnectionEntity? =
        app.connections.value.firstOrNull { it.isDefault && it.enabled }
            ?: app.connections.value.firstOrNull { it.enabled }

    fun setPhase(phase: Phase) {
        if (phase == Phase.LISTENING) _partial.value = ""
        _phase.value = phase
    }

    fun onPartial(text: String) {
        _partial.value = text
    }

    fun clearError() {
        _error.value = null
    }

    fun onMicError(message: String) {
        _phase.value = Phase.READY
        _error.value = ApiError(com.mrrob.llmchat.data.ApiErrorKind.NETWORK, message)
    }

    /** An utterance was finalized by speech recognition. */
    fun onUtterance(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            _phase.value = Phase.READY
            return
        }
        val connection = connection()
        if (connection == null) {
            _error.value = ApiError(
                com.mrrob.llmchat.data.ApiErrorKind.NETWORK,
                "Add a connection in Settings first."
            )
            _phase.value = Phase.READY
            return
        }
        if (!online.value) {
            _error.value = ApiError(com.mrrob.llmchat.data.ApiErrorKind.OFFLINE, "You're offline. Voice chat needs a connection.")
            _phase.value = Phase.READY
            return
        }
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _error.value = null
            val conv = ensureConversation(connection)
            repo().insertMessage(MessageEntity(conversationId = conv.id, role = "user", text = trimmed))
            repo().touchConversation(conv.id, trimmed.take(80))
            reloadTranscript(conv.id)
            _phase.value = Phase.PROCESSING
            val wire = repo().wireMessages(repo().messagesOf(conv.id))
            try {
                val result = app.client.complete(
                    repo().resolveApi(connection),
                    conv.model.ifBlank { connection.activeModel },
                    wire,
                    systemPrompt = conv.systemPrompt
                )
                repo().insertMessage(
                    MessageEntity(
                        conversationId = conv.id,
                        role = "assistant",
                        text = result.text,
                        model = conv.model.ifBlank { connection.activeModel },
                        latencyMs = result.latencyMs
                    )
                )
                repo().touchConversation(conv.id, result.text.take(80))
                reloadTranscript(conv.id)
                autoTitle(connection, result.text)
                _phase.value = Phase.SPEAKING
                onSpeakFinished = { _phase.value = Phase.READY }
            } catch (e: ApiError) {
                _error.value = e
                _phase.value = Phase.READY
            } catch (e: Exception) {
                _error.value = ApiError(com.mrrob.llmchat.data.ApiErrorKind.NETWORK, e.message ?: "Something went wrong.")
                _phase.value = Phase.READY
            }
        }
    }

    /** The screen calls this when its TTS utterance completes. */
    var onSpeakFinished: (() -> Unit)? = null

    fun speakFinished() {
        onSpeakFinished?.invoke()
    }

    fun interrupt() {
        generationJob?.cancel()
        onSpeakFinished = null
        _phase.value = Phase.LISTENING
    }

    fun endSession() {
        generationJob?.cancel()
        onSpeakFinished = null
        _phase.value = Phase.READY
        _conversation.value = null
        conversationId = null
        _transcript.value = emptyList()
    }

    private fun repo() = app.repo

    private suspend fun ensureConversation(connection: ConnectionEntity): ConversationEntity {
        conversationId?.let { id ->
            repo().conversation(id)?.let { return it }
        }
        val conv = repo().createConversation(
            kind = "VOICE",
            title = "Voice conversation",
            connectionId = connection.id,
            model = connection.activeModel
        )
        conversationId = conv.id
        _conversation.value = conv
        return conv
    }

    private fun reloadTranscript(id: String) {
        viewModelScope.launch {
            _transcript.value = repo().messagesOf(id)
        }
    }

    private suspend fun autoTitle(connection: ConnectionEntity, assistantText: String) {
        val conv = _conversation.value ?: return
        val s = app.settingsStore.settings.value
        if (!s.autoTitle || conv.title != "Voice conversation") return
        val firstUser = _transcript.value.firstOrNull { it.role == "user" }?.text ?: return
        repo().renameConversation(conv.id, repo().generateTitle(connection, conv.model, firstUser, assistantText))
    }
}
