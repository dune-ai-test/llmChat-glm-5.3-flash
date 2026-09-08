package com.mrrob.llmchat.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrrob.llmchat.MainViewModel
import com.mrrob.llmchat.ui.components.ActionButton
import com.mrrob.llmchat.ui.components.IosCard
import com.mrrob.llmchat.ui.components.IosDivider
import com.mrrob.llmchat.ui.components.IosTextField
import com.mrrob.llmchat.ui.components.InfoRow
import com.mrrob.llmchat.ui.components.LargeTitle
import com.mrrob.llmchat.ui.components.SectionHeader
import com.mrrob.llmchat.ui.components.StatusBanner
import com.mrrob.llmchat.ui.components.displayEndpoint
import kotlinx.coroutines.delay

/**
 * Connection settings: server URL, API key and model, with a
 * "Test Connection" action that loads the model list via GET /models.
 */
@Composable
fun SettingsScreen(viewModel: MainViewModel) {
    val focusManager = LocalFocusManager.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val test by viewModel.test.collectAsStateWithLifecycle()

    var baseUrl by rememberSaveable { mutableStateOf(settings.baseUrl) }
    var apiKey by rememberSaveable { mutableStateOf(settings.apiKey) }
    var model by rememberSaveable { mutableStateOf(settings.model) }
    var apiKeyVisible by rememberSaveable { mutableStateOf(false) }
    var saved by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(saved) {
        if (saved) {
            delay(2000)
            saved = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        LargeTitle(title = "Settings")

        SectionHeader(text = "Server")
        IosCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            IosTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it; viewModel.clearTest(); saved = false },
                placeholder = "https://api.openai.com",
                keyboardType = KeyboardType.Uri
            )
            IosDivider()
            IosTextField(
                value = apiKey,
                onValueChange = { apiKey = it; viewModel.clearTest(); saved = false },
                placeholder = "API key",
                keyboardType = KeyboardType.Password,
                visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailing = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            imageVector = if (apiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                            contentDescription = if (apiKeyVisible) "Hide API key" else "Show API key",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
            IosDivider()
            IosTextField(
                value = model,
                onValueChange = { model = it; saved = false },
                placeholder = "Model (e.g. gpt-4o-mini)"
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        ActionButton(
            text = if (test.running) "Testing…" else "Test Connection",
            enabled = !test.running && baseUrl.isNotBlank() && apiKey.isNotBlank(),
            loading = test.running
        ) {
            focusManager.clearFocus()
            viewModel.testConnection(baseUrl, apiKey)
        }

        test.error?.let { error ->
            StatusBanner(text = error, isError = true)
        }
        test.successMessage?.let { message ->
            StatusBanner(text = message, isError = false)
        }

        if (test.models.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))
            SectionHeader(text = "Available models — tap to select")
            IosCard(modifier = Modifier.padding(horizontal = 20.dp)) {
                test.models.forEachIndexed { index, id ->
                    if (index > 0) IosDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                model = id
                                saved = false
                            }
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = id,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (id == model) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(text = "Save")
        IosCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
                    ) {
                        focusManager.clearFocus()
                        viewModel.saveSettings(baseUrl, apiKey, model)
                        saved = true
                    }
                    .heightIn(min = 50.dp)
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (saved) "Saved ✓" else "Save Settings",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    }
                )
            }
        }

        if (baseUrl.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Chat requests are sent to ${displayEndpoint(baseUrl)} in the OpenAI-compatible format.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        SectionHeader(text = "About")
        IosCard(modifier = Modifier.padding(horizontal = 20.dp)) {
            InfoRow(label = "App", value = "LLM Chat")
            IosDivider()
            InfoRow(label = "Version", value = "1.0.0")
            IosDivider()
            InfoRow(label = "Protocol", value = "OpenAI-compatible")
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}
