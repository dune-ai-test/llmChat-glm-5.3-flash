package com.mrrob.llmchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrrob.llmchat.AppViewModel
import com.mrrob.llmchat.data.ConnectionEntity
import com.mrrob.llmchat.ui.kit.AsterButton
import com.mrrob.llmchat.ui.kit.ButtonVariant
import com.mrrob.llmchat.ui.kit.IconTile
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.kit.StatusPill
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType

/**
 * 03 / 05 / 33 — Home dashboard with three states: connected, no connection
 * and the offline overlay (banner + queue card + dimmed cards).
 */
@Composable
fun HomeScreen(
    app: AppViewModel,
    onOpenChat: (String) -> Unit,
    onSeeAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onChangeModel: () -> Unit,
    onVoice: () -> Unit
) {
    val connections by app.connections.collectAsStateWithLifecycle()
    val conversations by app.conversations.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()
    val online by app.online.collectAsStateWithLifecycle()
    val todayCount by app.todayMessageCount.collectAsStateWithLifecycle()
    val queued by app.queuedCount.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    val t = LocalType.current

    val active: ConnectionEntity? = connections.firstOrNull { it.isDefault } ?: connections.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
    ) {
        if (!online) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(c.warningTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(IconsL.wifiOff, null, tint = c.warning, modifier = Modifier.size(15.dp))
                }
                Spacer(Modifier.size(10.dp))
                Text(
                    "You're offline — chats are saved locally",
                    style = t.desc.copy(fontWeight = FontWeight.SemiBold),
                    color = c.warning
                )
            }
        }

        if (queued > 0 && online) {
            OfflineQueueCard(count = queued)
            Spacer(Modifier.height(10.dp))
        }

        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        greeting(),
                        style = t.pageTitle,
                        color = c.textPrimary
                    )
                    Text(
                        if (active == null) "Ready to get started?" else "Ready to start a conversation?",
                        style = t.caption.copy(fontSize = 14.sp),
                        color = c.textSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (active != null) {
                        StatusPill(status = if (online) "Connected" else "Offline", online = online)
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(c.accentTint)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onOpenSettings
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("M", style = t.rowTitle, color = c.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (active == null) {
                HomeNoConnection(app = app)
            } else {
                // Current model card
                ModelCard(
                    connection = active,
                    dimmed = !online,
                    onChangeModel = onChangeModel,
                    onManage = { app.startEditConnection(active) }
                )

                // Quick actions
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickAction(IconsL.chatAdd, "New Chat", "Start a text conversation", Modifier.weight(1f)) {
                            app.startNewChat()
                        }
                        QuickAction(IconsL.mic, "Voice Chat", "Talk naturally with your model", Modifier.weight(1f)) {
                            onVoice()
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        QuickAction(IconsL.arrowSwap, "Switch Model", "Change the active LLM", Modifier.weight(1f), dimmed = !online) {
                            onChangeModel()
                        }
                        QuickAction(IconsL.server, "Connections", "Manage API providers", Modifier.weight(1f)) {
                            app.selectTab(com.mrrob.llmchat.AsterTab.CONNECTIONS)
                        }
                    }
                }

                // Recent
                if (conversations.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Recent", style = t.sectionTitle, color = c.textPrimary)
                            Text(
                                "See All",
                                style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                                color = c.accent,
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onSeeAll
                                )
                            )
                        }
                        conversations.take(3).forEach { conv ->
                            ChatListRow(
                                conversation = conv,
                                showChip = false,
                                onClick = { onOpenChat(conv.id) }
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(Modifier.height(28.dp))
                        Text("Start your first conversation", style = t.cardTitle, color = c.textPrimary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Tap New Chat above and talk to your model.",
                            style = t.caption,
                            color = c.textSecondary
                        )
                        Spacer(Modifier.height(16.dp))
                        AsterButton(
                            text = "New Chat",
                            modifier = Modifier.fillMaxWidth(0.6f),
                            onClick = { app.startNewChat() }
                        )
                    }
                }

                // Today's activity
                ActivityCard(
                    messages = todayCount,
                    conversations = conversations.size,
                    voice = conversations.count { it.voice }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OfflineQueueCard(count: Int) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(c.fill)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(IconsL.cloudOff, null, tint = c.textSecondary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("$count messages queued", style = t.rowTitle, color = c.textPrimary)
            Text("Will send automatically when you reconnect", style = t.caption, color = c.textSecondary)
        }
    }
}

@Composable
private fun ModelCard(
    connection: ConnectionEntity,
    dimmed: Boolean,
    onChangeModel: () -> Unit,
    onManage: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.7f else 1f)
            .clip(RoundedCornerShape(24.dp))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconTile(
                icon = IconsL.sparkles,
                size = 46.dp,
                tileRadius = 16.dp,
                iconSize = 22.dp,
                background = if (dimmed) c.fill else c.accentTint,
                tint = if (dimmed) c.textMuted else c.accent
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(connection.activeModel.ifBlank { "No model selected" }, style = t.heroTitle, color = c.textPrimary)
                Text(
                    "${providerLabel(connection.provider)} · ${connection.name}",
                    style = t.desc,
                    color = c.textSecondary
                )
            }
        }
        if (!dimmed) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Change Model",
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.accent,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onChangeModel
                    )
                )
                Text(
                    "Manage Connection",
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textSecondary,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onManage
                    )
                )
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    onClick: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(20.dp))
            .alpha(if (dimmed) 0.5f else 1f)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconTile(
            icon = icon,
            size = 40.dp,
            tileRadius = 13.dp,
            iconSize = 19.dp,
            background = c.fill,
            tint = if (dimmed) c.textMuted else c.accent
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = t.rowTitle.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary, maxLines = 1)
            Text(subtitle, style = t.tiny.copy(fontSize = 11.sp, lineHeight = 14.sp), color = c.textSecondary, maxLines = 1)
        }
    }
}

@Composable
private fun ActivityCard(messages: Int, conversations: Int, voice: Int) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.fill)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            "TODAY'S ACTIVITY",
            style = t.caption.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                letterSpacing = 0.8.sp
            ),
            color = c.textMuted
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Stat("$messages", "Messages")
            Stat("$conversations", "Conversations")
            Stat("$voice", "Voice chats")
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = t.sectionTitle, color = c.textPrimary)
        Text(label, style = t.caption, color = c.textSecondary)
    }
}

/** 05 — No-connection first-run experience. */
@Composable
private fun HomeNoConnection(app: AppViewModel) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp, bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(c.card)
                .border(1.dp, c.border, RoundedCornerShape(26.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(IconsL.server, null, tint = c.accent, modifier = Modifier.size(34.dp))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Connect your first model", style = t.heroTitle, color = c.textPrimary)
            Text(
                "Add an API endpoint and start chatting with your own LLM.",
                style = t.caption.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = c.textSecondary,
                modifier = Modifier.padding(horizontal = 40.dp)
            )
        }
        AsterButton(
            text = "Add Connection",
            modifier = Modifier.width(280.dp),
            onClick = { app.startWizard() }
        )
        AsterButton(
            text = "How it works",
            variant = ButtonVariant.SECONDARY,
            modifier = Modifier.width(280.dp),
            onClick = { /* info lives in Settings > About; keep simple */ }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("OpenAI", "OpenAI Compatible", "Custom API").forEach {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(c.card)
                        .border(1.dp, c.border, RoundedCornerShape(99.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            app.startWizard()
                            app.wizardChooseProvider(
                                when (it) {
                                    "OpenAI" -> "OPENAI"
                                    "OpenAI Compatible" -> "OPENAI_COMPATIBLE"
                                    else -> "CUSTOM_API"
                                }
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(it, style = t.tiny.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), color = c.textSecondary)
                }
            }
        }
    }
}

/** Map provider keys to their display labels. */
fun providerLabel(provider: String): String = when (provider) {
    "OPENAI" -> "OpenAI API"
    "OPENAI_COMPATIBLE" -> "OpenAI Compatible API"
    "LOCAL_SERVER" -> "Local Server"
    else -> "Custom API"
}
