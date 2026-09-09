package com.mrrob.llmchat.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrrob.llmchat.VoiceViewModel
import com.mrrob.llmchat.data.MessageEntity
import com.mrrob.llmchat.speech.SpeechRecognitionManager
import com.mrrob.llmchat.speech.TtsPlayer
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.kit.StatusDot
import com.mrrob.llmchat.ui.theme.AsterColors
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType

/**
 * 22 / 23 / 24 / 25 — Voice chat. A fullscreen orb with a bottom control
 * deck and an always-available transcript panel. States: ready → listening
 * → processing → speaking, with continuous mode and barge-in.
 */
@Composable
fun VoiceScreen(
    vm: VoiceViewModel,
    onExit: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    val context = LocalContext.current

    val phase by vm.phase.collectAsStateWithLifecycle()
    val transcript by vm.transcript.collectAsStateWithLifecycle()
    val partial by vm.partial.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()

    var showTranscript by remember { mutableStateOf(true) }
    var sessionActive by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }

    val recognizer = remember { SpeechRecognitionManager(context) }
    val tts = remember { TtsPlayer(context).apply { rate = settings.speakingRate } }
    val listState = rememberLazyListState()

    val hasPermission = remember { mutableStateOf(
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    ) }

    fun startListening() {
        recognizer.startListening(
            onPartial = vm::onPartial,
            onFinal = { text ->
                if (text.isBlank()) {
                    vm.setPhase(VoiceViewModel.Phase.READY)
                } else {
                    vm.onUtterance(text)
                }
            },
            onListeningStarted = { vm.setPhase(VoiceViewModel.Phase.LISTENING) },
            onError = { msg -> vm.onMicError(msg) }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission.value = granted
        if (granted && sessionActive && phase == VoiceViewModel.Phase.READY) {
            startListening()
        }
    }

    LaunchedEffect(hasPermission.value) {
        if (!hasPermission.value && sessionActive) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Phase 25: speak the newest assistant message when entering SPEAKING.
    LaunchedEffect(phase) {
        when (phase) {
            VoiceViewModel.Phase.SPEAKING -> {
                val last = transcript.lastOrNull { it.role == "assistant" }
                if (last != null && settings.voiceTts) {
                    tts.speak(com.mrrob.llmchat.data.Markdown.toPlainText(last.text)) {
                        vm.speakFinished()
                    }
                } else {
                    vm.speakFinished()
                }
            }
            VoiceViewModel.Phase.LISTENING -> if (!recognizer.isListening) {
                if (hasPermission.value) startListening()
            }
            VoiceViewModel.Phase.READY -> {
                if (settings.continuousVoice && sessionActive && !muted &&
                    transcript.isNotEmpty() && transcript.last().role == "assistant"
                ) {
                    if (hasPermission.value) startListening()
                }
            }
            else -> Unit
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recognizer.destroy()
            tts.shutdown()
        }
    }

    LaunchedEffect(transcript.size, partial) {
        if (transcript.isNotEmpty() && showTranscript) {
            listState.animateScrollToItem(transcript.lastIndex)
        }
    }

    val centerLabel = when (phase) {
        VoiceViewModel.Phase.READY -> if (!sessionActive) "Tap the mic to speak" else "Ready"
        VoiceViewModel.Phase.LISTENING -> "Listening"
        VoiceViewModel.Phase.PROCESSING -> "Thinking"
        VoiceViewModel.Phase.SPEAKING -> "Speaking"
    }
    val centerSub = when (phase) {
        VoiceViewModel.Phase.READY -> if (showTranscript) "" else "transcript hidden"
        VoiceViewModel.Phase.LISTENING -> "Tap to interrupt"
        VoiceViewModel.Phase.PROCESSING ->
            "${conversation?.model?.ifBlank { vm.connection()?.activeModel } ?: "Model"} is composing a reply"
        VoiceViewModel.Phase.SPEAKING -> "Tap to interrupt"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                IconsL.chevronDown, "Minimize", tint = c.textSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        recognizer.stopListening()
                        onExit()
                    }
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Voice Chat", style = t.rowTitle.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = c.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        conversation?.model?.ifBlank { vm.connection()?.activeModel ?: "" }
                            ?: vm.connection()?.activeModel ?: "",
                        style = t.caption,
                        color = c.textSecondary
                    )
                    StatusDot(online = true)
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (showTranscript) c.accentTint else c.card)
                    .border(1.dp, if (showTranscript) c.accentTint else c.border, RoundedCornerShape(99.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showTranscript = !showTranscript }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    IconsL.chatSquare, "Transcript",
                    tint = if (showTranscript) c.accent else c.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Transcript",
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (showTranscript) c.accent else c.textSecondary
                )
            }
        }

        // ── Transcript panel ───────────────────────────────────────────────────
        if (showTranscript) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 10.dp, bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(transcript, key = { it.id }) { message ->
                    VoiceBubble(message)
                }
                if (phase == VoiceViewModel.Phase.LISTENING && partial.isNotBlank()) {
                    item { VoiceUserLive(partial) }
                }
                if (phase == VoiceViewModel.Phase.PROCESSING) {
                    item { SkeletonBubble() }
                }
            }
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                VoiceOrb(phase = phase, micEnabled = true)
            }
        }

        // ── Error banner ───────────────────────────────────────────────────────
        error?.let { err ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.dangerTint)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { vm.clearError() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(IconsL.micOff, null, tint = c.danger, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(9.dp))
                Text(err.message, style = t.desc.copy(color = c.danger), modifier = Modifier.weight(1f))
                Text("Retry", style = t.desc.copy(fontWeight = FontWeight.SemiBold), color = c.accent,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        vm.clearError()
                        if (!hasPermission.value) permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        else if (phase == VoiceViewModel.Phase.READY) startListening()
                    }
                )
            }
        }

        // ── Control deck (23 / 24 / 25) ────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.card)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            VoiceWave(phase = phase, active = phase == VoiceViewModel.Phase.LISTENING || phase == VoiceViewModel.Phase.SPEAKING)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(centerLabel, style = t.body.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary)
                if (centerSub.isNotBlank()) {
                    Text(centerSub, style = t.caption, color = c.textMuted)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                DeckButton(IconsL.micOff, activeBg = muted, onClick = {
                    muted = !muted
                    if (muted) recognizer.stopListening()
                    else if (phase == VoiceViewModel.Phase.READY && sessionActive) startListening()
                })
                CenterMic(
                    phase = phase,
                    onClick = {
                        if (!sessionActive) {
                            sessionActive = true
                            muted = false
                        }
                        when (phase) {
                            VoiceViewModel.Phase.READY -> {
                                if (!hasPermission.value) {
                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    startListening()
                                }
                            }
                            VoiceViewModel.Phase.LISTENING -> recognizer.stopListening()
                            VoiceViewModel.Phase.PROCESSING -> vm.interrupt()
                            VoiceViewModel.Phase.SPEAKING -> {
                                tts.stop()
                                vm.interrupt()
                            }
                        }
                    }
                )
                DeckButton(IconsL.square, onClick = {
                    recognizer.stopListening()
                    tts.stop()
                    vm.setPhase(VoiceViewModel.Phase.READY)
                })
                DeckButton(IconsL.callEnd, danger = true, onClick = {
                    sessionActive = false
                    recognizer.stopListening()
                    tts.stop()
                    vm.endSession()
                    onExit()
                })
            }
        }
    }
}

// ── Pieces ───────────────────────────────────────────────────────────────────

@Composable
private fun VoiceBubble(message: MessageEntity) {
    val c = LocalScheme.current
    val t = LocalType.current
    if (message.role == "user") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                message.text,
                style = t.bodyTight,
                color = Color.White,
                modifier = Modifier
                    .widthIn(max = 225.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                    .background(c.accent)
                    .padding(horizontal = 15.dp, vertical = 11.dp)
            )
        }
    } else if (message.role == "assistant") {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
            Text(
                message.text,
                style = t.bodyTight,
                color = c.textPrimary,
                modifier = Modifier
                    .widthIn(max = 235.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
                    .background(c.card)
                    .border(1.dp, c.border, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
                    .padding(horizontal = 15.dp, vertical = 11.dp)
            )
        }
    } else {
        Text(message.text, style = t.tiny, color = c.textMuted, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun VoiceUserLive(text: String) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text,
                style = t.bodyTight,
                color = Color.White,
                modifier = Modifier
                    .widthIn(max = 225.dp)
                    .alpha(0.9f)
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                    .background(c.accent)
                    .padding(horizontal = 15.dp, vertical = 11.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                repeat(3) { i ->
                    val transition = rememberInfiniteTransition(label = "live$i")
                    val a by transition.animateFloat(
                        0.4f, 1f,
                        infiniteRepeatable(tween(700, delayMillis = i * 150), RepeatMode.Reverse),
                        label = "la$i"
                    )
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White.copy(alpha = a)))
                }
            }
        }
    }
}

@Composable
private fun SkeletonBubble() {
    val c = LocalScheme.current
    Column(
        modifier = Modifier
            .widthIn(max = 235.dp)
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(200.dp, 160.dp, 120.dp).forEach { w ->
            Box(Modifier.width(w).height(9.dp).clip(RoundedCornerShape(5.dp)).background(c.fill))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            repeat(3) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(c.textMuted))
            }
        }
    }
}

@Composable
private fun CenterMic(phase: VoiceViewModel.Phase, onClick: () -> Unit) {
    val c = LocalScheme.current
    val transition = rememberInfiniteTransition(label = "micpulse")
    val pulse by transition.animateFloat(1f, 1.12f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    val bg = when (phase) {
        VoiceViewModel.Phase.LISTENING -> c.accent
        VoiceViewModel.Phase.PROCESSING -> c.fill
        VoiceViewModel.Phase.SPEAKING -> c.accent
        else -> c.accent
    }
    val icon = when (phase) {
        VoiceViewModel.Phase.PROCESSING -> null
        VoiceViewModel.Phase.SPEAKING -> IconsL.square
        else -> IconsL.mic
    }
    Box(
        modifier = Modifier
            .size(76.dp)
            .scale(if (phase == VoiceViewModel.Phase.LISTENING) pulse else 1f)
            .clip(CircleShape)
            .background(bg)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when (phase) {
            VoiceViewModel.Phase.PROCESSING -> androidx.compose.material3.CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = Color.White,
                strokeWidth = 2.5.dp
            )
            else -> Icon(icon!!, null, tint = Color.White, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
private fun DeckButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    danger: Boolean = false,
    activeBg: Boolean = false,
    onClick: () -> Unit
) {
    val c = LocalScheme.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                when {
                    danger -> c.dangerTint
                    activeBg -> c.accentTint
                    else -> c.fill
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, null,
            tint = if (danger) c.danger else c.textSecondary,
            modifier = Modifier.size(20.dp)
        )
    }
}

/** The fullscreen orb (22): layered circles + gradient core. */
@Composable
fun VoiceOrb(phase: VoiceViewModel.Phase, micEnabled: Boolean) {
    val c = LocalScheme.current
    val transition = rememberInfiniteTransition(label = "orb")
    val breathe by transition.animateFloat(1f, 1.06f,
        infiniteRepeatable(tween(1600), RepeatMode.Reverse), label = "breathe")
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(212.dp)
                .clip(CircleShape)
                .background(c.accent.copy(alpha = 0.08f))
                .border(1.dp, c.accent.copy(alpha = 0.13f), CircleShape)
        )
        Box(
            Modifier
                .size(158.dp)
                .clip(CircleShape)
                .background(c.accentTint)
        )
        Box(
            Modifier
                .size(104.dp)
                .scale(breathe)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(c.accentDeep, c.accent))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (micEnabled) IconsL.mic else IconsL.micOff,
                null, tint = Color.White, modifier = Modifier.size(42.dp)
            )
        }
    }
}

/** 10-bar waveform that morphs per phase. */
@Composable
private fun VoiceWave(phase: VoiceViewModel.Phase, active: Boolean) {
    val c = LocalScheme.current
    val heights: List<Float> = when (phase) {
        VoiceViewModel.Phase.PROCESSING -> List(10) { 6f }
        VoiceViewModel.Phase.LISTENING -> listOf(8f, 14f, 24f, 30f, 18f, 26f, 32f, 20f, 12f, 16f)
        VoiceViewModel.Phase.SPEAKING -> listOf(12f, 20f, 28f, 34f, 24f, 30f, 18f, 26f, 14f, 10f)
        else -> listOf(8f, 16f, 26f, 36f, 44f, 44f, 36f, 26f, 16f, 8f)
    }
    val barColor = if (phase == VoiceViewModel.Phase.PROCESSING) c.border else c.accent
    Row(
        modifier = Modifier.height(34.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val transition = rememberInfiniteTransition(label = "wave")
        heights.forEachIndexed { i, h ->
            val live by transition.animateFloat(
                h, (h * 0.55f).coerceAtLeast(4f),
                infiniteRepeatable(tween(420 + (i % 4) * 90, delayMillis = i * 40), RepeatMode.Reverse),
                label = "bar$i"
            )
            Box(
                Modifier
                    .width(3.5.dp)
                    .height(if (active) live.dp else h.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        if (active) barColor.copy(alpha = (0.35f + 0.06f * i)) else barColor
                    )
            )
        }
    }
}

