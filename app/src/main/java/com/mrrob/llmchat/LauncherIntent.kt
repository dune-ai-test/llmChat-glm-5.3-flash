package com.mrrob.llmchat

import kotlinx.coroutines.flow.MutableStateFlow

/** Bridges launcher shortcuts / the home-screen widget action into the app. */
object LauncherIntent {
    val action = MutableStateFlow<String?>(null)
}
