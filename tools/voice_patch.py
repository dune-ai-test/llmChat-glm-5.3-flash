import io

p = 'app/src/main/java/com/mrrob/llmchat/ui/VoiceScreen.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')
def rep(old, new, tag, count=1):
    global s
    assert s.count(old) == count, (tag, s.count(old))
    s = s.replace(old, new); print('applied:', tag)

# 1. Replace custom centered header with the shared left-aligned ScreenTopBar.
old_header = '''        // ── Top bar ───────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                IconsL.chevronDown, "Minimize", tint = c.textSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        recognizer.stopListening()
                        onExit()
                    }
            )
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Voice Chat", style = t.rowTitle.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp), color = c.textPrimary)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        conversation?.model?.ifBlank { vm.connection()?.activeModel ?: "" }
                            ?: vm.connection()?.activeModel ?: "",
                        style = t.caption,
                        color = c.textSecondary
                    )
                    StatusDot(online = true)
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (showTranscript) c.accentTint else c.card)
                    .border(1.dp, if (showTranscript) c.accentTint else c.border, RoundedCornerShape(99.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { showTranscript = !showTranscript }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    IconsL.chatSquare, "Transcript",
                    tint = if (showTranscript) c.accent else c.textSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Transcript",
                    style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (showTranscript) c.accent else c.textSecondary
                )
            }
        }'''

new_header = '''        // ── Top bar (matches Home / Chats / Connections placement) ─────────────
        ScreenTopBar(
            title = "Voice",
            leading = {
                Icon(
                    IconsL.chevronDown, "Minimize", tint = c.textSecondary,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            recognizer.stopListening()
                            onExit()
                        }
                )
            },
            actions = {
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
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(if (showTranscript) c.accentTint else c.card)
                        .border(1.dp, if (showTranscript) c.accentTint else c.border, RoundedCornerShape(99.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showTranscript = !showTranscript }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        IconsL.chatSquare, "Transcript",
                        tint = if (showTranscript) c.accent else c.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Transcript",
                        style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (showTranscript) c.accent else c.textSecondary
                    )
                }
            }
        )'''
rep(old_header, new_header, 'unified top bar')

# 2. add ScreenTopBar import
if 'import com.mrrob.llmchat.ui.kit.ScreenTopBar' not in s:
    s = s.replace('import com.mrrob.llmchat.ui.kit.StatusDot',
                  'import com.mrrob.llmchat.ui.kit.ScreenTopBar\nimport com.mrrob.llmchat.ui.kit.StatusDot', 1)
    print('added ScreenTopBar import')

# 3. home button in the in-transcript control row (before the mic row's callEnd)
rep('''                CenterMic(phase = phase, onClick = { micTap() })
                DeckButton(IconsL.square, onClick = {
                    recognizer.stopListening()
                    tts.stop()
                    vm.setPhase(VoiceViewModel.Phase.READY)
                })
                DeckButton(IconsL.callEnd, danger = true, onClick = {
                    sessionActive = false
                    recognizer.stopListening()
                    tts.stop()
                    vm.endSession()
                    onExit()
                })''',
'''                CenterMic(phase = phase, onClick = { micTap() })
                DeckButton(IconsL.square, onClick = {
                    recognizer.stopListening()
                    tts.stop()
                    vm.setPhase(VoiceViewModel.Phase.READY)
                })
                DeckButton(IconsL.callEnd, danger = true, onClick = {
                    sessionActive = false
                    recognizer.stopListening()
                    tts.stop()
                    vm.endSession()
                    onExit()
                })
                DeckButton(IconsL.home, onClick = { recognizer.stopListening(); onExit() })''',
'home btn (transcript row)')

# 4. home button in the hidden control row
rep('''                DeckButton(IconsL.callEnd, danger = true, onClick = {
                    sessionActive = false
                    recognizer.stopListening()
                    tts.stop()
                    vm.endSession()
                    onExit()
                })
            }
        }
    }
}''',
'''                DeckButton(IconsL.callEnd, danger = true, onClick = {
                    sessionActive = false
                    recognizer.stopListening()
                    tts.stop()
                    vm.endSession()
                    onExit()
                })
                DeckButton(IconsL.home, onClick = { recognizer.stopListening(); onExit() })
            }
        }
    }
}''', 'home btn (hidden row)')

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('voice ok')
