import io

p = 'app/src/main/java/com/mrrob/llmchat/ui/ChatScreen.kt'
raw = io.open(p, encoding='utf-8', newline='').read()
s = raw.replace('\r\n', '\n')

def rep(old, new, count=1):
    global s
    assert s.count(old) == count, (old[:70], s.count(old))
    s = s.replace(old, new)

# 1. assistant click handler + details block
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
            )
            val count = vm.variantCount(message)''',
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
                        Text(parts.joinToString("  ·  "), style = t.tiny, color = c.textMuted)
                    }
                }
            }
            val count = vm.variantCount(message)''')

# 2. user bubble click: no-op stays; assistant only. (unchanged)

# 3. model sheet call -> shared signature
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

# 4. remove private ModelSheetContent function (now shared in ModelSheets.kt)
start = s.find('// ── Model sheet (14) ─')
assert start >= 0, 'sheet section marker'
end = s.find('@Composable\nprivate fun ActionMenuItem(')
assert end > start
s = s[:start] + s[end:]

# 5. markdown share/copy
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

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('chat completion ok')
