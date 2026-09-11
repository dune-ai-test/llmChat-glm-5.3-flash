package com.mrrob.llmchat.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * All user preferences, persisted in SharedPreferences and exposed as a
 * [StateFlow]. API keys live separately in encrypted storage and are never
 * part of this object.
 */
data class AppSettings(
    val themeMode: String = "system",            // system | light | dark
    val fontScale: String = "medium",            // small | medium | large
    val chatDensity: String = "comfortable",     // comfortable | compact
    val haptics: Boolean = true,
    val onboardingDone: Boolean = false,
    val displayName: String = "Marcus Reidel",
    val voiceName: String = "Aria · Warm",

    val streaming: Boolean = true,
    val enterToSend: Boolean = false,
    val autoScroll: Boolean = true,
    val autoTitle: Boolean = true,
    val codeLineNumbers: Boolean = false,
    val temperature: Float = 0.7f,
    val maxTokens: Int = 4096,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val promptPreset: String = "custom",         // concise | detailed | code-first | custom

    val voiceStt: Boolean = true,                // on-device STT enabled
    val voiceTts: Boolean = true,                // text-to-speech replies
    val voiceAutoPlay: Boolean = false,
    val continuousVoice: Boolean = true,
    val interruptOnSpeech: Boolean = true,
    val speakingRate: Float = 1.1f,

    val requestTimeoutSec: Int = 60,
    val endpointPath: String = "/chat/completions",
    val apiVersion: String = "",
    val rawParams: String = "",
    val debugLogging: Boolean = false,

    val recentSearches: String = "",
    val favoriteModels: String = "",
    val accentTheme: String = "indigo",        // indigo | emerald | sunset
    val navMode: String = "bottom",            // bottom | side
    val exportFolderUri: String = ""
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "Be direct and practical. Prefer short examples over long explanations. Use markdown for code."
        const val PRESET_CONCISE = "Answer in as few words as possible without losing accuracy."
        const val PRESET_DETAILED = "Give thorough, well-structured answers with context and examples."
        const val PRESET_CODE_FIRST =
            "Prioritize working code. Use markdown code blocks with language labels. Explain only what is non-obvious."
    }
}

class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("aster_settings", Context.MODE_PRIVATE)

    private var secrets: android.content.SharedPreferences? = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "aster_secrets",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        context.getSharedPreferences("aster_secrets", Context.MODE_PRIVATE)
    }

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun read(): AppSettings = with(prefs) {
        AppSettings(
            themeMode = getString("theme_mode", "system") ?: "system",
            fontScale = getString("font_scale", "medium") ?: "medium",
            chatDensity = getString("chat_density", "comfortable") ?: "comfortable",
            haptics = getBoolean("haptics", true),
            onboardingDone = getBoolean("onboarding_done", false),
            displayName = getString("display_name", "Marcus Reidel") ?: "Marcus Reidel",
            voiceName = getString("voice_name", "Aria · Warm") ?: "Aria · Warm",
            streaming = getBoolean("streaming", true),
            enterToSend = getBoolean("enter_to_send", false),
            autoScroll = getBoolean("auto_scroll", true),
            autoTitle = getBoolean("auto_title", true),
            codeLineNumbers = getBoolean("code_line_numbers", false),
            temperature = getFloat("temperature", 0.7f),
            maxTokens = getInt("max_tokens", 4096),
            systemPrompt = getString("system_prompt", AppSettings.DEFAULT_SYSTEM_PROMPT)
                ?: AppSettings.DEFAULT_SYSTEM_PROMPT,
            promptPreset = getString("prompt_preset", "custom") ?: "custom",
            voiceStt = getBoolean("voice_stt", true),
            voiceTts = getBoolean("voice_tts", true),
            voiceAutoPlay = getBoolean("voice_autoplay", false),
            continuousVoice = getBoolean("continuous_voice", true),
            interruptOnSpeech = getBoolean("interrupt_on_speech", true),
            speakingRate = getFloat("speaking_rate", 1.1f),
            requestTimeoutSec = getInt("request_timeout", 60),
            endpointPath = getString("endpoint_path", "/chat/completions") ?: "/chat/completions",
            apiVersion = getString("api_version", "").orEmpty(),
            rawParams = getString("raw_params", "").orEmpty(),
            debugLogging = getBoolean("debug_logging", false),
            recentSearches = getString("recent_searches", "").orEmpty(),
            favoriteModels = getString("favorite_models", "").orEmpty(),
            accentTheme = getString("accent_theme", "indigo") ?: "indigo",
            navMode = getString("nav_mode", "bottom") ?: "bottom",
            exportFolderUri = getString("export_folder", "").orEmpty()
        )
    }

    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_settings.value)
        _settings.value = next
        prefs.edit().apply {
            putString("theme_mode", next.themeMode)
            putString("font_scale", next.fontScale)
            putString("chat_density", next.chatDensity)
            putBoolean("haptics", next.haptics)
            putBoolean("onboarding_done", next.onboardingDone)
            putString("display_name", next.displayName)
            putString("voice_name", next.voiceName)
            putBoolean("streaming", next.streaming)
            putBoolean("enter_to_send", next.enterToSend)
            putBoolean("auto_scroll", next.autoScroll)
            putBoolean("auto_title", next.autoTitle)
            putBoolean("code_line_numbers", next.codeLineNumbers)
            putFloat("temperature", next.temperature)
            putInt("max_tokens", next.maxTokens)
            putString("system_prompt", next.systemPrompt)
            putString("prompt_preset", next.promptPreset)
            putBoolean("voice_stt", next.voiceStt)
            putBoolean("voice_tts", next.voiceTts)
            putBoolean("voice_autoplay", next.voiceAutoPlay)
            putBoolean("continuous_voice", next.continuousVoice)
            putBoolean("interrupt_on_speech", next.interruptOnSpeech)
            putFloat("speaking_rate", next.speakingRate)
            putInt("request_timeout", next.requestTimeoutSec)
            putString("endpoint_path", next.endpointPath)
            putString("api_version", next.apiVersion)
            putString("raw_params", next.rawParams)
            putBoolean("debug_logging", next.debugLogging)
            putString("recent_searches", next.recentSearches)
            putString("favorite_models", next.favoriteModels)
            putString("accent_theme", next.accentTheme)
            putString("nav_mode", next.navMode)
            putString("export_folder", next.exportFolderUri)
        }.apply()
    }

    // ── Generic auxiliary prefs (per-connection headers etc.) ───────────────────

    fun prefsString(key: String, default: String = ""): String = prefs.getString(key, default).orEmpty()

    fun prefsPutString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    /** Fetched model lists cached per base URL, so a server only needs one fetch. */
    fun cachedModels(baseUrl: String): List<String> = runCatching {
        val arr = org.json.JSONArray(prefsString("models_$baseUrl", "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    fun setCachedModels(baseUrl: String, models: List<String>) =
        prefsPutString("models_$baseUrl", org.json.JSONArray(models).toString())

    // ── API keys (encrypted, never in AppSettings or exports) ──────────────────

    fun apiKey(connectionId: String): String = runCatching {
        secrets?.getString("key_$connectionId", "").orEmpty()
    }.getOrDefault("")

    fun setApiKey(connectionId: String, key: String) {
        runCatching { secrets?.edit()?.putString("key_$connectionId", key)?.apply() }
    }

    fun removeApiKey(connectionId: String) {
        runCatching { secrets?.edit()?.remove("key_$connectionId")?.apply() }
    }

    // ── Backup password (set once; exports and daily auto-backups use it) ─────

    fun backupPassword(): String = runCatching {
        secrets?.getString("backup_pw", "").orEmpty()
    }.getOrDefault("")

    fun setBackupPassword(password: String) {
        runCatching { secrets?.edit()?.putString("backup_pw", password)?.apply() }
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        secrets?.edit()?.clear()?.apply()
        _settings.value = read()
    }
}

/** Mask an API key for display: sk-proj-••••••••••4w2 */
fun maskApiKey(key: String): String {
    if (key.length <= 8) return "•".repeat(key.length)
    return key.take(6) + "•".repeat(6) + "…" + key.takeLast(3)
}
