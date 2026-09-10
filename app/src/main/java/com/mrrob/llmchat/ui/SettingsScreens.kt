package com.mrrob.llmchat.ui
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Brush

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrrob.llmchat.AppViewModel
import com.mrrob.llmchat.data.AppSettings
import com.mrrob.llmchat.ui.kit.AsterBackHeader
import com.mrrob.llmchat.ui.kit.AsterCard
import com.mrrob.llmchat.ui.kit.AsterRow
import com.mrrob.llmchat.ui.kit.AsterSegmented
import com.mrrob.llmchat.ui.kit.AsterSliderRow
import com.mrrob.llmchat.ui.kit.AsterSwitch
import com.mrrob.llmchat.ui.kit.CardDivider
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.kit.InfoStrip
import com.mrrob.llmchat.ui.kit.SectionLabel
import com.mrrob.llmchat.ui.kit.ScreenTopBar
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 27 — Settings home, redesigned: profile hero, quick theme chips, and
 * grouped sections (Chat / Voice / Appearance / Advanced / Data / About).
 * Backup is encrypted with a password via the Aster vault format.
 */
@Composable
fun SettingsScreen(app: AppViewModel, onNavigate: (String) -> Unit) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val connections by app.connections.collectAsStateWithLifecycle()
    val dataOp by app.dataOp.collectAsStateWithLifecycle()
    val debugLog = app.container.debugLog
    val c = LocalScheme.current
    val t = LocalType.current
    val context = LocalContext.current
    var privacyDialog by remember { mutableStateOf(false) }
    var profileDialog by remember { mutableStateOf(false) }
    var resetConfirm by remember { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showExportPassword by remember { mutableStateOf(false) }
    var showImportPassword by remember { mutableStateOf(false) }
    var importReason by remember { mutableStateOf<String?>(null) }
    var resultMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(dataOp) {
        when (val op = dataOp) {
            is AppViewModel.DataOp.NeedPassword -> {
                importReason = op.reason
                showImportPassword = true
            }
            is AppViewModel.DataOp.Done -> {
                resultMessage = op.message
                app.clearDataOp()
            }
            else -> Unit
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pwd = pendingExportPassword
        pendingExportPassword = ""
        if (uri != null && pwd != null) app.exportBackup(uri, pwd)
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            app.pendingImportUri = uri
            app.importBackup(uri, "")
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(c.bg)) {
        ScreenTopBar(title = "Settings")
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Profile hero
            AsterCard(
                onClick = { profileDialog = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                androidx.compose.ui.graphics.Brush.linearGradient(
                                    listOf(c.accent, c.accentDeep)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            settings.displayName.take(1).uppercase(),
                            style = t.cardTitle.copy(fontSize = 21.sp, fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }
                    Spacer(Modifier.width(13.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(settings.displayName.ifBlank { "Your workspace" }, style = t.cardTitle, color = c.textPrimary)
                        Text(
                            "${connections.size} connection${if (connections.size == 1) "" else "s"} \u00b7 everything stored encrypted on this device",
                            style = t.desc.copy(fontSize = 12.5.sp),
                            color = c.textSecondary,
                            maxLines = 1
                        )
                    }
                    Icon(IconsL.chevronRight, null, tint = c.textMuted, modifier = Modifier.size(17.dp))
                }
            }

            // Quick theme switch
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                    com.mrrob.llmchat.ui.kit.AsterChip(
                        text = label,
                        selected = settings.themeMode == key,
                        modifier = Modifier.weight(1f),
                        onClick = { app.updateSettings { x -> x.copy(themeMode = key) } }
                    )
                }
            }

            resultMessage?.let { msg ->
                InfoStrip(text = msg, icon = IconsL.info, background = c.fill, tint = c.textSecondary)
                LaunchedEffect(msg) { delay(6000); resultMessage = null }
            }

            SectionLabel("Chat")
            AsterCard(contentPadding = PaddingValuesZero) {
                AsterRow(label = "Streaming", icon = IconsL.chatSquare, value = if (settings.streaming) "On" else "Off", showChevron = true, onClick = { onNavigate("settings/chat") })
                CardDivider()
                AsterRow(
                    label = "System prompt",
                    icon = IconsL.code,
                    value = if (settings.promptPreset != "custom") settings.promptPreset.replaceFirstChar { it.uppercase() }
                    else settings.systemPrompt.split(" ").take(2).joinToString(" "),
                    showChevron = true,
                    onClick = { onNavigate("settings/chat") }
                )
                CardDivider()
                AsterRow(label = "Temperature", icon = IconsL.thermometer, value = formatTemp(settings.temperature), showChevron = true, onClick = { onNavigate("settings/chat") })
            }

            SectionLabel("Voice")
            AsterCard(contentPadding = PaddingValuesZero) {
                AsterRow(
                    label = "Speech-to-text",
                    icon = IconsL.audioLines,
                    value = "On-device",
                    showChevron = true,
                    onClick = { onNavigate("settings/voice") }
                )
                CardDivider()
                AsterRow(
                    label = "Text-to-speech",
                    icon = IconsL.volume,
                    trailing = { AsterSwitch(checked = settings.voiceTts) { app.updateSettings { x -> x.copy(voiceTts = it) } } }
                )
                CardDivider()
                AsterRow(label = "Voice", icon = IconsL.mic, value = settings.voiceName, showChevron = true, onClick = { onNavigate("settings/voice") })
                CardDivider()
                AsterRow(
                    label = "Auto-play responses",
                    icon = IconsL.play,
                    trailing = { AsterSwitch(checked = settings.voiceAutoPlay) { app.updateSettings { x -> x.copy(voiceAutoPlay = it) } } }
                )
            }

            SectionLabel("Appearance")
            AsterCard(contentPadding = PaddingValuesZero) {
                AsterRow(
                    label = "Color theme",
                    icon = IconsL.sparkles,
                    value = when (settings.accentTheme) {
                        "emerald" -> "Emerald"
                        "sunset" -> "Sunset"
                        else -> "Indigo"
                    },
                    showChevron = true,
                    onClick = { onNavigate("settings/appearance") }
                )
                CardDivider()
                AsterRow(label = "Font size", icon = IconsL.type, value = settings.fontScale.replaceFirstChar { it.uppercase() }, showChevron = true, onClick = { onNavigate("settings/appearance") })
                CardDivider()
                AsterRow(label = "Chat density", icon = IconsL.list, value = settings.chatDensity.replaceFirstChar { it.uppercase() }, showChevron = true, onClick = { onNavigate("settings/appearance") })
                CardDivider()
                AsterRow(
                    label = "Navigation",
                    icon = IconsL.list,
                    value = if (settings.navMode == "side") "Sidebar" else "Bottom bar",
                    showChevron = true,
                    onClick = { onNavigate("settings/appearance") }
                )
            }

            SectionLabel("Advanced")
            AsterCard(contentPadding = PaddingValuesZero) {
                AsterRow(
                    label = "Custom headers",
                    icon = IconsL.braces,
                    value = defaultHeaderCount(app).toString(),
                    showChevron = true,
                    onClick = { onNavigate("settings/advanced") }
                )
                CardDivider()
                AsterRow(label = "Request timeout", icon = IconsL.timer, value = "${settings.requestTimeoutSec}s", showChevron = true, onClick = { onNavigate("settings/advanced") })
                CardDivider()
                AsterRow(label = "Custom endpoint", icon = IconsL.globe, value = settings.endpointPath, showChevron = true, onClick = { onNavigate("settings/advanced") })
                CardDivider()
                AsterRow(
                    label = "Debug logging",
                    icon = IconsL.bug,
                    trailing = {
                        AsterSwitch(checked = settings.debugLogging) { enabled ->
                            debugLog.enabled = enabled
                            app.updateSettings { x -> x.copy(debugLogging = enabled) }
                        }
                    }
                )
                if (settings.debugLogging) {
                    CardDivider()
                    AsterRow(label = "View request log", showChevron = true, onClick = { showLog = true })
                }
            }

            SectionLabel("Data")
            AsterCard(contentPadding = PaddingValuesZero) {
                AsterRow(
                    label = "Export encrypted backup",
                    icon = IconsL.download,
                    description = "Chats, connections and keys - locked with a password.",
                    onClick = { showExportPassword = true }
                )
                CardDivider()
                AsterRow(label = "Import backup", icon = IconsL.arrowSwap, onClick = { importLauncher.launch(arrayOf("*/*")) })
                CardDivider()
                AsterRow(label = "Reset settings", onClick = { resetConfirm = true })
                CardDivider()
                AsterRow(label = "Delete all data", destructive = true, onClick = { deleteConfirm = true })
            }

            SectionLabel("About")
            AsterCard(contentPadding = PaddingValuesZero) {
                AsterRow(label = "Version", icon = IconsL.info, value = "1.2.0", onClick = { privacyDialog = true })
                CardDivider()
                AsterRow(label = "Privacy", icon = IconsL.block, onClick = { privacyDialog = true })
            }

            Spacer(Modifier.height(28.dp))
        }
    }

    if (privacyDialog) {
        AlertDialog(
            onDismissRequest = { privacyDialog = false },
            containerColor = c.card,
            title = { Text("Privacy", style = t.cardTitle, color = c.textPrimary) },
            text = {
                Text(
                    "Your conversations are sent to the API provider you configure. " +
                        "Aster has no backend of its own - chats, settings and keys stay on this device. " +
                        "API keys are stored encrypted; backups are additionally locked with your password.",
                    style = t.desc,
                    color = c.textSecondary
                )
            },
            confirmButton = { TextButton(onClick = { privacyDialog = false }) { Text("Done", color = c.accent) } }
        )
    }

    if (profileDialog) {
        EditTextDialog(
            title = "Display name",
            value = settings.displayName,
            multiline = false,
            onDismiss = { profileDialog = false },
            onSave = { v ->
                app.updateSettings { x -> x.copy(displayName = v.ifBlank { "Your workspace" }) }
                profileDialog = false
            }
        )
    }

    if (showExportPassword) {
        PasswordDialog(
            title = "Choose a backup password",
            reason = "The file is encrypted before it leaves the app. A forgotten password cannot be recovered.",
            confirmLabel = "Export",
            onDismiss = { showExportPassword = false },
            onConfirm = { pwd ->
                pendingExportPassword = pwd
                showExportPassword = false
                exportLauncher.launch("aster-backup.json")
            }
        )
    }

    importReason?.let { reason ->
        if (showImportPassword) {
            PasswordDialog(
                title = "Backup is locked",
                reason = reason,
                confirmLabel = "Unlock",
                onDismiss = {
                    showImportPassword = false
                    importReason = null
                    app.pendingImportUri?.let { app.clearDataOp() }
                    app.pendingImportUri = null
                },
                onConfirm = { pwd ->
                    val uri = app.pendingImportUri
                    showImportPassword = false
                    if (uri != null) app.importBackup(uri, pwd)
                }
            )
        }
    }

    if (resetConfirm) {
        AlertDialog(
            onDismissRequest = { resetConfirm = false },
            containerColor = c.card,
            title = { Text("Reset app settings?", style = t.cardTitle, color = c.textPrimary) },
            text = { Text("Preferences return to defaults. Conversations are kept.", style = t.desc, color = c.textSecondary) },
            confirmButton = { TextButton(onClick = { app.resetSettings(); resetConfirm = false }) { Text("Reset", color = c.accent) } },
            dismissButton = { TextButton(onClick = { resetConfirm = false }) { Text("Cancel", color = c.textSecondary) } }
        )
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            containerColor = c.card,
            title = { Text("Delete all data?", style = t.cardTitle, color = c.textPrimary) },
            text = { Text("This removes every conversation, connection and key. This cannot be undone.", style = t.desc, color = c.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    app.clearAllData()
                    deleteConfirm = false
                    app.selectTab(com.mrrob.llmchat.AsterTab.HOME)
                }) { Text("Delete everything", color = c.danger, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("Cancel", color = c.textSecondary) } }
        )
    }

    if (showLog) {
        EditTextDialog(
            title = "Request log",
            value = debugLog.copyText().ifBlank { "No requests logged yet." },
            multiline = true,
            monospace = true,
            onDismiss = { showLog = false },
            onSave = { debugLog.clear() }
        )
    }
}

/** Backup password entry (typed twice for export, once for import). */
@Composable
private fun PasswordDialog(
    title: String,
    reason: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    var pwd by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }
    val valid = pwd.length >= 4 && (confirmLabel != "Export" || pwd == pwd2)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title, style = t.cardTitle, color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(reason, style = t.desc, color = c.textSecondary)
                LockedField("Password", pwd) { pwd = it }
                if (confirmLabel == "Export") {
                    LockedField("Confirm password", pwd2) { pwd2 = it }
                    if (pwd2.isNotEmpty() && pwd != pwd2) {
                        Text("Passwords do not match.", style = t.caption, color = c.danger)
                    }
                }
                if (pwd.isNotEmpty() && pwd.length < 4) {
                    Text("At least 4 characters.", style = t.caption, color = c.warning)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(pwd) }) {
                Text(confirmLabel, color = if (valid) c.accent else c.textMuted, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } }
    )
}

@Composable
private fun LockedField(placeholder: String, value: String, onValueChange: (String) -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.bg)
            .border(1.dp, c.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp)
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            textStyle = t.rowTitle.copy(color = c.textPrimary),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = t.rowTitle, color = c.textMuted)
                    inner()
                }
            }
        )
    }
}

/** 30 — Appearance: theme, colour accent, navigation style, font size, density. */
@Composable
fun AppearanceScreen(app: AppViewModel, onBack: () -> Unit) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    val t = LocalType.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
    ) {
        AsterBackHeader(title = "Appearance", onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Theme", style = t.cardTitle, color = c.textPrimary)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("light", "dark", "system").forEach { mode ->
                    ThemeCard(
                        mode = mode,
                        selected = settings.themeMode == mode,
                        modifier = Modifier.weight(1f),
                        onClick = { app.updateSettings { x -> x.copy(themeMode = mode) } }
                    )
                }
            }

            SectionLabel("Color theme")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val accents = listOf(
                    "indigo" to ("Indigo" to Color(0xFF5B5BD6)),
                    "emerald" to ("Emerald" to Color(0xFF0F9D6B)),
                    "sunset" to ("Sunset" to Color(0xFFE0653A))
                )
                accents.forEach { (key, pair) ->
                    val selected = settings.accentTheme == key
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) c.accentTint else c.card)
                            .border(
                                if (selected) 1.5.dp else 1.dp,
                                if (selected) c.accent else c.border,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { app.updateSettings { x -> x.copy(accentTheme = key) } }
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(pair.second)
                        )
                        Text(
                            pair.first,
                            style = t.desc.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                            color = c.textPrimary
                        )
                    }
                }
            }

            SectionLabel("Navigation")
            AsterCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 13.dp)) {
                AsterRow(label = "Show tabs as")
                AsterSegmented(
                    options = listOf("Bottom bar", "Sidebar"),
                    selectedIndex = if (settings.navMode == "side") 1 else 0,
                    onSelect = { idx ->
                        app.updateSettings { x -> x.copy(navMode = if (idx == 1) "side" else "bottom") }
                    }
                )
            }

            SectionLabel("Text")
            AsterCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 13.dp)) {
                AsterRow(label = "Font size")
                AsterSegmented(
                    options = listOf("Small", "Medium", "Large"),
                    selectedIndex = listOf("small", "medium", "large").indexOf(settings.fontScale).coerceAtLeast(0),
                    optionStyle = { i -> t.desc.copy(fontSize = when (i) { 0 -> 12.sp; 1 -> 14.sp; else -> 16.sp }) },
                    onSelect = { idx ->
                        app.updateSettings { x -> x.copy(fontScale = listOf("small", "medium", "large")[idx]) }
                    }
                )
            }
            AsterCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 13.dp)) {
                AsterRow(label = "Chat density")
                AsterSegmented(
                    options = listOf("Comfortable", "Compact"),
                    selectedIndex = if (settings.chatDensity == "compact") 1 else 0,
                    onSelect = { idx ->
                        app.updateSettings { x -> x.copy(chatDensity = if (idx == 1) "compact" else "comfortable") }
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThemeCard(mode: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) c.accentTint else c.card)
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) c.accent else c.border,
                RoundedCornerShape(18.dp)
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val previewDark = mode == "dark" || (mode == "system" && c.dark)
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (previewDark) Color(0xFF1A1A1E) else Color(0xFFF2F2EF))
                .border(1.dp, c.border, RoundedCornerShape(11.dp))
                .padding(9.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.width(34.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(if (previewDark) Color(0xFF3A3A42) else Color(0xFFD8D8D2)))
                Box(Modifier.width(44.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(c.accent))
                Box(Modifier.width(26.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(if (previewDark) Color(0xFF3A3A42) else Color(0xFFD8D8D2)))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(mode.replaceFirstChar { it.uppercase() }, style = t.desc.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold), color = c.textPrimary, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .border(if (selected) 1.5.dp else 1.5.dp, if (selected) c.accent else c.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) Box(Modifier.size(9.dp).clip(CircleShape).background(c.accent))
            }
        }
    }
}

// ── 31 Advanced API ───────────────────────────────────────────────────────────

@Composable
fun AdvancedApiScreen(app: AppViewModel, onBack: () -> Unit) {
    val settings by app.settings.collectAsStateWithLifecycle()
    val connections by app.connections.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    val t = LocalType.current
    val scope = rememberCoroutineScope()
    val defaultConn = remember(connections) { connections.firstOrNull { it.isDefault } }

    var headers by remember(defaultConn) {
        mutableStateOf<List<Pair<String, String>>>(
            defaultConn?.let { conn ->
                app.repo.customHeadersOf(conn).entries.map { it.key to it.value }
            } ?: emptyList()
        )
    }
    var headerDialog by remember { mutableStateOf(false) }
    var timeoutDialog by remember { mutableStateOf(false) }
    var endpointDialog by remember { mutableStateOf(false) }
    var rawDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        AsterBackHeader(title = "Advanced", onBack = onBack)
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionLabel("Custom headers")
            AsterCard(contentPadding = PaddingValuesZero) {
                headers.forEachIndexed { idx, (key, value) ->
                    if (idx > 0) CardDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(key, style = t.rowTitle.copy(fontFamily = com.mrrob.llmchat.ui.theme.MonoFamily), modifier = Modifier.weight(1f))
                        Text(
                            if (key.equals("Authorization", true)) "Bearer ••••••" else value,
                            style = t.desc.copy(fontFamily = com.mrrob.llmchat.ui.theme.MonoFamily),
                            color = c.textSecondary,
                            maxLines = 1
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            IconsL.trash, "Remove", tint = c.textMuted,
                            modifier = Modifier
                                .size(15.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    headers = headers.filterIndexed { i, _ -> i != idx }
                                    persistHeaders(app, defaultConn?.id, headers)
                                }
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = defaultConn != null
                        ) { headerDialog = true }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(IconsL.plus, null, tint = c.accent, modifier = Modifier.size(14.dp))
                    Text("Add header", style = t.desc.copy(fontWeight = FontWeight.SemiBold), color = c.accent)
                }
            }

            SectionLabel("Request")
            AsterCard(contentPadding = PaddingValuesZero) {
                AsterRow(label = "Request timeout", value = "${settings.requestTimeoutSec}s", onClick = { timeoutDialog = true })
                CardDivider()
                AsterRow(label = "Endpoint path", value = settings.endpointPath, onClick = { endpointDialog = true })
                CardDivider()
                AsterRow(
                    label = "API version",
                    value = settings.apiVersion.ifBlank { "default" },
                    onClick = { /* editing via raw params endpoint */ }
                )
            }

            SectionLabel("Raw parameters")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.codeBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { rawDialog = true }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("additional_params.json", style = t.monoSmall, color = c.textMuted, modifier = Modifier.weight(1f))
                    Icon(IconsL.copy, "Copy", tint = c.textMuted, modifier = Modifier.size(14.dp))
                }
                Column(modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)) {
                    (settings.rawParams.ifBlank { "{}" }.lines().ifEmpty { listOf("{}") }).forEach { line ->
                        val color = when {
                            line.contains("\"") && line.contains(":") -> c.success
                            line.trim() == "{" || line.trim() == "}" -> c.codeFg
                            else -> c.codeFg
                        }
                        Text(line.ifBlank { " " }, style = t.mono, color = color)
                    }
                }
            }

            SectionLabel("Diagnostics")
            AsterCard(contentPadding = PaddingValuesZero) {
                val debugLog = app.container.debugLog
                AsterRow(
                    label = "Debug logging",
                    description = "Store request/response bodies locally for troubleshooting.",
                    trailing = {
                        AsterSwitch(checked = settings.debugLogging) { on ->
                            debugLog.enabled = on
                            app.updateSettings { s -> s.copy(debugLogging = on) }
                        }
                    }
                )
            }

            InfoStrip(
                text = "Incorrect raw parameters may cause request failures.",
                icon = IconsL.warning,
                background = c.warningTint,
                tint = c.warning
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (headerDialog) {
        AddHeaderDialog(onDismiss = { headerDialog = false }) { key, value ->
            headers = headers + (key to value)
            persistHeaders(app, defaultConn?.id, headers)
            headerDialog = false
        }
    }
    if (timeoutDialog) {
        NumberDialog(
            title = "Request timeout (seconds)",
            value = settings.requestTimeoutSec.toString(),
            onDismiss = { timeoutDialog = false },
            onSave = { v ->
                v.toIntOrNull()?.coerceIn(5, 600)?.let { app.updateSettings { s -> s.copy(requestTimeoutSec = it) } }
                timeoutDialog = false
            }
        )
    }
    if (endpointDialog) {
        EditTextDialog(
            title = "Endpoint path",
            value = settings.endpointPath,
            multiline = false,
            onDismiss = { endpointDialog = false },
            onSave = {
                app.updateSettings { s -> s.copy(endpointPath = it.ifBlank { "/chat/completions" }) }
                endpointDialog = false
            }
        )
    }
    if (rawDialog) {
        EditTextDialog(
            title = "additional_params.json",
            value = settings.rawParams.ifBlank { "{\n  \n}" },
            multiline = true,
            monospace = true,
            onDismiss = { rawDialog = false },
            onSave = {
                app.updateSettings { s -> s.copy(rawParams = validateJsonOrEmpty(it)) }
                rawDialog = false
            }
        )
    }
    scope
}

private fun validateJsonOrEmpty(text: String): String =
    try {
        if (text.isBlank()) "" else { JSONObject(text); text.trim() }
    } catch (_: Exception) {
        ""
    }

private fun persistHeaders(app: AppViewModel, connectionId: String?, headers: List<Pair<String, String>>) {
    val obj = JSONObject()
    headers.forEach { (k, v) -> obj.put(k, v) }
    if (connectionId != null) app.repo.setHeaders(connectionId, obj.toString())
}

@Composable
private fun AddHeaderDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text("Add header", style = t.cardTitle, color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallField("Name", key) { key = it }
                SmallField("Value", value) { value = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (key.isNotBlank()) onAdd(key.trim(), value.trim()) }) {
                Text("Add", color = c.accent, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } }
    )
}

@Composable
private fun SmallField(placeholder: String, value: String, onValueChange: (String) -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
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
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = t.rowTitle.copy(color = c.textPrimary),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterStart),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = t.rowTitle, color = c.textMuted)
                    inner()
                }
            }
        )
    }
}

@Composable
internal fun EditTextDialog(
    title: String,
    value: String,
    multiline: Boolean,
    monospace: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    var text by remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title, style = t.cardTitle, color = c.textPrimary) },
        text = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 300.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(c.bg)
                    .border(1.dp, c.border, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = !multiline,
                    textStyle = (if (monospace) t.mono.copy(fontSize = 13.sp, lineHeight = 18.sp) else t.body).copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Save", color = c.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } }
    )
}

@Composable
internal fun NumberDialog(
    title: String,
    value: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    var text by remember { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        title = { Text(title, style = t.cardTitle, color = c.textPrimary) },
        text = {
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
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = t.rowTitle.copy(color = c.textPrimary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.CenterStart)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) { Text("Apply", color = c.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) } }
    )
}
