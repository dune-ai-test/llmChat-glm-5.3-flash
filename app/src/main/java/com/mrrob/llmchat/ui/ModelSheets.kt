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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mrrob.llmchat.data.ConnectionEntity
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType

/**
 * Shared model picker content for bottom sheets (chat header + home).
 * Favorites sort first inside each connection group; tapping picks the model.
 */
@Composable
fun ModelSheetContent(
    groups: List<Pair<ConnectionEntity, List<String>>>,
    current: String,
    favorites: List<String>,
    onToggleFavorite: (String) -> Unit,
    onSelect: (model: String, connectionId: String) -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Select Model", style = t.cardTitle.copy(fontSize = 20.sp), color = c.textPrimary)
            Text("Applies to this chat", style = t.caption, color = c.textMuted)
        }
        Spacer(Modifier.height(10.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            groups.forEachIndexed { gi, (conn, models) ->
                val ordered = models.sortedByDescending { it in favorites }
                com.mrrob.llmchat.ui.kit.SectionLabel(if (gi == 0) "Recommended" else conn.name)
                ordered.forEach { model ->
                    ModelRow(
                        model = model,
                        subtitle = "${conn.name} · ${if (conn.enabled) "Connected" else "Disabled"}",
                        online = conn.enabled,
                        selected = model == current,
                        favorite = model in favorites,
                        onToggleFavorite = { onToggleFavorite(model) },
                        onClick = { onSelect(model, conn.id) }
                    )
                }
            }
        }
    }
}

/** One selectable model row with a star for favorites and a status dot. */
@Composable
private fun ModelRow(
    model: String,
    subtitle: String,
    online: Boolean,
    selected: Boolean,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.accentTint else c.card)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) c.accent else c.border,
                RoundedCornerShape(16.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(start = 14.dp, end = 6.dp, top = 11.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (favorite) IconsL.starFilled else IconsL.star,
            if (favorite) "Unfavorite" else "Favorite",
            tint = if (favorite) c.warning else c.textMuted,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onToggleFavorite
                )
                .padding(5.dp))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                model,
                style = t.rowTitle.copy(fontWeight = FontWeight.Bold),
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.mrrob.llmchat.ui.kit.StatusDot(online = online)
                Spacer(Modifier.width(5.dp))
                Text(subtitle, style = t.caption, color = c.textSecondary, maxLines = 1)
            }
        }
        if (selected) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(c.accent),
                contentAlignment = Alignment.Center
            ) {
                Icon(IconsL.check, "Selected", tint = Color.White, modifier = Modifier.size(12.dp))
            }
        } else {
            Spacer(Modifier.width(20.dp))
        }
        Spacer(Modifier.width(8.dp))
    }
}

/**
 * One-place model management for the wizard: refresh from server, search,
 * multi-select with one default, favorites, and add-by-typing.
 */
@Composable
fun ModelManagerDialog(
    allModels: List<String>,
    selectedModels: List<String>,
    activeModel: String,
    favorites: List<String>,
    fetching: Boolean,
    error: String?,
    favoritesAvailable: Boolean,
    onRefresh: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onSetDefault: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onAddModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    var query by remember { mutableStateOf("") }
    val filtered = remember(allModels, query, favoritesAvailable) {
        val favs = if (favoritesAvailable) favorites else emptyList()
        (if (query.isBlank()) allModels else allModels.filter { it.contains(query.trim(), ignoreCase = true) })
            .sortedByDescending { it in favs }
            .distinct()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = RoundedCornerShape(18.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Models", style = t.cardTitle, color = c.textPrimary, modifier = Modifier.weight(1f))
                Text(
                    if (fetching) "Refreshing…" else "Refresh",
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = c.accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !fetching && favoritesAvailable
                        ) { onRefresh() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
                if (fetching) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = c.accent)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Search / add field
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.bg)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp)
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = t.rowTitle.copy(color = c.textPrimary),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterStart),
                        decorationBox = { inner ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(IconsL.search, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(8.dp))
                                Box(Modifier.weight(1f)) {
                                    if (query.isEmpty()) {
                                        Text("Search or type a new model id…", style = t.rowTitle, color = c.textMuted, maxLines = 1)
                                    }
                                    inner()
                                }
                            }
                        }
                    )
                }
                if (error != null) {
                    Text(error, style = t.caption, color = c.danger)
                }
                if (query.isNotBlank() && query.trim() !in allModels && query.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.accentTint)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                onAddModel(query.trim())
                                query = ""
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(IconsL.plus, null, tint = c.accent, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Add \"${query.trim()}\"",
                            style = t.rowTitle.copy(fontWeight = FontWeight.SemiBold),
                            color = c.accent
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (allModels.isEmpty() && !fetching && favoritesAvailable) {
                        Text("No models yet — tap Refresh to load from the server.", style = t.caption, color = c.textMuted)
                    }
                    filtered.forEach { model ->
                        val isSelected = model in selectedModels
                        val isDefault = model == activeModel
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isDefault) c.accentTint else c.card)
                                .border(1.dp, if (isDefault) c.accent else c.border, RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onToggleSelect(model) }
                                .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (model in favorites) IconsL.starFilled else IconsL.star,
                                "Favorite",
                                tint = if (model in favorites) c.warning else c.textMuted,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = { onToggleFavorite(model) }
                                    )
                                    .padding(5.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text(
                                    model,
                                    style = t.rowTitle.copy(fontWeight = if (isDefault) FontWeight.Bold else FontWeight.Normal),
                                    color = c.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isSelected && !isDefault) {
                                    Text(
                                        "Make default",
                                        style = t.tiny.copy(fontWeight = FontWeight.SemiBold),
                                        color = c.accent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) { onSetDefault(model) }
                                            .padding(horizontal = 2.dp)
                                    )
                                } else if (isDefault) {
                                    Text(
                                        "Default",
                                        style = t.tiny.copy(fontWeight = FontWeight.SemiBold),
                                        color = c.accent
                                    )
                                }
                            }
                            Icon(
                                if (isSelected) IconsL.checkCircle else IconsL.check,
                                null,
                                tint = if (isSelected) c.accent else c.textMuted.copy(alpha = 0.35f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    if (selectedModels.isEmpty()) "Close" else "${selectedModels.size} selected · Done",
                    color = c.accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close", color = c.textSecondary) }
        }
    )
}
