import io

p = 'app/src/main/java/com/mrrob/llmchat/ui/WizardScreens.kt'
s = io.open(p, encoding='utf-8').read()

def rep(old, new, count=1):
    global s
    assert s.count(old) == count, (old[:70], s.count(old))
    s = s.replace(old, new)

# add showAllModels state next to the picker state
rep(
    '    var showModelPicker by remember { mutableStateOf(false) }',
    '    var showModelPicker by remember { mutableStateOf(false) }\n'
    '    var showAllModels by remember { mutableStateOf(false) }'
)

start = s.find('            // Models - cached per URL, searchable picker, or fetch live')
end = s.find('            if (showModelPicker) {')
assert start >= 0 and end > start

new_block = '''            // Models - pick several; one is the connection's default
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (w.selectedModels.isEmpty()) "Models"
                        else "Models \\u00b7 ${w.selectedModels.size} selected",
                        style = t.desc.copy(fontWeight = FontWeight.Medium),
                        color = c.textSecondary
                    )
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
                val visible = if (showAllModels) shown else shown.take(5)
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
                visible.forEach { model ->
                    val selected = model in w.selectedModels
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
                                    val next = if (model in state.selectedModels) {
                                        state.selectedModels - model
                                    } else {
                                        (state.selectedModels + model).distinct()
                                    }
                                    var act = state.activeModel
                                    if (model in next && act.isBlank()) act = model
                                    if (act == model && act !in next) act = next.firstOrNull().orEmpty()
                                    state.copy(selectedModels = next, activeModel = act)
                                }
                            }
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            model,
                            style = t.rowTitle,
                            color = c.textPrimary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        if (active) {
                            Text(
                                "DEFAULT",
                                style = t.tiny.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                                color = c.accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(c.accentTint)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        } else if (selected) {
                            Text(
                                "Set default",
                                style = t.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = c.textSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
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
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        if (selected) {
                            Icon(IconsL.check, "Selected", tint = c.accent, modifier = Modifier.size(18.dp))
                        } else {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, c.border, CircleShape)
                            )
                        }
                    }
                }
                if (shown.size > 5) {
                    Text(
                        if (showAllModels) "Show less" else "Show all ${shown.size} models",
                        style = t.desc.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = c.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { showAllModels = !showAllModels }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
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
                        Text(
                            if (w.fetchingModels) "Fetching..." else "Fetch models",
                            style = t.desc.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                            color = c.accent
                        )
                    }
                }
            }

'''
s = s[:start] + new_block + s[end:]

# Ensure CircleShape import exists below the package line (used by the empty checkbox)
if 'import androidx.compose.foundation.shape.CircleShape' not in s:
    body_marker = '\nimport androidx.compose.foundation.background'
    assert body_marker in s
    s = s.replace(body_marker, '\nimport androidx.compose.foundation.shape.CircleShape' + body_marker, 1)
    print('added CircleShape import')

io.open(p, 'w', encoding='utf-8').write(s)
print('wizard models block replaced')
