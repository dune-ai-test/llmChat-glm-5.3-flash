package com.mrrob.llmchat.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrrob.llmchat.ui.kit.AsterButton
import com.mrrob.llmchat.ui.kit.ButtonVariant
import com.mrrob.llmchat.ui.kit.IconTile
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType

/**
 * 02 — Onboarding: badge, headline, the three value props and the two CTAs.
 * "Get Started" starts the connection wizard; "Skip" (long-press-free text
 * link below) jumps straight to home.
 */
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit, onSkip: () -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 36.dp),
            verticalArrangement = Arrangement.spacedBy(26.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.accentTint)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    "Welcome to Aster",
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.accent
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Every model you use, one quiet workspace.",
                    style = t.largeTitle.copy(fontSize = 30.sp, lineHeight = 35.sp),
                    color = c.textPrimary
                )
                Text(
                    "Connect any OpenAI-compatible API and talk to your models with text or voice.",
                    style = t.body.copy(color = c.textSecondary)
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                ValueProp(IconsL.keyRound, "Connect any provider", "OpenAI, local models, or your own endpoint.")
                ValueProp(IconsL.chatBubble, "Chat with depth", "Markdown, code blocks, and full history.")
                ValueProp(IconsL.mic, "Talk naturally", "Voice conversations that stay in context.")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AsterButton(text = "Get Started", onClick = onGetStarted)
            AsterButton(
                text = "Skip",
                variant = ButtonVariant.SECONDARY,
                onClick = onSkip
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                repeat(3) { i ->
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (i == 0) c.accent else c.border)
                    )
                }
            }
        }
    }
}

@Composable
private fun ValueProp(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, desc: String) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        IconTile(icon = icon, size = 46.dp, tileRadius = 15.dp, iconSize = 20.dp, background = c.fill)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = t.rowTitle.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary)
            Text(desc, style = t.desc, color = c.textSecondary)
        }
    }
}
