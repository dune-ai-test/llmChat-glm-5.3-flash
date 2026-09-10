import io

# ---------------------------------------------------------------- VoiceViewModel
p = 'app/src/main/java/com/mrrob/llmchat/VoiceViewModel.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')
anchor = '    fun interrupt() {'
if 'fun switchModel' not in s:
    assert s.count(anchor) == 1
    s = s.replace(anchor, '''    /** Switch the model of the running voice conversation (and its connection). */
    fun switchModel(model: String, connectionId: String) {
        viewModelScope.launch {
            val conv = _conversation.value ?: return@launch
            val newConn = connectionId.ifBlank { conv.connectionId }
            repo().updateConversation(conv.copy(model = model, connectionId = newConn))
            repo().insertMessage(
                MessageEntity(conversationId = conv.id, role = "system", text = "Model switched to $model")
            )
            repo().conversation(conv.id)?.let { _conversation.value = it }
            reloadTranscript(conv.id)
        }
    }

''' + anchor)
    io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
    print('VoiceViewModel.switchModel added')

# -------------------------------------------------------------------- VoiceScreen
p = 'app/src/main/java/com/mrrob/llmchat/ui/VoiceScreen.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')

def rep(o, n, cnt=1):
    global s
    assert s.count(o) == cnt, (o[:60], s.count(o))
    s = s.replace(o, n)

# 1. remove model+dot from top bar actions
rep('''            actions = {
                val currentModel = conversation?.model?.ifBlank { vm.connection()?.activeModel ?: "" }
                    ?: vm.connection()?.activeModel ?: ""
                if (currentModel.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Text(currentModel, style = t.caption, color = c.textSecondary, maxLines = 1)
                        StatusDot(online = true)
                    }
                }
                Row(''',
'''            actions = {
                Row(''')

# 2. sheet state + connections collector + opt-in
rep('    var showTranscript by remember { mutableStateOf(false) }',
    '''    var showTranscript by remember { mutableStateOf(false) }
    var showModelSheet by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }''')
if 'val connections by app.connections' not in s:
    rep('    val conversation by vm.conversation.collectAsStateWithLifecycle()',
        '    val conversation by vm.conversation.collectAsStateWithLifecycle()\n    val connections by app.connections.collectAsStateWithLifecycle()')
s = s.replace('@Composable\nfun VoiceScreen(',
              '@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)\n@Composable\nfun VoiceScreen(', 1)

# 3. live words in the fullscreen view
rep('''                Spacer(Modifier.height(22.dp))
                Text(centerLabel, style = t.heroTitle, color = c.textPrimary)''',
'''                Spacer(Modifier.height(22.dp))
                if (phase == VoiceViewModel.Phase.LISTENING && partial.isNotBlank()) {
                    Text(
                        "\\u201c$partial\\u201d",
                        style = t.heroTitle,
                        color = c.accent,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 36.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(centerLabel, style = t.heroTitle, color = c.textPrimary)''')

# 4. model chip above deck waveform
rep('''            VoiceWave(phase = phase, active = phase == VoiceViewModel.Phase.LISTENING || phase == VoiceViewModel.Phase.SPEAKING)''',
'''            val deckModel = conversation?.model?.ifBlank { vm.connection()?.activeModel ?: "" }
                ?: vm.connection()?.activeModel ?: ""
            if (deckModel.isNotBlank()) {
                ModelChip(model = deckModel, onClick = { showModelSheet = true })
            }
            VoiceWave(phase = phase, active = phase == VoiceViewModel.Phase.LISTENING || phase == VoiceViewModel.Phase.SPEAKING)''')

# 5. fullscreen controls: wrap row + chip in a Column
rep('''        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {''',
'''        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            val hiddenModel = conversation?.model?.ifBlank { vm.connection()?.activeModel ?: "" }
                ?: vm.connection()?.activeModel ?: ""
            if (hiddenModel.isNotBlank()) {
                ModelChip(model = hiddenModel, onClick = { showModelSheet = true })
                Spacer(Modifier.height(12.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {''')
# close the extra Column: the else-branch Row ends before "        }\n    }\n}" that precedes Pieces
pieces = s.find('// ── Pieces ─')
func_tail = s.rfind('            }\n        }\n    }\n}\n', 0, pieces)
assert func_tail > 0
s = (s[:func_tail]
     + '            }\n        }  // fullscreen controls column\n        }\n    }\n}\n'
     + s[func_tail + len('            }\n        }\n    }\n}\n'):])

# 6. remove CenterMic pulse (double-animation smoothing)
rep('''    val transition = rememberInfiniteTransition(label = "micpulse")
    val pulse by transition.animateFloat(1f, 1.12f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    val bg = when (phase) {''', '    val bg = when (phase) {')
rep('''            .size(76.dp)
            .scale(if (phase == VoiceViewModel.Phase.LISTENING) pulse else 1f)
            .clip(CircleShape)''',
'''            .size(76.dp)
            .clip(CircleShape)''')

# 7. model sheet at end of VoiceScreen
func_end = s.rfind('\n}\n', 0, s.find('// ── Pieces ─'))
assert func_end > 0
sheet = '''

    if (showModelSheet) {
        val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            sheetState = sheetState,
            containerColor = c.card
        ) {
            val groups = remember(connections) {
                connections.filter { it.enabled }.map { conn ->
                    conn to (app.repo.modelsOf(conn) + conn.activeModel).filter(String::isNotBlank).distinct()
                }.filter { it.second.isNotEmpty() }
            }
            ModelSheetContent(
                groups = groups,
                current = conversation?.model ?: "",
                favorites = app.favoriteModels(),
                onToggleFavorite = app::toggleFavoriteModel,
                onSelect = { model, connId ->
                    vm.switchModel(model, connId)
                    showModelSheet = false
                }
            )
        }
    }
'''
s = s[:func_end] + sheet + s[func_end:]

# 8. ModelChip helper + imports
chip = '''
@Composable
private fun ModelChip(model: String, onClick: () -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(c.fill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        StatusDot(online = true)
        Text(
            "$model  \\u00b7  change",
            style = t.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = c.textSecondary
        )
    }
}

'''
s = s.replace('// ── Pieces ─', chip + '// ── Pieces ─', 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('voice ok')
