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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrrob.llmchat.AppViewModel
import com.mrrob.llmchat.data.ConversationEntity
import com.mrrob.llmchat.ui.kit.AsterBackHeader
import com.mrrob.llmchat.ui.kit.AsterCard
import com.mrrob.llmchat.ui.kit.AsterRow
import com.mrrob.llmchat.ui.kit.CardDivider
import com.mrrob.llmchat.ui.kit.CircleIconButton
import com.mrrob.llmchat.ui.kit.ScreenTopBar
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType

/** 15 — Chats tab: grouped history with search field and new-chat action. */
@Composable
fun ChatsTab(
    app: AppViewModel,
    onOpenChat: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenArchived: () -> Unit
) {
    val conversations by app.conversations.collectAsStateWithLifecycle()
    val archived by app.archived.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    val t = LocalType.current
    var menuConversation by remember { mutableStateOf<ConversationEntity?>(null) }
    var renameTarget by remember { mutableStateOf<ConversationEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ConversationEntity?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
        ScreenTopBar(
            title = "Chats",
            actions = {
                CircleIconButton(
                    icon = IconsL.plus,
                    background = c.accent,
                    tint = androidx.compose.ui.graphics.Color.White,
                    bordered = false,
                    iconSize = 19.dp,
                    onClick = { app.startNewChat() }
                )
            }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {

        // Search field (tap to open the search screen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(c.card)
                .border(1.dp, c.border, RoundedCornerShape(99.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onOpenSearch
                )
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(IconsL.search, null, tint = c.textMuted, modifier = Modifier.size(17.dp))
            Spacer(Modifier.size(10.dp))
            Text("Search conversations", style = t.body.copy(fontSize = 14.5.sp), color = c.textMuted)
        }
        Spacer(Modifier.height(16.dp))

        if (conversations.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(IconsL.chatBubble, null, tint = c.textMuted, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text("No conversations yet", style = t.cardTitle, color = c.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Your chats will appear here.", style = t.caption, color = c.textSecondary)
            }
        } else {
            groupedSections(conversations).forEach { (section, items) ->
                Text(
                    section.label,
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                    color = c.textMuted,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
                items.forEach { conv ->
                    ChatListRow(
                        conversation = conv,
                        onClick = { onOpenChat(conv.id) },
                        onLongClick = { menuConversation = conv }
                    )
                }
            }
        }

        if (archived.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            AsterCard(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                onClick = onOpenArchived
            ) {
                AsterRow(
                    label = "Archived chats",
                    icon = IconsL.archive,
                    value = "${archived.size}",
                    showChevron = true,
                    onClick = onOpenArchived
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        }
    }

    // Context menu
    menuConversation?.let { conv ->
        AlertDialog(
            onDismissRequest = { menuConversation = null },
            containerColor = c.card,
            titleContentColor = c.textPrimary,
            textContentColor = c.textSecondary,
            title = { Text(conv.title, style = t.cardTitle) },
            text = {
                Column {
                    MenuAction("Rename") { renameTarget = conv; menuConversation = null }
                    MenuAction(if (conv.pinned) "Unpin" else "Pin") { app.togglePin(conv.id); menuConversation = null }
                    MenuAction(if (conv.starred) "Unstar" else "Star") { app.toggleStar(conv.id); menuConversation = null }
                    MenuAction("Duplicate") { app.duplicateConversation(conv.id); menuConversation = null }
                    MenuAction("Archive") { app.toggleArchive(conv.id); menuConversation = null }
                    MenuAction("Delete", danger = true) { deleteTarget = conv; menuConversation = null }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuConversation = null }) { Text("Close", color = c.textSecondary) }
            }
        )
    }

    renameTarget?.let { conv ->
        var text by remember { mutableStateOf(conv.title) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = c.card,
            title = { Text("Rename conversation", style = t.cardTitle, color = c.textPrimary) },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.bg)
                        .border(1.dp, c.border, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        singleLine = true,
                        textStyle = t.body.copy(color = c.textPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (text.isNotBlank()) app.renameConversation(conv.id, text.trim())
                        renameTarget = null
                    }
                ) { Text("Save", color = c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = c.textSecondary) }
            }
        )
    }

    deleteTarget?.let { conv ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = c.card,
            title = { Text("Delete this conversation?", style = t.cardTitle, color = c.textPrimary) },
            text = { Text("This can't be undone from here, but you'll get a brief Undo window.", style = t.desc, color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = { app.deleteConversation(conv.id); deleteTarget = null }) {
                    Text("Delete", color = c.danger, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = c.textSecondary) }
            }
        )
    }
}

@Composable
private fun MenuAction(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = LocalScheme.current
    Text(
        label,
        style = LocalType.current.rowTitle,
        color = if (danger) c.danger else c.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 8.dp, vertical = 12.dp)
    )
}


/** Archived conversations list: open or unarchive. */
@Composable
fun ArchivedScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val archived by app.archived.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    val t = LocalType.current

    Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
        ScreenTopBar(
            title = "Archived",
            leading = {
                CircleIconButton(
                    icon = IconsL.chevronLeft,
                    onClick = onBack,
                    size = 32.dp,
                    iconSize = 18.dp,
                    bordered = false,
                    tint = c.textPrimary
                )
            }
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            if (archived.isEmpty()) {
                Spacer(Modifier.height(60.dp))
                Text("No archived chats", style = t.cardTitle, color = c.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Long-press a chat in the list and choose Archive to keep it here.",
                    style = t.caption,
                    color = c.textSecondary
                )
            } else {
                archived.forEach { conv ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onOpenChat(conv.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(c.card)
                                .border(1.dp, c.border, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (conv.voice) IconsL.mic else IconsL.chatSquare, null,
                                tint = if (conv.voice) c.accent else c.textSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(conv.title, style = t.desc.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp), color = c.textPrimary, maxLines = 1)
                            if (conv.lastPreview.isNotBlank()) {
                                Text(conv.lastPreview, style = t.micro.copy(fontSize = 13.sp), color = c.textSecondary, maxLines = 1)
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        CircleIconButton(
                            icon = IconsL.archive,
                            onClick = { app.toggleArchive(conv.id) },
                            size = 36.dp,
                            iconSize = 16.dp
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 06 — Recent activity (full grouped list reached from Home "See All"). */
@Composable
fun RecentActivityScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit,
    onOpenSearch: () -> Unit
) {
    val conversations by app.conversations.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    val t = LocalType.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Recent", style = t.largeTitle, color = c.textPrimary)
            CircleIconButton(icon = IconsL.search, onClick = onOpenSearch)
        }
        Spacer(Modifier.height(12.dp))
        if (conversations.isEmpty()) {
            Text("Nothing here yet.", style = t.desc, color = c.textSecondary)
        } else {
            groupedSections(conversations).forEach { (section, items) ->
                Text(
                    section.name.uppercase(),
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
                    color = c.textMuted,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
                items.forEach { conv ->
                    ChatListRow(conversation = conv, onClick = { onOpenChat(conv.id) })
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** 16 — Search screen: active field, filters, results with highlight, recents. */
@Composable
fun SearchScreen(
    app: AppViewModel,
    onBack: () -> Unit,
    onOpenChat: (String) -> Unit
) {
    val query by app.query.collectAsStateWithLifecycle()
    val filter by app.filter.collectAsStateWithLifecycle()
    val results by app.searchResults.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    val t = LocalType.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                IconsL.arrowBack,
                "Back",
                tint = c.textPrimary,
                modifier = Modifier
                    .size(21.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    )
            )
            Spacer(Modifier.size(12.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.card)
                    .border(1.5.dp, c.accent, RoundedCornerShape(99.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(IconsL.search, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(9.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { app.setQuery(it) },
                    singleLine = true,
                    textStyle = t.body.copy(fontSize = 15.sp, color = c.textPrimary),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.size(12.dp))
            Text(
                "Cancel",
                style = t.body.copy(fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold),
                color = c.accent,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBack
                )
            )
        }
        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "All", "voice" to "Voice", "starred" to "Starred").forEach { (key, label) ->
                com.mrrob.llmchat.ui.kit.AsterChip(
                    text = label,
                    selected = filter == key,
                    onClick = { app.setFilter(key) }
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (query.isBlank()) {
            val recents = app.recentSearches()
            if (recents.isNotEmpty()) {
                Text(
                    "RECENT SEARCHES",
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, fontSize = 11.sp),
                    color = c.textMuted
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    recents.forEach { term ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(99.dp))
                                .background(c.fill)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { app.setQuery(term) }
                                .padding(horizontal = 13.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(IconsL.history, null, tint = c.textMuted, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.size(6.dp))
                            Text(term, style = t.desc, color = c.textSecondary)
                        }
                    }
                }
            }
        } else {
            if (results.isEmpty()) {
                Spacer(Modifier.height(40.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No results for \"$query\"", style = t.cardTitle, color = c.textPrimary)
                    Spacer(Modifier.height(4.dp))
                    Text("Try a different word or filter.", style = t.caption, color = c.textSecondary)
                }
            } else {
                Text(
                    "${results.size} RESULT${if (results.size == 1) "" else "S"}",
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp, fontSize = 11.sp),
                    color = c.textMuted,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                results.forEach { conv ->
                    ChatListRow(
                        conversation = conv,
                        highlight = query,
                        onClick = {
                            app.recordRecentSearch(query)
                            onOpenChat(conv.id)
                        }
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
