package com.mrrob.llmchat.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local-only request log for the Advanced settings debug switch.
 * Secrets are redacted before anything is stored; logs live only in
 * process memory and are never exported unless the user copies them.
 */
class DebugLog {

    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    @Volatile
    var enabled: Boolean = false

    fun log(message: String) {
        if (!enabled) return
        val redacted = message
            .replace(Regex("(?i)(authorization\"?\\s*[:=]\\s*\"?)(Bearer\\s+)?[A-Za-z0-9_\\-]{6,}"), "$1$2•••redacted•••")
            .replace(Regex("(?i)(api[_-]?key\"?\\s*[:=]\\s*\"?)[A-Za-z0-9_\\-]{6,}"), "$1•••redacted•••")
            .replace(Regex("sk-[A-Za-z0-9_\\-]{8,}"), "sk-•••redacted•••")
        _entries.value = (_entries.value + redacted).takeLast(200)
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun copyText(): String = _entries.value.joinToString("\n")
}
