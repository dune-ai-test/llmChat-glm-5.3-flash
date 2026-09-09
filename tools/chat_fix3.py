import io

p = 'app/src/main/java/com/mrrob/llmchat/ui/ChatScreen.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')
n_applied = 0

def try_rep(old, new, tag):
    global s, n_applied
    if new in s:
        print('skip (already applied):', tag)
        return
    assert s.count(old) == 1, (tag, s.count(old))
    s = s.replace(old, new)
    n_applied += 1
    print('applied:', tag)

# A. MessageRow signature
try_rep('''private fun MessageRow(
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
) {''', 'MessageRow signature')

# B. assistant clickable -> toggle details
try_rep('''                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = {}, onLongClick = onLongPress),
            verticalArrangement = Arrangement.spacedBy(8.dp)''',
'''                .clip(RoundedCornerShape(14.dp))
                .combinedClickable(onClick = onToggleDetails, onLongClick = onLongPress),
            verticalArrangement = Arrangement.spacedBy(8.dp)''', 'assistant clickable')

# C. details block after MarkdownText in assistant
try_rep('''            MarkdownText(
                source = message.text,
                streaming = false,
                showCodeLineNumbers = vm.showCodeLineNumbers()
            )
            val count = vm.variantCount(message)''',
'''            MarkdownText(
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
                        Text(parts.joinToString("  \\u00b7  "), style = t.tiny, color = c.textMuted)
                    }
                }
            }
            val count = vm.variantCount(message)''', 'details block')

# D. model sheet call
try_rep('''            ModelSheetContent(
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
            )''', 'sheet call')

# E. remove private ModelSheetContent fn (shared one in ModelSheets.kt takes over)
start = s.find('@Composable\nprivate fun ModelSheetContent(')
assert start > 0, 'sheet fn start'
end = s.find('@Composable\nprivate fun ActionMenuItem(', start)
assert end > start, 'sheet fn end marker'
s = s[:start] + s[end:]
n_applied += 1
print('removed private ModelSheetContent')

# F. markdown share
try_rep('''                    ActionMenuItem(IconsL.share, "Share full conversation") {
                        shareText(context, vm.shareText())
                        renameDialog = false
                    }''',
'''                    ActionMenuItem(IconsL.share, "Share as Markdown") {
                        shareText(context, vm.markdownText())
                        renameDialog = false
                    }
                    ActionMenuItem(IconsL.copy, "Copy as Markdown") {
                        clipboard.setText(AnnotatedString(vm.markdownText()))
                    }''', 'markdown share')

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('done, applied', n_applied)
