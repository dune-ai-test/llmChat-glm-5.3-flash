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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrrob.llmchat.AppViewModel
import com.mrrob.llmchat.data.ConnectionEntity
import com.mrrob.llmchat.ui.kit.AsterCard
import com.mrrob.llmchat.ui.kit.CircleIconButton
import com.mrrob.llmchat.ui.kit.IconTile
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.kit.InfoStrip
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType

/** 07 — Connections tab: provider cards with status, default badge, actions. */
@Composable
fun ConnectionsTab(
    app: AppViewModel,
    onEdit: (ConnectionEntity) -> Unit,
    onAdd: () -> Unit
) {
    val connections by app.connections.collectAsStateWithLifecycle()
    val online by app.online.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    val t = LocalType.current
    var menuFor by remember { mutableStateOf<ConnectionEntity?>(null) }
    var renameTarget by remember { mutableStateOf<ConnectionEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ConnectionEntity?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Connections", style = t.largeTitle, color = c.textPrimary)
            CircleIconButton(
                icon = IconsL.plus,
                background = c.accent,
                tint = Color.White,
                bordered = false,
                iconSize = 19.dp,
                onClick = onAdd
            )
        }
        Spacer(Modifier.height(18.dp))

        if (connections.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconTile(icon = IconsL.server, size = 64.dp, tileRadius = 20.dp, iconSize = 26.dp, background = c.accentTint)
                Spacer(Modifier.height(14.dp))
                Text("No connections yet", style = t.cardTitle, color = c.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text("Add an API endpoint to start chatting.", style = t.caption, color = c.textSecondary)
                Spacer(Modifier.height(16.dp))
                com.mrrob.llmchat.ui.kit.AsterButton(text = "Add Connection", modifier = Modifier.padding(horizontal = 40.dp), onClick = onAdd)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                connections.forEach { conn ->
                    ConnectionCard(
                        connection = conn,
                        online = online,
                        onMenu = { menuFor = conn },
                        onEdit = { onEdit(conn) },
                        onTest = { app.testExistingConnection(conn) }
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            InfoStrip(
                text = "Connections work with any OpenAI-compatible /chat/completions endpoint.",
                icon = IconsL.info,
                background = c.fill,
                tint = c.textMuted
            )
        }
        Spacer(Modifier.height(24.dp))
    }

    menuFor?.let { conn ->
        Box(modifier = Modifier) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menuFor = null },
                containerColor = c.card,
                shape = RoundedCornerShape(16.dp)
            ) {
                if (!conn.isDefault) {
                    DropdownMenuItem(
                        text = { Text("Set as default", color = c.textPrimary) },
                        onClick = {
                            app.setDefaultConnection(conn.id)
                            menuFor = null
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(if (conn.enabled) "Disable" else "Enable", color = c.textPrimary) },
                    onClick = {
                        app.toggleConnectionEnabled(conn, !conn.enabled)
                        menuFor = null
                    }
                )
                DropdownMenuItem(
                    text = { Text("Duplicate", color = c.textPrimary) },
                    onClick = {
                        app.duplicateConnection(conn)
                        menuFor = null
                    }
                )
                DropdownMenuItem(
                    text = { Text("Rename", color = c.textPrimary) },
                    onClick = {
                        renameTarget = conn
                        menuFor = null
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = c.danger) },
                    onClick = {
                        deleteTarget = conn
                        menuFor = null
                    }
                )
            }
        }
    }

    renameTarget?.let { conn ->
        var text by remember { mutableStateOf(conn.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = c.card,
            title = { Text("Rename connection", style = t.cardTitle, color = c.textPrimary) },
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
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) app.renameConnection(conn, text.trim())
                    renameTarget = null
                }) { Text("Save", color = c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = c.textSecondary) }
            }
        )
    }

    deleteTarget?.let { conn ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = c.card,
            title = { Text("Delete this connection?", style = t.cardTitle, color = c.textPrimary) },
            text = { Text("Chats made with it stay, but new requests need a connection.", style = t.desc, color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    app.deleteConnection(conn)
                    deleteTarget = null
                }) { Text("Delete", color = c.danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Cancel", color = c.textSecondary) }
            }
        )
    }
}

@Composable
private fun ConnectionCard(
    connection: ConnectionEntity,
    online: Boolean,
    onMenu: () -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    val connected = connection.status == "CONNECTED" && online
    val offline = connection.status == "OFFLINE" || !online

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (offline) 0.82f else 1f)
            .clip(RoundedCornerShape(22.dp))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            IconTile(
                icon = providerIcon(connection.provider),
                size = 44.dp,
                tileRadius = 15.dp,
                iconSize = 20.dp,
                background = c.fill
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(connection.name, style = t.cardTitle, color = c.textPrimary)
                    if (connection.isDefault) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.accentTint)
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                "DEFAULT",
                                style = t.tiny.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                ),
                                color = c.accent
                            )
                        }
                    }
                }
                Text(
                    connection.activeModel.ifBlank { "No model" },
                    style = t.desc.copy(fontWeight = FontWeight.Medium),
                    color = c.textSecondary
                )
                Text(connection.baseUrl, style = t.caption, color = c.textMuted, maxLines = 1)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (connected) c.success else c.warning)
            )
            Text(
                if (connected) "Connected" else if (!online) "Offline" else "Not tested",
                style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                color = if (connected) c.success else c.warning
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.border))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    "Edit",
                    style = t.desc.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textSecondary,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEdit
                    )
                )
                Text(
                    "Test",
                    style = t.desc.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textSecondary,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onTest
                    )
                )
                if (offline) {
                    Text(
                        "Delete",
                        style = t.desc.copy(fontWeight = FontWeight.SemiBold),
                        color = c.danger,
                        modifier = Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onMenu
                        )
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(c.fill)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMenu
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(IconsL.more, "More", tint = c.textSecondary, modifier = Modifier.size(15.dp))
            }
        }
    }
}

fun providerIcon(provider: String): androidx.compose.ui.graphics.vector.ImageVector = when (provider) {
    "OPENAI" -> IconsL.sparkles
    "OPENAI_COMPATIBLE" -> IconsL.shuffle
    "LOCAL_SERVER" -> IconsL.hardDrive
    else -> IconsL.code
}
