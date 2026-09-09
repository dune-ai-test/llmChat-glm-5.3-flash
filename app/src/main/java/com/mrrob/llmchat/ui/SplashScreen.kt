package com.mrrob.llmchat.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType
import kotlinx.coroutines.delay

/** 01 — Splash: logo tile, wordmark, animated loading dots, version. */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current

    LaunchedEffect(Unit) {
        delay(1400)
        onFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.accentTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(IconsL.sparkles, null, tint = c.accent, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Aster", style = t.heroTitle.copy(fontSize = 34.sp, letterSpacing = (-0.5).sp), color = c.textPrimary)
            Spacer(Modifier.height(4.dp))
            Text("Your AI workspace", style = t.desc.copy(fontSize = 15.sp), color = c.textSecondary)
        }

        val transition = rememberInfiniteTransition(label = "dots")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { i ->
                val pulse by transition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.25f,
                    animationSpec = infiniteRepeatable(
                        tween(durationMillis = 900, delayMillis = i * 180, easing = LinearEasing),
                        RepeatMode.Reverse
                    ),
                    label = "dot$i"
                )
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(c.accent)
                        .alpha(pulse)
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Version 1.2.0", style = t.caption, color = c.textMuted)
    }
}
