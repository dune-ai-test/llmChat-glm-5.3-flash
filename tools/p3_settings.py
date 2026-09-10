import io

# ------------------------------------------------------------ SettingsScreens.kt
p = 'app/src/main/java/com/mrrob/llmchat/ui/SettingsScreens.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')

def rep(o, n, cnt=1):
    global s
    assert s.count(o) == cnt, (o[:60], s.count(o))
    s = s.replace(o, n)

# 1. remove the quick theme chips from Settings main
rep('''            // Quick theme switch
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

''', '')

# 2. new export flow: prepare bytes first, then SAF, then persistable write
rep('''    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val pwd = pendingExportPassword
        pendingExportPassword = ""
        if (uri != null && pwd != null) app.exportBackup(uri, pwd)
    }''',
'''    val exportBytes by app.exportBytes.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) app.writeExportTo(uri)
        app.consumeExportBytes()
    }

    LaunchedEffect(exportBytes) {
        if (exportBytes != null) exportLauncher.launch("aster-backup.json")
    }''')

rep('''            onConfirm = { pwd ->
                pendingExportPassword = pwd
                showExportPassword = false
                exportLauncher.launch("aster-backup.json")
            }''',
'''            onConfirm = { pwd ->
                showExportPassword = false
                app.prepareExport(pwd)
            }''')

rep('''/** Scratch state between the password dialog and the SAF launcher callback. */
private var pendingExportPassword: String? = null

''', '')

# 3. unified subpage headers: ScreenTopBar with leading back button
for title in ["Chat", "Voice", "Appearance", "Advanced"]:
    rep(f'        AsterBackHeader(title = "{title}", onBack = onBack)',
        f'''        ScreenTopBar(
            title = "{title}",
            leading = {{
                CircleIconButton(
                    icon = IconsL.chevronLeft,
                    onClick = onBack,
                    size = 40.dp,
                    iconSize = 20.dp,
                    bordered = false,
                    tint = c.textPrimary
                )
            }}
        )''')

# CircleIconButton import needed? it is used in SettingsScreen? add import if missing
if 'import com.mrrob.llmchat.ui.kit.CircleIconButton' not in s:
    s = s.replace('import com.mrrob.llmchat.ui.kit.CardDivider',
                  'import com.mrrob.llmchat.ui.kit.CardDivider\nimport com.mrrob.llmchat.ui.kit.CircleIconButton', 1)

# 4. appearance root inset (its Column was rewritten without statusBarsPadding)
rep('''    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTopBar(
            title = "Appearance",''',
'''    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTopBar(
            title = "Appearance",''')

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('settings screens ok')

# --------------------------------------------------------------- AppViewModel
p2 = 'app/src/main/java/com/mrrob/llmchat/AppViewModel.kt'
s2 = io.open(p2, encoding='utf-8', newline='').read().replace('\r\n', '\n')

old_export = '''    /** Encrypts the workspace with the vault format and writes it to [uri]. */
    fun exportBackup(uri: android.net.Uri, password: String) {
        viewModelScope.launch {
            _dataOp.value = DataOp.Running("Exporting\\u2026")
            try {
                val json = repo.exportJson()
                val bytes = com.mrrob.llmchat.data.Vault.encrypt(json, password)
                withContext(Dispatchers.IO) {
                    appContext.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("Could not open the file.")
                }
                _dataOp.value = DataOp.Done("Backup exported (${bytes.size / 1024} KB, encrypted).")
            } catch (e: Exception) {
                _dataOp.value = DataOp.Done("Export failed: ${e.message}")
            }
        }
    }'''
assert s2.count(old_export) == 1, 'export fn anchor'
new_export = '''    /** Encrypted backup bytes staged for the SAF document launcher. */
    private val _exportBytes = MutableStateFlow<ByteArray?>(null)
    val exportBytes: StateFlow<ByteArray?> = _exportBytes.asStateFlow()
    fun consumeExportBytes() { _exportBytes.value = null }

    fun prepareExport(password: String) {
        viewModelScope.launch {
            _dataOp.value = DataOp.Running("Encrypting\\u2026")
            try {
                val json = repo.exportJson()
                val bytes = com.mrrob.llmchat.data.Vault.encrypt(json, password)
                _dataOp.value = DataOp.Idle
                _exportBytes.value = bytes
            } catch (e: Exception) {
                _dataOp.value = DataOp.Done("Export failed: ${e.message}")
            }
        }
    }

    /** Writes the staged bytes synchronously while the SAF grant is alive. */
    fun writeExportTo(uri: android.net.Uri) {
        val bytes = _exportBytes.value ?: return
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val result = runCatching {
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: throw IllegalStateException("Could not open the file for writing.")
        }
        _dataOp.value = if (result.isSuccess) {
            DataOp.Done("Backup exported (${bytes.size / 1024} KB, encrypted).")
        } else {
            DataOp.Done("Export failed: ${result.exceptionOrNull()?.message}")
        }
    }'''
s2 = s2.replace(old_export, new_export)

# importBackup: take persistable read permission too + io
s2 = s2.replace('''    fun importBackup(uri: android.net.Uri, password: String) {
        viewModelScope.launch {
            _dataOp.value = DataOp.Running("Importing\\u2026")
            try {
                val bytes = withContext(Dispatchers.IO) {
                    appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Could not read the file.")
                }''',
'''    fun importBackup(uri: android.net.Uri, password: String) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            _dataOp.value = DataOp.Running("Importing\\u2026")
            try {
                val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read the file.")''')

io.open(p2, 'w', encoding='utf-8', newline='\n').write(s2)
print('viewmodel export ok')
