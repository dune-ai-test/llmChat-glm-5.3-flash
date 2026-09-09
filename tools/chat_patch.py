import io

p = 'app/src/main/java/com/mrrob/llmchat/ui/ChatScreen.kt'
s = io.open(p, encoding='utf-8').read()

def rep(old, new, count=1):
    global s
    assert s.count(old) == count, (old[:70], s.count(old))
    s = s.replace(old, new)

# 1. signature: favorites params
rep('''fun ChatScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
    onVoice: () -> Unit,
    onOpenConnections: () -> Unit
) {''',
'''fun ChatScreen(
    vm: ChatViewModel,
    favorites: List<String> = emptyList(),
    onToggleFavorite: (String) -> Unit = {},
    onBack: () -> Unit,
    onVoice: () -> Unit,
    onOpenConnections: () -> Unit
) {''')

# 2. extra state
rep('''    val listState = rememberLazyListState()
    var input by rememberSaveable { mutableStateOf("") }''',
'''    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var detailsFor by remember { mutableStateOf<Long?>(null) }
    val isAtBottom = remember { mutableStateOf(true) }''')

# 3. follow + tracking
rep('''    // Auto-scroll while new content arrives.
    LaunchedEffect(messages.size, streaming?.text?.length, generating) {
        if (settings.autoScroll && (messages.isNotEmpty() || streaming != null)) {
            listState.animateScrollToItem(messages.size)
        }
    }''',
'''    // Track whether the viewport sits at the bottom.
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }.collect { isAtBottom.value = it }
    }

    // Auto-scroll while new content arrives - unless the user scrolled up to read.
    LaunchedEffect(messages.size, streaming?.text?.length, generating) {
        if (settings.autoScroll && isAtBottom.value && (messages.isNotEmpty() || generating)) {
            listState.animateScrollToItem(messages.size.coerceAtLeast(0))
        }
    }''')

# 4. MessageRow call with details params
rep('''                        MessageRow(
                            message = message,
                            isLastAssistant = message.id == lastAssistantId,
                            vm = vm,
                            onLongPress = { menuMessage = message }
                        )''',
'''                        MessageRow(
                            message = message,
                            isLastAssistant = message.id == lastAssistantId,
                            expandedDetails = detailsFor == message.id,
                            onToggleDetails = {
                                detailsFor = if (detailsFor == message.id) null else message.id
                            },
                            vm = vm,
                            onLongPress = { menuMessage = message }
                        )''')

# 5. jump-to-latest pill: insert before the transcript Box's final two closes.
old5 = '''                    streaming?.error?.let { err ->
                        item {
                            ErrorRecoveryCard(
                                kind = err.kind,
                                message = err.message,
                                detail = err.detail,
                                onTryAgain = vm::retryGeneration,
                                onChangeModel = { showModelSheet = true },
                                onCheckKey = onOpenConnections,
                                onDismiss = vm::clearGenerationError
                            )
                        }
                    }
                }
            }
        }

        // ── Composer ──────────────────────────────────────────────────────────'''
new5 = '''                    streaming?.error?.let { err ->
                        item {
                            ErrorRecoveryCard(
                                kind = err.kind,
                                message = err.message,
                                detail = err.detail,
                                onTryAgain = vm::retryGeneration,
                                onChangeModel = { showModelSheet = true },
                                onCheckKey = onOpenConnections,
                                onDismiss = vm::clearGenerationError
                            )
                        }
                    }
                }
            }

            if ((messages.isNotEmpty() || generating) && !isAtBottom.value) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    Row(
                        modifier = Modifier
                            .padding(bottom = 10.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.card)
                            .border(1.dp, c.border, RoundedCornerShape(99.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                scope.launch {
                                    listState.animateScrollToItem(
                                        (messages.size - 1).coerceAtLeast(0)
                                    )
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(IconsL.chevronDown, null, tint = c.textPrimary, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Latest",
                            style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                            color = c.textPrimary
                        )
                    }
                }
            }
        }

        // ── Composer ──────────────────────────────────────────────────────────'''
rep(old5, new5)

# 6. composer onHome param + button
rep('''        Composer(
            value = input,
            onValueChange = { input = it },
            generating = generating,
            placeholder = "Message Aster…",''',
'''        Composer(
            value = input,
            onValueChange = { input = it },
            generating = generating,
            onHome = {
                vm.saveDraft(input)
                onBack()
            },
            placeholder = "Message Aster…",''')

rep('''private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    generating: Boolean,
    placeholder: String,''',
'''private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    generating: Boolean,
    onHome: () -> Unit,
    placeholder: String,''')

rep('''        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = d.composerV),
            verticalAlignment = Alignment.Bottom
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,''',
'''        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = d.composerV),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(c.card)
                    .border(1.dp, c.border, CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onHome
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(IconsL.home, "Home", tint = c.textSecondary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = value,
                onValueChange = onValueChange,''')

# 7. MessageRow signature + details row + remove Good/Bad pills
rep('''private fun MessageRow(
    message: MessageEntity,
    isLastAssistant: Boolean,
    vm: ChatViewModel,
    onLongPress: () -> Unit
) {''',
'''private fun MessageRow(
    message: MessageEntity,
    isLastAssistant: Boolean,
    expandedDetails: Boolean,
    onToggleDetails: () -> Unit,
    vm: ChatViewModel,
    onLongPress: () -> Unit
) {''')

rep('''        "assistant" -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = {}, onLongClick = onLongPress),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarkdownText(
                source = message.text,
                streaming = false,
                showCodeLineNumbers = vm.showCodeLineNumbers()
            )''',
'''        "assistant" -> Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onToggleDetails, onLongClick = onLongPress),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MarkdownText(
                source = message.text,
                streaming = false,
                showCodeLineNumbers = vm.showCodeLineNumbers()
            )
            if (expandedDetails) {
                val parts = buildList {
                    if (message.tokensIn >= 0) add("${message.tokensIn} in")
                    if (message.tokensOut >= 0) add("${message.tokensOut} out")
                    if (message.latencyMs > 0) add("%.1fs".format(message.latencyMs / 1000f))
                    if (message.tokensOut > 0 && message.latencyMs > 0) {
                        add("%.1f tok/s".format(message.tokensOut * 1000f / message.latencyMs))
                    }
                    if (message.model.isNotBlank()) add(message.model)
                }
                if (parts.isNotEmpty()) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(99.dp))
                            .background(c.fill)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            parts.joinToString("  ·  "),
                            style = t.tiny,
                            color = c.textMuted
                        )
                    }
                }
            }''')

rep('''            val count = vm.variantCount(message)
            if (count > 1) {''',
'''            val count = vm.variantCount(message)
            if (count > 1) {''')  # unchanged

# pills: copy + regenerate only
rep('''    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(IconsL.copy, "Copy") { clipboard.setText(AnnotatedString(message.text)) }
        Pill(IconsL.refresh, "Regenerate") { vm.regenerate(message) }
        Pill(
            IconsL.thumbUp, "Good",
            active = message.rating == 1,
            onToggle = { vm.rate(message, if (message.rating == 1) 0 else 1) }
        )
        Pill(
            IconsL.thumbDown, "Bad",
            active = message.rating == -1,
            onToggle = { vm.rate(message, if (message.rating == -1) 0 else -1) }
        )
    }''',
'''    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(IconsL.copy, "Copy") { clipboard.setText(AnnotatedString(message.text)) }
        Pill(IconsL.refresh, "Regenerate") { vm.regenerate(message) }
    }''')

# 8. shared model sheet call
rep('''            ModelSheetContent(
                groups = vm.sheetModels(),
                current = conversation?.model.orEmpty(),
                onSelect = { model ->
                    vm.switchModel(model)
                    showModelSheet = false
                }
            )''',
'''            ModelSheetContent(
                groups = vm.sheetModels(),
                current = conversation?.model.orEmpty(),
                favorites = favorites,
                onToggleFavorite = onToggleFavorite,
                onSelect = { model, connId ->
                    vm.switchModel(model, connId)
                    showModelSheet = false
                }
            )''')

# 9. share/copy as markdown in options dialog
rep('''                    ActionMenuItem(IconsL.share, "Share full conversation") {
                        shareText(context, vm.shareText())
                        renameDialog = false
                    }''',
'''                    ActionMenuItem(IconsL.share, "Share as Markdown") {
                        shareText(context, vm.markdownText())
                        renameDialog = false
                    }
                    ActionMenuItem(IconsL.copy, "Copy as Markdown") {
                        clipboard.setText(AnnotatedString(vm.markdownText()))
                    }''')

# 10. remove the private ModelSheetContent (now shared); import scope
i = s.find('''// ── Model sheet (14) ─────────────────────────────────────────────────────────

@Composable
private fun ModelSheetContent(''')
assert i >= 0, 'sheet marker not found'
j = s.find('@Composable\nprivate fun ActionMenuItem(', i)
assert j > i
s = s[:i] + s[j:]

rep('import androidx.compose.runtime.remember\nimport androidx.compose.runtime.saveable.rememberSaveable',
    'import androidx.compose.runtime.remember\nimport androidx.compose.runtime.rememberCoroutineScope\nimport androidx.compose.runtime.saveable.rememberSaveable')

# leftover import cleanup: CircleIconButton/StatusDot still used? StatusDot yes.
# add width import already present (Spacer width used earlier) - ensure
if 'import androidx.compose.foundation.layout.width' not in s:
    s = s.replace('import androidx.compose.foundation.layout.padding\n',
                  'import androidx.compose.foundation.layout.padding\nimport androidx.compose.foundation.layout.width\n', 1)

import io as _io
_io.open(p, 'w', encoding='utf-8').write(s)
print('chat screen ok')
