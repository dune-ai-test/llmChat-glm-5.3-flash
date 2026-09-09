import io

p = 'app/src/main/java/com/mrrob/llmchat/ui/WizardScreens.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')

addition = '''
@Composable
private fun WizardChrome(
    step: Int,
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                IconsL.arrowBack, "Back", tint = c.textPrimary,
                modifier = Modifier
                    .size(21.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onBack
                    )
            )
            Text("Step $step of 3", style = t.desc.copy(fontWeight = FontWeight.SemiBold), color = c.textSecondary)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(c.fill)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(step / 3f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.accent)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = t.pageTitle, color = c.textPrimary)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = t.caption.copy(fontSize = 14.sp, lineHeight = 20.sp), color = c.textSecondary)
            }
        }
    }
}

@Composable
private fun WizardField(
    label: String,
    value: String,
    placeholder: String,
    keyboard: KeyboardType = KeyboardType.Text,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
    onValueChange: (String) -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = t.desc.copy(fontWeight = FontWeight.Medium), color = c.textSecondary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(c.card)
                .border(1.dp, c.border, RoundedCornerShape(14.dp))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = t.rowTitle.copy(fontSize = 15.sp, color = c.textPrimary),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
                visualTransformation = visual,
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(placeholder, style = t.rowTitle.copy(fontSize = 15.sp), color = c.textMuted, maxLines = 1)
                        }
                        inner()
                    }
                }
            )
            trailing?.invoke()
        }
    }
}

@Composable
fun WizardTest(
    app: AppViewModel,
    onDone: () -> Unit,
    onEditBack: () -> Unit,
    onCancel: () -> Unit
) {
    val w by app.wizard.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val c = LocalScheme.current
    val t = LocalType.current
    val testErr = w.testError

    LaunchedEffect(Unit) {
        if (!w.testRunning && !w.saved && w.steps.isEmpty() && w.testError == null) {
            app.wizardRunTest()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        WizardChrome(step = 2, title = "Connection details", subtitle = "", onBack = onCancel)
        Spacer(Modifier.height(22.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ReadLine("API Base URL", w.baseUrl)
            ReadLine(
                "API Key",
                when {
                    w.apiKey.isNotBlank() -> maskKeyForDisplay(w.apiKey)
                    w.hasExistingKey -> "\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022\\u2022  (saved)"
                    else -> "None"
                }
            )
            ReadLine("Model", w.activeModel.ifBlank { w.selectedModels.firstOrNull().orEmpty() })

            if (w.testError == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(c.card)
                        .border(1.dp, c.border, RoundedCornerShape(18.dp))
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TestRow(TestStep.CONNECT, "Connecting...", "GET /models", w.steps[TestStep.CONNECT])
                    TestRow(TestStep.AUTHENTICATE, "Handshake complete", "Authentication accepted", w.steps[TestStep.AUTHENTICATE])
                    TestRow(TestStep.CHECK_MODEL, "Warming model context", "Model responded", w.steps[TestStep.CHECK_MODEL])
                }
            }

            val succeeded = !w.testRunning && w.testError == null && w.steps.isNotEmpty()
            if (succeeded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.successTint)
                        .padding(horizontal = 20.dp, vertical = 26.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(c.success),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(IconsL.check, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(26.dp))
                    }
                    Text("Connection successful", style = t.cardTitle, color = c.textPrimary)
                    Text("Model responded in ${w.latencyMs} ms", style = t.desc.copy(fontSize = 13.5.sp), color = c.textSecondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetaCard("Latency", "${w.latencyMs} ms", Modifier.weight(1f))
                    MetaCard("Streaming", "Enabled", Modifier.weight(1f))
                    MetaCard("Models", "${w.fetchedModels.size}", Modifier.weight(1f))
                }
            }

            if (testErr != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(c.dangerTint)
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(c.danger),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(IconsL.close, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Couldn't connect", style = t.cardTitle.copy(fontSize = 17.sp), color = c.textPrimary)
                        Text(
                            "Check your API URL and API key, then try again.",
                            style = t.desc.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                            color = c.textSecondary
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.danger.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(testErr.message, style = t.caption.copy(fontWeight = FontWeight.Medium), color = c.danger)
                    }
                }
                AsterButton(text = "Try Again", onClick = { app.wizardState { it.copy(testError = null, steps = emptyMap()) }; app.wizardRunTest() })
                AsterButton(text = "Edit Connection", variant = ButtonVariant.SECONDARY, onClick = onEditBack)
            }
        }

        Spacer(Modifier.height(18.dp))
        when {
            w.testRunning -> AsterButton(text = "Cancel", variant = ButtonVariant.SECONDARY, onClick = onCancel)
            testErr != null -> {}
            else -> AsterButton(
                text = "Continue",
                onClick = {
                    scope.launch {
                        app.wizardSaveAndFinish()
                        onDone()
                    }
                }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
'''

# append at end
s = s.rstrip() + "\n" + addition

# ensure imports present
need = [
    'import androidx.compose.runtime.rememberCoroutineScope',
    'import kotlinx.coroutines.launch',
]
for imp in need:
    if imp not in s:
        s = s.replace('import androidx.lifecycle.compose.collectAsStateWithLifecycle',
                      imp + '\nimport androidx.lifecycle.compose.collectAsStateWithLifecycle', 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('wizard functions restored')
