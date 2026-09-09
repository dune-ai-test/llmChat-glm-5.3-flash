import io

p = 'app/src/main/java/com/mrrob/llmchat/ui/WizardScreens.kt'
s = io.open(p, encoding='utf-8').read()

def rep(old, new, count=1):
    global s
    assert s.count(old) == count, (old[:60], s.count(old))
    s = s.replace(old, new)

# 0) Safety: if a previous partial run already applied anything, this file was NOT
#    written (heredoc failed), so we expect originals.
assert 'app.wizardState { state -> state.copy(baseUrl = it) }' in s

# 1) URL field -> wizardUrlChanged
rep('''            WizardField(label = "API Base URL", value = w.baseUrl, placeholder = "https://api.openai.com", keyboard = KeyboardType.Uri) {
                app.wizardState { state -> state.copy(baseUrl = it) }
            }''',
'''            WizardField(label = "API Base URL", value = w.baseUrl, placeholder = "https://api.openai.com", keyboard = KeyboardType.Uri) {
                app.wizardUrlChanged(it)
            }''')

# 2) API key field: never display saved key, no reveal toggle
old_start = '            WizardField(\n                label = "API Key",'
i = s.find(old_start)
assert i >= 0
end_marker = '\n                app.wizardState { state -> state.copy(apiKey = it) }\n            }\n'
e = s.find(end_marker, i)
assert e > i
new_key = '''            WizardField(
                label = "API Key",
                value = w.apiKey,
                placeholder = if (w.hasExistingKey)
                    "A saved key is in use - type a new one to replace it"
                else
                    "API key (optional)",
                keyboard = KeyboardType.Password,
                visual = PasswordVisualTransformation(),
                trailing = {
                    if (w.hasExistingKey) {
                        Icon(IconsL.lock, "Saved securely on this device", tint = c.success, modifier = Modifier.size(15.dp))
                    }
                }
            ) {
                app.wizardState { state -> state.copy(apiKey = it) }
            }
'''
s = s[:i] + new_key + s[e + len(end_marker):]

# 3) Replace the entire Models block with cache-aware list + Add model button + picker
ms = s.find('            // Models')
me = s.find('''            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(IconsL.lock''')
assert ms >= 0 and me > ms
models_block = '''            // Models - cached per URL, searchable picker, or fetch live
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Models", style = t.desc.copy(fontWeight = FontWeight.Medium), color = c.textSecondary)
                    Text(
                        "Add model",
                        style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = c.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showModelPicker = true }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                val shown = (w.fetchedModels + w.activeModel.takeIf { it.isNotBlank() && it !in w.fetchedModels }.orEmpty()).distinct()
                if (shown.isEmpty()) {
                    val modelsErr = w.modelsError
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.card)
                            .border(1.dp, c.border, RoundedCornerShape(14.dp))
                    ) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                            Text("No models yet - fetch them or add one.", style = t.rowTitle, color = c.textPrimary)
                            if (modelsErr != null) {
                                Text(modelsErr, style = t.tiny, color = c.danger)
                            }
                        }
                    }
                }
                shown.forEach { model ->
                    val active = model == w.activeModel
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.card)
                            .border(1.dp, if (active) c.accent else c.border, RoundedCornerShape(14.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                app.wizardState { state ->
                                    state.copy(
                                        activeModel = model,
                                        selectedModels = (state.selectedModels + model).distinct()
                                    )
                                }
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(model, style = t.rowTitle, color = c.textPrimary, modifier = Modifier.weight(1f), maxLines = 1)
                        if (active) Icon(IconsL.check, "Selected", tint = c.accent, modifier = Modifier.size(16.dp))
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.fill)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = !w.fetchingModels
                        ) { app.wizardFetchModels() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (w.fetchingModels) {
                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = c.accent)
                        } else {
                            Icon(IconsL.download, null, tint = c.accent, modifier = Modifier.size(16.dp))
                        }
                        Text(if (w.fetchingModels) "Fetching..." else "Fetch models", style = t.desc.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold), color = c.accent)
                    }
                }
            }

            if (showModelPicker) {
                ModelPickerDialog(
                    fetched = w.fetchedModels,
                    onDismiss = { showModelPicker = false },
                    onPick = { name ->
                        app.wizardAddModel(name)
                        showModelPicker = false
                    }
                )
            }

'''
s = s[:ms] + models_block + s[me:]

# 4) state var: replace revealKey with showModelPicker in WizardConfig
rep('''    var revealKey by remember { mutableStateOf(false) }''',
    '''    var showModelPicker by remember { mutableStateOf(false) }''')

# 5) test-screen key line
rep('            ReadLine("API Key", maskKeyForDisplay(w.apiKey))',
'''            ReadLine(
                "API Key",
                when {
                    w.apiKey.isNotBlank() -> maskKeyForDisplay(w.apiKey)
                    w.hasExistingKey -> "\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022  (saved)"
                    else -> "None"
                }
            )''')

# 6) Append ModelPickerDialog before WizardChrome
dialog = '''
/** Search the fetched list or type a model id; tapping a result makes it active. */
@Composable
private fun ModelPickerDialog(
    fetched: List<String>,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    var query by remember { mutableStateOf("") }
    var typed by remember { mutableStateOf("") }
    val filtered = remember(fetched, query) {
        if (query.isBlank()) fetched
        else fetched.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = RoundedCornerShape(18.dp),
        title = { Text("Add model", style = t.cardTitle, color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.bg)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = t.rowTitle.copy(color = c.textPrimary),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterStart),
                        decorationBox = { inner ->
                            Box {
                                if (query.isEmpty()) Text("Search fetched models...", style = t.rowTitle, color = c.textMuted)
                                inner()
                            }
                        }
                    )
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.bg)
                        .border(1.dp, c.border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        singleLine = true,
                        textStyle = t.rowTitle.copy(color = c.textPrimary),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        decorationBox = { inner ->
                            Box {
                                if (typed.isEmpty()) Text("Or type a model id...", style = t.rowTitle, color = c.textMuted)
                                inner()
                            }
                        }
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (filtered.isEmpty() && query.isNotBlank()) {
                        Text("No fetched model matches.", style = t.caption, color = c.textMuted, modifier = Modifier.padding(vertical = 8.dp))
                    }
                    filtered.forEach { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { onPick(model) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(IconsL.sparkles, null, tint = c.accent, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(model, style = t.rowTitle, color = c.textPrimary, maxLines = 1)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (typed.isNotBlank()) onPick(typed) }
            ) { Text("Use typed", color = if (typed.isNotBlank()) c.accent else c.textMuted, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = c.textSecondary) }
        }
    )
}

@Composable
private fun WizardChrome('''
rep('\n@Composable\nprivate fun WizardChrome(', dialog.rstrip('\n') + '\n')

# imports: verticalScroll + rememberScrollState used inside dialog; check present
for imp in ['import androidx.compose.foundation.verticalScroll',
            'import androidx.compose.foundation.rememberScrollState',
            'import androidx.compose.foundation.layout.width',
            'import androidx.compose.ui.graphics.SolidColor',
            'import androidx.compose.material3.AlertDialog',
            'import androidx.compose.material3.TextButton']:
    if imp not in s:
        s = imp + '\n' + s
        print('added', imp)

io.open(p, 'w', encoding='utf-8').write(s)
print('wizard ok')
