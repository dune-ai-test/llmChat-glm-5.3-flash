package com.mrrob.llmchat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mrrob.llmchat.data.ChatMessage
import com.mrrob.llmchat.data.LlmApi
import com.mrrob.llmchat.data.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lifecycle of the voice conversation. */
enum class VoicePhase { Idle, Listening, Thinking, Speaking }

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SettingsRepository(application)
    private val api = LlmApi(repository.settings)

    val settings: StateFlow<SettingsRepository.Settings> = repository.settings

    fun saveSettings(baseUrl: String, apiKey: String, model: String) {
        repository.save(baseUrl, apiKey, model)
    }

    // ── Connection test (Settings screen) ───────────────────────────────────────

    data class TestState(
        val running: Boolean = false,
        val models: List<String> = emptyList(),
        val error: String? = null,
        val successMessage: String? = null
    )

    private val _test = MutableStateFlow(TestState())
    val test: StateFlow<TestState> = _test.asStateFlow()

    fun testConnection(baseUrl: String, apiKey: String) {
        if (_test.value.running) return
        _test.value = TestState(running = true)
        viewModelScope.launch {
            try {
                val models = api.listModels(baseUrl, apiKey)
                _test.value = TestState(
                    models = models,
                    successMessage = "Connection successful · ${models.size} models found"
                )
            } catch (e: Exception) {
                _test.value = TestState(error = e.message ?: "Something went wrong.")
            }
        }
    }

    fun clearTest() {
        if (_test.value != TestState()) _test.value = TestState()
    }

    // ── Text chat ───────────────────────────────────────────────────────────────

    data class ChatUiState(
        val messages: List<ChatMessage> = emptyList(),
        val sending: Boolean = false,
        val error: String? = null
    )

    private val _chat = MutableStateFlow(ChatUiState())
    val chat: StateFlow<ChatUiState> = _chat.asStateFlow()

    private var chatJob: Job? = null

    fun sendText(input: String) {
        val text = input.trim()
        if (text.isEmpty() || _chat.value.sending) return
        if (!settings.value.isConfigured) {
            _chat.value = _chat.value.copy(error = "Add your server URL, API key and model in Settings first.")
            return
        }
        val history = _chat.value.messages + ChatMessage(ChatMessage.Role.USER, text)
        _chat.value = ChatUiState(messages = history, sending = true)
        chatJob?.cancel()
        chatJob = viewModelScope.launch {
            try {
                val reply = api.chat(history)
                _chat.value = ChatUiState(messages = history + ChatMessage(ChatMessage.Role.ASSISTANT, reply))
            } catch (e: Exception) {
                _chat.value = ChatUiState(messages = history, error = e.message ?: "Something went wrong.")
            }
        }
    }

    fun dismissChatError() {
        _chat.update { it.copy(error = null) }
    }

    // ── Voice chat ──────────────────────────────────────────────────────────────

    data class VoiceUiState(
        val messages: List<ChatMessage> = emptyList(),
        val phase: VoicePhase = VoicePhase.Idle,
        val partial: String = "",
        val error: String? = null
    )

    private val _voice = MutableStateFlow(VoiceUiState())
    val voice: StateFlow<VoiceUiState> = _voice.asStateFlow()

    private var voiceJob: Job? = null

    fun onVoiceListeningStarted() {
        _voice.update { it.copy(phase = VoicePhase.Listening, partial = "", error = null) }
    }

    fun onVoicePartial(text: String) {
        _voice.update { it.copy(partial = text) }
    }

    fun onVoiceNothingHeard() {
        _voice.update { if (it.phase == VoicePhase.Listening) it.copy(phase = VoicePhase.Idle, partial = "") else it }
    }

    fun onVoicePermissionDenied() {
        _voice.update { it.copy(phase = VoicePhase.Idle, error = "Microphone permission is required for voice chat.") }
    }

    fun onVoiceError(message: String) {
        _voice.update { it.copy(phase = VoicePhase.Idle, partial = "", error = message) }
    }

    /** Called by the recognition listener with the finalized transcript of one utterance. */
    fun onVoiceFinal(text: String) {
        val finalText = text.trim()
        if (finalText.isEmpty()) {
            onVoiceNothingHeard()
            return
        }
        sendVoiceMessage(finalText)
    }

    fun sendVoiceMessage(text: String) {
        if (_voice.value.phase == VoicePhase.Thinking) return
        if (!settings.value.isConfigured) {
            _voice.update {
                it.copy(phase = VoicePhase.Idle, partial = "", error = "Add your server URL, API key and model in Settings first.")
            }
            return
        }
        val history = _voice.value.messages + ChatMessage(ChatMessage.Role.USER, text)
        _voice.value = VoiceUiState(messages = history, phase = VoicePhase.Thinking)
        voiceJob?.cancel()
        voiceJob = viewModelScope.launch {
            try {
                val reply = api.chat(history)
                _voice.value = VoiceUiState(
                    messages = history + ChatMessage(ChatMessage.Role.ASSISTANT, reply),
                    phase = VoicePhase.Speaking
                )
                // Safety net: never stay stuck in "Speaking" if TTS callbacks never fire.
                delay(20_000)
                onSpeakingDone()
            } catch (e: Exception) {
                _voice.value = VoiceUiState(messages = history, phase = VoicePhase.Idle, error = e.message ?: "Something went wrong.")
            }
        }
    }

    fun onSpeakingDone() {
        _voice.update {
            if (it.phase == VoicePhase.Speaking) it.copy(phase = VoicePhase.Listening, partial = "") else it
        }
    }

    fun dismissVoiceError() {
        _voice.update { it.copy(error = null) }
    }
}
