import io

p = 'app/src/main/java/com/mrrob/llmchat/ui/WizardScreens.kt'
s = io.open(p, encoding='utf-8', newline='').read().replace('\r\n', '\n')

# --- Replace the whole inline Models block + its old ModelPickerDialog call ---
start = s.find('            // Models - pick several; one is the connection\'s default')
assert start > 0, 'models start'
end = s.find('            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {\n                Icon(IconsL.lock', start)
assert end > start, 'lock note end'

new_block = '''            // Models - one summary row opens the picker dialog
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Models", style = t.desc.copy(fontWeight = FontWeight.Medium), color = c.textSecondary)
                val chosen = (w.selectedModels + w.activeModel).filter(String::isNotBlank).distinct()
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.card)
                        .border(1.dp, c.border, RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showModelPicker = true }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (chosen.isEmpty()) "Choose models to use\\u2026" else chosen.joinToString(),
                            style = t.rowTitle,
                            color = if (chosen.isEmpty()) c.textMuted else c.textPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (w.activeModel.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Default: ${w.activeModel}",
                                style = t.tiny.copy(fontWeight = FontWeight.SemiBold),
                                color = c.accent
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(IconsL.chevronRight, null, tint = c.textMuted, modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    if (chosen.isEmpty()) "Tap to fetch from the server, search, or add by name."
                    else "${chosen.size} selected - one is marked default.",
                    style = t.tiny,
                    color = c.textMuted
                )
            }

            if (showModelPicker) {
                ModelManagerDialog(
                    allModels = (w.fetchedModels + w.selectedModels).distinct(),
                    selectedModels = w.selectedModels,
                    activeModel = w.activeModel,
                    favorites = app.favoriteModels(),
                    fetching = w.fetchingModels,
                    error = w.modelsError,
                    favoritesAvailable = true,
                    onRefresh = { app.wizardFetchModels() },
                    onToggleSelect = app::wizardToggleModel,
                    onSetDefault = app::wizardSetDefaultModel,
                    onToggleFavorite = app::toggleFavoriteModel,
                    onAddModel = app::wizardAddModel,
                    onDismiss = { showModelPicker = false }
                )
            }

'''
s = s[:start] + new_block + s[end:]

# --- Remove the old private ModelPickerDialog fn ---
mp = s.find('/** Search the fetched list or type a model id; tapping a result makes it active. */')
if mp > 0:
    mend = s.find('@Composable\nprivate fun ReadLine(', mp)
    if mend < 0:
        mend = s.find('@Composable\nprivate fun MetaCard(', mp)
    assert mend > mp, 'ModelPickerDialog end'
    s = s[:mp] + s[mend:]
    print('removed old ModelPickerDialog')

# --- imports ---
if 'import com.mrrob.llmchat.ui.kit.ModelManagerDialog' not in s:
    s = s.replace('import com.mrrob.llmchat.ui.kit.AsterButton',
                  'import com.mrrob.llmchat.ui.kit.AsterButton\nimport com.mrrob.llmchat.ui.ModelManagerDialog', 1)
if 'import androidx.compose.foundation.layout.width' not in s:
    s = s.replace('import androidx.compose.foundation.layout.size\n',
                  'import androidx.compose.foundation.layout.size\nimport androidx.compose.foundation.layout.width\n', 1)
if 'import androidx.compose.foundation.shape.CircleShape' not in s and 'CircleShape' in s and 'import androidx.compose.foundation.shape.CircleShape' not in s:
    s = s.replace('import androidx.compose.foundation.shape.RoundedCornerShape',
                  'import androidx.compose.foundation.shape.CircleShape\nimport androidx.compose.foundation.shape.RoundedCornerShape', 1)

io.open(p, 'w', encoding='utf-8', newline='\n').write(s)
print('wizard compact ok')
