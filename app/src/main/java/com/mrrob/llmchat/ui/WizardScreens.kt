package com.mrrob.llmchat.ui
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrrob.llmchat.AppViewModel
import com.mrrob.llmchat.data.TestStep
import com.mrrob.llmchat.data.TestStepState
import kotlinx.coroutines.launch
import com.mrrob.llmchat.ui.kit.AsterButton
import com.mrrob.llmchat.ui.ModelManagerDialog
import com.mrrob.llmchat.ui.kit.ButtonVariant
import com.mrrob.llmchat.ui.kit.IconTile
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType

/**
 * 08 / 09 / 10 / 11 / 12 / 13 — the add-connection wizard, driven by the
 * AppViewModel's WizardState.
 */

@Composable
fun WizardIntro(app: AppViewModel, onBegin: () -> Unit, onBack: () -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
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
            Text("New connection", style = t.desc.copy(fontWeight = FontWeight.SemiBold), color = c.textSecondary)
        }
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Add Connection", style = t.largeTitle, color = c.textPrimary)
            Text("Three quick steps and you're talking to your model.", style = t.caption.copy(fontSize = 14.sp), color = c.textSecondary)
        }
        Spacer(Modifier.height(24.dp))
        WizardRailStep(
            number = 1, done = true,
            title = "Provider", desc = "OpenAI, compatible, or custom endpoint.",
            status = "OpenAI selected", statusOk = true, isLast = false
        )
        WizardRailStep(number = 2, done = false, title = "Connection", desc = "Base URL, API key and model.", status = null, statusOk = false, isLast = false)
        WizardRailStep(number = 3, done = false, title = "Customize", desc = "Optional: temperature, prompts and limits.", status = null, statusOk = false, isLast = true)
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(bottom = 28.dp)) {
            AsterButton(text = "Begin setup", onClick = onBegin)
            Text(
                "Restore a previous connection",
                style = t.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                color = c.accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        app.selectTab(com.mrrob.llmchat.AsterTab.CONNECTIONS)
                        onBack()
                    }
                    .padding(vertical = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun WizardRailStep(
    number: Int,
    done: Boolean,
    title: String,
    desc: String,
    status: String?,
    statusOk: Boolean,
    isLast: Boolean
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (done) c.accent else c.card)
                    .border(if (done) 0.dp else 1.5.dp, c.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$number",
                    style = t.rowTitle.copy(fontWeight = FontWeight.Bold),
                    color = if (done) Color.White else c.textSecondary
                )
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(1.5.dp)
                        .height(44.dp)
                        .background(c.border)
                )
            }
        }
        Column(
            modifier = Modifier
                .padding(bottom = if (isLast) 0.dp else 12.dp)
                .then(
                    if (done) Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.card)
                        .border(1.dp, c.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                    else Modifier.padding(top = 6.dp)
                ),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(title, style = t.cardTitle.copy(fontSize = 15.5.sp), color = c.textPrimary)
            Text(desc, style = t.desc, color = c.textSecondary)
            if (status != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 2.dp)) {
                    Icon(IconsL.check, null, tint = c.success, modifier = Modifier.size(12.dp))
                    Text(
                        status,
                        style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (statusOk) c.success else c.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun WizardProvider(app: AppViewModel, onContinue: () -> Unit, onBack: () -> Unit) {
    val w by app.wizard.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    val t = LocalType.current
    val providers = listOf(
        "OPENAI" to Triple("OpenAI", "Official OpenAI API — GPT-5, GPT-5-mini and more.", IconsL.sparkles),
        "OPENAI_COMPATIBLE" to Triple("OpenAI Compatible", "Any endpoint using the OpenAI chat completions format.", IconsL.shuffle),
        "CUSTOM_API" to Triple("Custom API", "Bring your own REST endpoint and request format.", IconsL.code),
        "LOCAL_SERVER" to Triple("Local Server", "A model on your own machine, e.g. LM Studio or Ollama.", IconsL.hardDrive)
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        WizardChrome(step = 1, title = "Choose a provider", subtitle = "You can change this later at any time.", onBack = onBack)
        Spacer(Modifier.height(22.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            providers.forEach { (key, data) ->
                val selected = w.provider == key
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (selected) c.accentTint else c.card)
                        .border(
                            if (selected) 1.5.dp else 1.dp,
                            if (selected) c.accent else c.border,
                            RoundedCornerShape(20.dp)
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { app.wizardChooseProvider(key) }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconTile(
                        icon = data.third,
                        size = 44.dp,
                        tileRadius = 15.dp,
                        iconSize = 20.dp,
                        background = if (selected) c.card else c.fill
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(data.first, style = t.cardTitle, color = c.textPrimary)
                        Text(data.second, style = t.desc.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = c.textSecondary)
                    }
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (selected) c.accent else Color.Transparent)
                            .border(if (selected) 0.dp else 1.5.dp, c.border, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Icon(IconsL.check, null, tint = Color.White, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        AsterButton(text = "Continue", onClick = onContinue)
    }
}

@Composable
fun WizardConfig(
    app: AppViewModel,
    onTest: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit
) {
    val w by app.wizard.collectAsStateWithLifecycle()
    val c = LocalScheme.current
    val t = LocalType.current
    var showModelPicker by remember { mutableStateOf(false) }
    var showAllModels by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        WizardChrome(
            step = 2,
            title = "Connection details",
            subtitle = "Your key is encrypted and never leaves this device.",
            onBack = onBack
        )
        Spacer(Modifier.height(20.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WizardField(label = "API Base URL", value = w.baseUrl, placeholder = "https://api.openai.com", keyboard = KeyboardType.Uri) {
                app.wizardUrlChanged(it)
            }
            WizardField(
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

            // Models - one summary row opens the picker dialog
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
                            if (chosen.isEmpty()) "Choose models to use\u2026" else chosen.joinToString(),
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

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(IconsL.lock, null, tint = c.textMuted, modifier = Modifier.size(13.dp))
                Text("Requests are sent only to your endpoint.", style = t.caption, color = c.textMuted)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.card)
                    .border(1.dp, c.border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Advanced Settings", style = t.rowTitle.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary)
                    Text("Temperature · Streaming · Limits", style = t.caption, color = c.textSecondary)
                }
                Icon(IconsL.chevronDown, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            AsterButton(
                text = "Test Connection",
                modifier = Modifier.weight(1f),
                enabled = w.baseUrl.isNotBlank(),
                onClick = onTest
            )
            AsterButton(
                text = "Continue",
                modifier = Modifier.weight(1f),
                onClick = {
                    if (w.baseUrl.isNotBlank() &&
                        (w.activeModel.isNotBlank() || w.selectedModels.isNotEmpty())
                    ) onContinue()
                }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ReadLine(label: String, value: String) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = t.desc.copy(fontWeight = FontWeight.Medium), color = c.textSecondary)
        Text(value.ifBlank { "—" }, style = t.rowTitle.copy(fontSize = 15.sp), color = c.textPrimary, maxLines = 1)
    }
}

@Composable
private fun MetaCard(key: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(key.uppercase(), style = t.tiny.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp), color = c.textMuted)
        Text(value, style = t.rowTitle.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold), color = c.textPrimary)
    }
}

@Composable
private fun TestRow(step: TestStep, title: String, detail: String, state: TestStepState?) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when (state) {
            TestStepState.RUNNING -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = c.accent)
            TestStepState.DONE -> Icon(IconsL.checkCircle, null, tint = c.success, modifier = Modifier.size(20.dp))
            TestStepState.FAILED -> Icon(IconsL.close, null, tint = c.danger, modifier = Modifier.size(20.dp))
            null -> Box(Modifier.size(20.dp).clip(CircleShape).border(2.dp, c.border, CircleShape))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                title,
                style = if (state == TestStepState.RUNNING)
                    t.rowTitle.copy(fontWeight = FontWeight.SemiBold)
                else t.desc,
                color = if (state == TestStepState.RUNNING) c.textPrimary else c.textSecondary
            )
            if (state == TestStepState.RUNNING || state == TestStepState.DONE) {
                Text(detail, style = t.caption, color = c.textMuted)
            }
        }
    }
}

private fun maskKeyForDisplay(key: String): String =
    if (key.length <= 8) "•".repeat(key.length)
    else key.take(6) + "•".repeat(6) + key.takeLast(3)
