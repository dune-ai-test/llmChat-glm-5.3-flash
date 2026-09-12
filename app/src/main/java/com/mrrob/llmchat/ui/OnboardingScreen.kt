package com.mrrob.llmchat.ui

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrrob.llmchat.ui.kit.AsterButton
import com.mrrob.llmchat.ui.kit.ButtonVariant
import com.mrrob.llmchat.ui.kit.IconTile
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType

/** 02 — 3-page onboarding pager. "Get Started" opens the wizard; "Skip" enters the app. */
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit, onSkip: () -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
    val pagerState = rememberPagerState(pageCount = { 3 })
    val scope = rememberCoroutineScope()
    val lastPage = pagerState.currentPage == 2

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                if (lastPage) "" else "Skip",
                style = t.body.copy(fontWeight = FontWeight.SemiBold),
                color = c.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkip
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> OnboardPage(
                    badge = "Welcome to Aster Judge",
                    headline = "Every model you use, one quiet workspace.",
                    body = "Connect any OpenAI-compatible API and talk to your models with text or voice.",
                    props = listOf(
                        IconsL.keyRound to ("Connect any provider" to "OpenAI, local models, or your own endpoint."),
                        IconsL.chatBubble to ("Chat with depth" to "Markdown, code blocks, and full history."),
                        IconsL.mic to ("Talk naturally" to "Voice conversations that stay in context.")
                    )
                )
                1 -> OnboardPage(
                    badge = "Yours to command",
                    headline = "Bring your own keys and models.",
                    body = "Multiple connections, instant model switching, and full control over generation.",
                    props = listOf(
                        IconsL.server to ("Many connections" to "Add, edit and set a default provider."),
                        IconsL.sparkles to ("Model picker" to "Search, favorite and pick per conversation."),
                        IconsL.slider to ("Tunable generation" to "Temperature, tokens, prompts and more.")
                    )
                )
                else -> OnboardPage(
                    badge = "Private by design",
                    headline = "Your data stays on your device.",
                    body = "Keys are encrypted locally and requests go only to the endpoint you configure.",
                    props = listOf(
                        IconsL.lock to ("Encrypted keys" to "API keys never leave this device."),
                        IconsL.archive to ("Local history" to "Chats, favorites and drafts on-device."),
                        IconsL.download to ("Export anytime" to "Back up or share your conversations.")
                    )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                repeat(3) { i ->
                    val active = pagerState.currentPage == i
                    Box(
                        Modifier
                            .size(if (active) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(if (active) c.accent else c.border)
                    )
                }
            }
            AsterButton(
                text = if (lastPage) "Get Started" else "Next",
                onClick = {
                    if (lastPage) onGetStarted()
                    else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            )
        }
    }
}

@Composable
private fun OnboardPage(
    badge: String,
    headline: String,
    body: String,
    props: List<Pair<ImageVector, Pair<String, String>>>
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(c.accentTint)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(badge, style = t.caption.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(headline, style = t.largeTitle.copy(fontSize = 30.sp, lineHeight = 35.sp), color = c.textPrimary)
            Text(body, style = t.body.copy(color = c.textSecondary), textAlign = TextAlign.Left)
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            props.forEach { (icon, pair) ->
                val title = pair.first
                val desc = pair.second
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
        }
    }
}
