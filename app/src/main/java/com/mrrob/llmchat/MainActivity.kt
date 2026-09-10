package com.mrrob.llmchat

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mrrob.llmchat.ui.AsterRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAction(intent)
        enableEdgeToEdge()
        setContent {
            AsterRoot(application as AsterApp)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAction(intent)
    }

    private fun handleAction(intent: Intent?) {
        intent?.getStringExtra("aster_action")?.let { LauncherIntent.action.value = it }
    }
}
