package com.mrrob.llmchat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrrob.llmchat.MainViewModel
import com.mrrob.llmchat.VoicePhase
import com.mrrob.llmchat.data.ChatMessage
import com.mrrob.llmchat.speech.SpeechRecognitionManager
import com.mrrob.llmchat.speech.TtsPlayer
import com.mrrob.llmchat.ui.components.ErrorBanner
import com.mrrob.llmchat.ui.components.LargeTitle
import com.mrrob.llmchat.ui.theme.IosGreen

/**
 * Voice-only chat: a big tap-to-talk orb drives continuous
 * listen → think → speak rounds, while the full transcript stays visible.
 */
@Composable
fun VoiceScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val view = LocalView.current
    val state by viewModel.voice.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val speechManager = remember { SpeechRecognitionManager(context) }
    val tts = remember { TtsPlayer(context) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            speechManager.startListening(
                onPartial = viewModel::onVoicePartial,
                onFinal = viewModel::onVoiceFinal,
                onListeningStarted = viewModel::onVoiceListeningStarted,
                onError = viewModel::onVoiceError
            )
        } else {
            viewModel.onVoicePermissionDenied()
        }
    }

    // Continuous loop: after TTS finishes, resume listening automatically.
    LaunchedEffect(state.phase) {
        when (state.phase) {
            VoicePhase.Speaking -> tts.speak(
                text = state.messages.lastOrNull { it.role == ChatMessage.Role.ASSISTANT }?.text.orEmpty(),
                onDone = viewModel::onSpeakingDone
            )
            VoicePhase.Listening -> {
                if (!speechManager.isListening) {
                    speechManager.startListening(
                        onPartial = viewModel::onVoicePartial,
                        onFinal = viewModel::onVoiceFinal,
                        onListeningStarted = viewModel::onVoiceListeningStarted,
                        onError = viewModel::onVoiceError
                    )
                }
            }
            else -> Unit
        }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    DisposableEffect(Unit) {
        onDispose {
            speechManager.destroy()
            tts.shutdown()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LargeTitle(title = "Voice")

        ErrorBanner(
            message = state.error,
            onDismiss = viewModel::dismissVoiceError,
            modifier = Modifier.padding(horizontal = 20.dp)
        )

        // Live caption for the utterance currently being recognized.
        if (state.partial.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = state.partial,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Voice Conversation",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap the mic and just talk. Replies are spoken aloud and kept in the transcript below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )
                    }
                }
            } else {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(message)
                }
            }
        }

        VoiceOrb(
            phase = state.phase,
            onClick = {
                when (state.phase) {
                    VoicePhase.Idle -> {
                        if (!settings.isConfigured) {
                            viewModel.onVoiceError("Add your server URL, API key and model in Settings first.")
                            return@VoiceOrb
                        }
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        permissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                    }
                    VoicePhase.Listening -> speechManager.stopListening()
                    VoicePhase.Thinking, VoicePhase.Speaking -> Unit
                }
            }
        )

        Text(
            text = when (state.phase) {
                VoicePhase.Idle -> "Tap to talk"
                VoicePhase.Listening -> "Listening… tap to stop"
                VoicePhase.Thinking -> "Thinking…"
                VoicePhase.Speaking -> "Speaking…"
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 18.dp)
        )
    }
}

/** The big circular mic button; pulses while listening and shows progress while thinking. */
@Composable
private fun VoiceOrb(phase: VoicePhase, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse"
    )

    val orbColor = when (phase) {
        VoicePhase.Idle -> MaterialTheme.colorScheme.primary
        VoicePhase.Listening -> IosGreen
        VoicePhase.Thinking, VoicePhase.Speaking -> MaterialTheme.colorScheme.surfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onClick,
            enabled = phase != VoicePhase.Thinking && phase != VoicePhase.Speaking,
            shape = CircleShape,
            color = orbColor,
            shadowElevation = 6.dp,
            modifier = Modifier
                .size(92.dp)
                .scale(if (phase == VoicePhase.Listening) pulse else 1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                when (phase) {
                    VoicePhase.Thinking -> CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                    VoicePhase.Speaking -> Icon(
                        imageVector = Icons.Filled.VolumeUp,
                        contentDescription = "Speaking",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    else -> Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = if (phase == VoicePhase.Listening) "Stop" else "Start voice chat",
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
    }
}
