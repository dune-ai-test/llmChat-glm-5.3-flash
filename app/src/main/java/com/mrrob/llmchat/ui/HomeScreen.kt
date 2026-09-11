package com.mrrob.llmchat.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.mrrob.llmchat.ui.kit.ScreenTopBar
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType

/** 03 / 05 / 33 — Home dashboard: model card, quick chats, recents, offline states. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    app: AppViewModel,
    onOpenChat: (String) -> Unit,
    onSeeAll: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onVoice: () -> Unit
) {
    val connections by app.connections.collectAsStateWithLifecycle()
    val conversations by app.conversations.collectAsStateWithLifecycle()
    val settings by app.settings.collectAsStateWithLifecycle()
    val online by app.online.collectAsStateWithLifecycle()
    val queued by app.queuedCount.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    val t = LocalType.current
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var queueDismissed by remember { mutableStateOf(false) }

    val active: ConnectionEntity? = remember(connections) {
        connections.firstOrNull { it.isDefault } ?: connections.firstOrNull()
    }

    Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
        // Fixed top bar: app name + avatar (opens Settings)
        ScreenTopBar(
            title = "Aster",
            actions = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(c.accentTint)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenSettings
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        settings.displayName.take(1).uppercase().ifBlank { "M" },
                        style = t.rowTitle,
                        color = c.accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        )

        // Greeting under the top bar - re-evaluates every minute so it shifts
        // from "Good morning" to "Good afternoon"/"Good evening" while open.
        var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(60_000)
                nowMillis = System.currentTimeMillis()
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(greetingFor(nowMillis), style = t.largeTitle, color = c.textPrimary, maxLines = 1)
            Text(
                settings.displayName.ifBlank { "there" },
                style = t.caption.copy(fontSize = 14.sp, lineHeight = 18.sp),
                color = c.textSecondary,
                maxLines = 1
            )
        }
        if (!online) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
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
                Spacer(Modifier.width(10.dp))
                Text(
                    "You're offline — chats are saved locally",
                    style = t.desc.copy(fontWeight = FontWeight.SemiBold),
                    color = c.warning
                )
            }
        } else if (queued > 0 && !queueDismissed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.fill)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(IconsL.cloudOff, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    "$queued queued — sending automatically",
                    style = t.desc,
                    color = c.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    IconsL.close, "Dismiss", tint = c.textMuted,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { queueDismissed = true }
                )
            }
        }

        if (active == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                HomeNoConnection(app = app)
            }
        } else {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                ModelCard(
                    connection = active,
                    dimmed = !online,
                    onChangeModel = { showModelSheet = true },
                    onManage = { app.startEditConnection(active) }
                )

                // Quick actions — the two primary entries
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickAction(
                        IconsL.chatAdd, "New Chat", "Start a text conversation",
                        Modifier.weight(1f)
                    ) { app.startNewChat() }
                    QuickAction(
                        IconsL.mic, "Voice Chat", "Talk with your model",
                        Modifier.weight(1f)
                    ) { onVoice() }
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
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Start your first conversation", style = t.cardTitle, color = c.textPrimary)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap New Chat above and talk to your model.",
                            style = t.caption,
                            color = c.textSecondary
                        )
                        Spacer(Modifier.height(16.dp))
                        AsterButton(
                            text = "New Chat",
                            modifier = Modifier.width(220.dp),
                            onClick = { app.startNewChat() }
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showModelSheet && active != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val groups = remember(connections) {
            connections.filter { it.enabled }.map { conn ->
                conn to (app.repo.modelsOf(conn) + conn.activeModel).filter(String::isNotBlank).distinct()
            }.filter { it.second.isNotEmpty() }
        }
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            sheetState = sheetState,
            containerColor = c.card
        ) {
            ModelSheetContent(
                groups = groups,
                current = active.activeModel,
                favorites = app.favoriteModels(),
                onToggleFavorite = app::toggleFavoriteModel,
                onSelect = { model, connId ->
                    app.setDefaultModel(connId, model)
                    showModelSheet = false
                }
            )
        }
    }
}

// ── Pieces ───────────────────────────────────────────────────────────────────

@Composable
private fun ModelCard(
    connection: ConnectionEntity,
    dimmed: Boolean,
    onChangeModel: () -> Unit,
    onManage: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dimmed) 0.7f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconTile(
            icon = IconsL.sparkles,
            size = 44.dp,
            tileRadius = 14.dp,
            iconSize = 20.dp,
            background = if (dimmed) c.fill else c.accentTint,
            tint = if (dimmed) c.textMuted else c.accent
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Text(
                connection.activeModel.ifBlank { "No model selected" },
                style = t.rowTitle.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                color = c.textPrimary,
                maxLines = 1
            )
            Text(
                "${providerLabel(connection.provider)} · ${connection.name}",
                style = t.tiny.copy(fontSize = 12.sp),
                color = c.textMuted,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(if (dimmed) c.fill else c.accentTint)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onChangeModel
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Change",
                style = t.caption.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (dimmed) c.textSecondary else c.accent
                )
            )
            Icon(
                IconsL.chevronDown, null,
                tint = if (dimmed) c.textSecondary else c.accent,
                modifier = Modifier.size(13.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onManage
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(IconsL.settings, "Manage Connection", tint = c.textMuted, modifier = Modifier.size(17.dp))
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

/** 05 — No-connection first-run experience. */
@Composable
private fun HomeNoConnection(app: AppViewModel) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = Modifier
            .fillMaxWidth(),
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
            onClick = { }
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
