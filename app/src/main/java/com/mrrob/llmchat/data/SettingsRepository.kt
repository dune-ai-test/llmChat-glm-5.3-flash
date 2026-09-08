package com.mrrob.llmchat.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the LLM connection settings (server URL, API key, model) and
 * persists them in SharedPreferences so they survive app restarts.
 */
class SettingsRepository(context: Context) {

    data class Settings(
        val baseUrl: String = "",
        val apiKey: String = "",
        val model: String = ""
    ) {
        val isConfigured: Boolean
            get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
    }

    private val prefs = context.getSharedPreferences("llm_chat_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(
        Settings(
            baseUrl = prefs.getString(KEY_BASE_URL, "").orEmpty(),
            apiKey = prefs.getString(KEY_API_KEY, "").orEmpty(),
            model = prefs.getString(KEY_MODEL, "").orEmpty()
        )
    )

    val settings: StateFlow<Settings> = _settings.asStateFlow()

    fun save(baseUrl: String, apiKey: String, model: String) {
        val clean = Settings(baseUrl.trim(), apiKey.trim(), model.trim())
        prefs.edit()
            .putString(KEY_BASE_URL, clean.baseUrl)
            .putString(KEY_API_KEY, clean.apiKey)
            .putString(KEY_MODEL, clean.model)
            .apply()
        _settings.value = clean
    }

    private companion object {
        const val KEY_BASE_URL = "base_url"
        const val KEY_API_KEY = "api_key"
        const val KEY_MODEL = "model"
    }
}
