import io

p = 'app/src/main/java/com/mrrob/llmchat/ui/SettingsScreens.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')

# ------------------------------------------------------------------ SettingsScreen
start = s.find('/** 27 — Settings main screen. */')
end = s.find('private val PaddingValuesZero')
assert start > 0 and end > start, (start, end)

new_settings = '''/**
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
                            "${connections.size} connection${if (connections.size == 1) "" else "s"} \\u00b7 everything stored encrypted on this device",
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

/** Scratch state between the password dialog and the SAF launcher callback. */
private var pendingExportPassword: String? = null

'''
s = s[:start] + new_settings + s[end:]

# ------------------------------------------------------------- Appearance screen
astart = s.find('/** 30 — Appearance */')
if astart < 0:
    astart = s.find('fun AppearanceScreen')
    assert astart > 0
    # back up to the preceding doc comment if any
    doc = s.rfind('/**', 0, astart)
    astart = doc if doc > 0 else astart
aend = s.find('@Composable\nprivate fun ThemeCard(')
assert astart > 0 and aend > astart, (astart, aend)

new_appearance = '''/** 30 — Appearance: theme, colour accent, navigation style, font size, density. */
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

'''
s = s[:astart] + new_appearance + s[aend:]

# required imports
for imp in [
    'import androidx.compose.ui.graphics.Brush',
    'import androidx.compose.runtime.LaunchedEffect',
    'import kotlinx.coroutines.delay',
    'import androidx.compose.ui.text.input.PasswordVisualTransformation',
]:
    if imp not in s:
        s = imp + '\n' + s

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('settings redesigned')
