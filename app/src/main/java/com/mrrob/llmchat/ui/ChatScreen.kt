package com.mrrob.llmchat.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrrob.llmchat.ChatViewModel
import com.mrrob.llmchat.data.ApiErrorKind
import com.mrrob.llmchat.data.ConnectionEntity
import com.mrrob.llmchat.data.Markdown
import com.mrrob.llmchat.data.MessageEntity
import kotlinx.coroutines.launch
import com.mrrob.llmchat.ui.kit.AsterButton
import com.mrrob.llmchat.ui.kit.ButtonVariant
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.kit.MarkdownText
import com.mrrob.llmchat.ui.kit.SectionLabel
import com.mrrob.llmchat.ui.kit.StatusDot
import com.mrrob.llmchat.ui.theme.LocalDensity
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType

/**
 * 17 / 18 / 19 / 20 / 21 — text chat: header with model pill, transcript,
 * streaming, message actions, variants, drafts and composer.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    favorites: List<String> = emptyList(),
    onToggleFavorite: (String) -> Unit = {},
    onBack: () -> Unit,
    onVoice: () -> Unit,
    onOpenConnections: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    val d = LocalDensity.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val settings by vm.settings.collectAsStateWithLifecycle()
    val conversation by vm.conversation.collectAsStateWithLifecycle()
    val messages by vm.messages.collectAsStateWithLifecycle()
    val streaming by vm.streaming.collectAsStateWithLifecycle()
    val generating by vm.generating.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by rememberSaveable { mutableStateOf("") }
    var detailsFor by remember { mutableStateOf<Long?>(null) }
    val isAtBottom = remember { mutableStateOf(true) }
    var draftApplied by rememberSaveable { mutableStateOf(false) }
    var showModelSheet by rememberSaveable { mutableStateOf(false) }
    var menuMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var editMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var renameDialog by remember { mutableStateOf(false) }

    // Restore the draft exactly once after the conversation loads.
    LaunchedEffect(Unit) {
        vm.draft.collect { text ->
            if (!draftApplied && text.isNotBlank()) {
                input = text
                draftApplied = true
            }
        }
    }

    val activeConnection = remember(conversation?.connectionId) { vm.activeConnection() }
    val lastAssistantId = remember(messages) {
        messages.lastOrNull { it.role == "assistant" }?.id
    }

    // Track whether the viewport sits at the bottom.
    LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            last == null || last.index >= info.totalItemsCount - 1
        }.collect { isAtBottom.value = it }
    }

    // Auto-scroll while new content arrives — unless the user scrolled up to read.
    LaunchedEffect(messages.size, streaming?.text?.length, generating) {
        if (settings.autoScroll && isAtBottom.value && (messages.isNotEmpty() || generating)) {
            listState.animateScrollToItem(messages.size.coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                IconsL.chevronLeft, "Back", tint = c.textPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        vm.saveDraft(input)
                        onBack()
                    }
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    conversation?.title?.ifBlank { "New conversation" } ?: "New conversation",
                    style = t.rowTitle.copy(fontWeight = FontWeight.Bold, fontSize = 17.sp),
                    color = c.textPrimary,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        conversation?.model?.ifBlank { activeConnection?.activeModel ?: "" } ?: "No model",
                        style = t.caption,
                        color = c.textSecondary,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(6.dp))
                    StatusDot(online = online)
                    Spacer(Modifier.width(5.dp))
                    Text(if (online) "Connected" else "Offline", style = t.caption, color = c.textSecondary)
                }
            }
            Icon(
                IconsL.more, "Options", tint = c.textSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { renameDialog = true }
            )
        }

        // ── Transcript ────────────────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (messages.isEmpty() && streaming == null && !generating) {
                EmptyChat(onPick = { prompt -> input = prompt })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(d.messageGap)
                ) {
                    items(messages, key = { it.id }) { message ->
                        MessageRow(
                            message = message,
                            isLastAssistant = message.id == lastAssistantId,
                            expandedDetails = detailsFor == message.id,
                            onToggleDetails = {
                                detailsFor = if (detailsFor == message.id) null else message.id
                            },
                            vm = vm,
                            onLongPress = { menuMessage = message }
                        )
                    }
                    val streamState = streaming
                    if (generating && streamState != null) {
                        item {
                            if (streamState.text.isNotEmpty()) {
                                StreamingBubble(text = streamState.text, onStop = vm::stopGenerating)
                            } else {
                                ThinkingRow()
                            }
                        }
                    }
                    streaming?.error?.let { err ->
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

        // ── Composer ──────────────────────────────────────────────────────────
        Composer(
            value = input,
            onValueChange = { input = it },
            generating = generating,
            onHome = {
                vm.saveDraft(input)
                onBack()
            },
            placeholder = "Message Aster…",
            enterToSend = settings.enterToSend,
            onSend = {
                val text = input
                input = ""
                draftApplied = true
                vm.send(text)
            },
            onStop = vm::stopGenerating,
            onMic = onVoice,
            onModelClick = { showModelSheet = true },
            modelLabel = conversation?.model?.ifBlank { activeConnection?.activeModel } ?: ""
        )
    }

    // ── Model sheet (14) ─────────────────────────────────────────────────────
    if (showModelSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showModelSheet = false },
            sheetState = sheetState,
            containerColor = c.card
        ) {
            ModelSheetContent(
                groups = vm.sheetModels(),
                current = conversation?.model.orEmpty(),
                favorites = favorites,
                onToggleFavorite = onToggleFavorite,
                onSelect = { model, connId ->
                    vm.switchModel(model, connId)
                    showModelSheet = false
                }
            )
        }
    }

    // ── Message actions popup (20) ───────────────────────────────────────────
    menuMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { menuMessage = null },
            containerColor = c.card,
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    if (message.role == "user") "Message" else "Response",
                    style = t.rowTitle,
                    color = c.textPrimary
                )
            },
            text = {
                Column {
                    ActionMenuItem(IconsL.copy, "Copy") {
                        clipboard.setText(AnnotatedString(message.text))
                        menuMessage = null
                    }
                    if (message.role == "assistant") {
                        ActionMenuItem(IconsL.refresh, "Regenerate") {
                            vm.regenerate(message)
                            menuMessage = null
                        }
                        ActionMenuItem(IconsL.volume, "Read aloud") {
                            vm.speak(Markdown.toPlainText(message.text))
                            menuMessage = null
                        }
                    }
                    if (message.role == "user") {
                        ActionMenuItem(IconsL.pencil, "Edit") {
                            editMessage = message
                            menuMessage = null
                        }
                    }
                    ActionMenuItem(IconsL.share, "Share") {
                        shareText(context, message.text)
                        menuMessage = null
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .height(1.dp)
                            .background(c.border)
                    )
                    ActionMenuItem(IconsL.trash, "Delete", danger = true) {
                        vm.deleteMessage(message)
                        menuMessage = null
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { menuMessage = null }) { Text("Cancel", color = c.textSecondary) }
            }
        )
    }

    // ── Edit message dialog (branch / replace) ───────────────────────────────
    editMessage?.let { message ->
        var text by remember { mutableStateOf(message.text) }
        AlertDialog(
            onDismissRequest = { editMessage = null },
            containerColor = c.card,
            title = { Text("Edit message", style = t.cardTitle, color = c.textPrimary) },
            text = {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 80.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(c.bg)
                        .border(1.dp, c.border, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        textStyle = t.body.copy(color = c.textPrimary),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.editUserMessageInPlace(message, text)
                    editMessage = null
                }) { Text("Replace", color = c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        vm.editAndBranch(message, text)
                        editMessage = null
                    }) { Text("Branch", color = c.accent, fontWeight = FontWeight.SemiBold) }
                    TextButton(onClick = { editMessage = null }) { Text("Cancel", color = c.textSecondary) }
                }
            }
        )
    }

    // ── Rename conversation / share whole ────────────────────────────────────
    conversation?.let { convForRename ->
        if (renameDialog) {
            var text by remember { mutableStateOf(convForRename.title) }
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            containerColor = c.card,
            title = { Text("Conversation options", style = t.cardTitle, color = c.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.bg)
                            .border(1.dp, c.border, RoundedCornerShape(14.dp))
                            .padding(14.dp)
                    ) {
                        BasicTextField(
                            value = text,
                            onValueChange = { text = it },
                            singleLine = true,
                            textStyle = t.body.copy(color = c.textPrimary),
                            cursorBrush = SolidColor(c.accent),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ActionMenuItem(IconsL.share, "Share as Markdown") {
                        shareText(context, vm.markdownText())
                        renameDialog = false
                    }
                    ActionMenuItem(IconsL.copy, "Copy as Markdown") {
                        clipboard.setText(AnnotatedString(vm.markdownText()))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (text.isNotBlank()) vm.rename(text.trim())
                    renameDialog = false
                }) { Text("Rename", color = c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { renameDialog = false }) { Text("Close", color = c.textSecondary) }
            }
        )
        }
    }
}

// ── Message rows ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageRow(
    message: MessageEntity,
    isLastAssistant: Boolean,
    expandedDetails: Boolean,
    onToggleDetails: () -> Unit,
    vm: ChatViewModel,
    onLongPress: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    val d = LocalDensity.current

    when (message.role) {
        "user" -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 20.dp,
                                topEnd = 20.dp,
                                bottomStart = 20.dp,
                                bottomEnd = 6.dp
                            )
                        )
                        .background(c.accent)
                        .combinedClickable(onClick = {}, onLongClick = onLongPress)
                        .padding(horizontal = d.bubbleH, vertical = d.bubbleV)
                ) {
                    Text(message.text, style = t.body, color = Color.White)
                }
                if (message.status == "QUEUED") {
                    Text("Queued · will send when online", style = t.tiny, color = c.warning)
                }
            }
        }

        "assistant" -> Column(
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
                        Text(parts.joinToString("  \u00b7  "), style = t.tiny, color = c.textMuted)
                    }
                }
            }
            val count = vm.variantCount(message)
            if (count > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        IconsL.chevronLeft, "Previous response",
                        tint = c.textSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { vm.cycleVariant(message, -1) }
                    )
                    Text(
                        "Response ${vm.variantIndex(message)} of $count",
                        style = t.tiny,
                        color = c.textMuted
                    )
                    Icon(
                        IconsL.chevronRight, "Next response",
                        tint = c.textSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { vm.cycleVariant(message, 1) }
                    )
                }
            }
            if (isLastAssistant) {
                MessagePills(message = message, vm = vm)
            }
        }

        else -> Text(
            text = message.text,
            style = t.tiny,
            color = c.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        )
    }
}

@Composable
private fun MessagePills(message: MessageEntity, vm: ChatViewModel) {
    val clipboard = LocalClipboardManager.current
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
    }
}

@Composable
private fun Pill(
    icon: ImageVector,
    label: String,
    active: Boolean = false,
    onToggle: () -> Unit = {}
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (active) c.accentTint else c.card)
            .border(1.dp, if (active) c.accent else c.border, RoundedCornerShape(99.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle
            )
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (active) c.accent else c.textSecondary, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = t.desc.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = if (active) c.accent else c.textSecondary
        )
    }
}

// ── Streaming bubble (19) ────────────────────────────────────────────────────

@Composable
private fun StreamingBubble(text: String, onStop: () -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MarkdownText(source = text, streaming = true)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(99.dp))
                .background(c.card)
                .border(1.dp, c.border, RoundedCornerShape(99.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onStop
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(IconsL.square, null, tint = c.textPrimary, modifier = Modifier.size(11.dp))
            Spacer(Modifier.width(7.dp))
            Text(
                "Stop generating",
                style = t.desc.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                color = c.textPrimary
            )
        }
    }
}

@Composable
private fun ThinkingRow() {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .border(2.dp, c.border, CircleShape)
                .border(2.dp, c.accent, CircleShape)
        )
        Text("Thinking…", style = t.caption, color = c.textMuted)
    }
}

// ── Error recovery (34) ──────────────────────────────────────────────────────

@Composable
private fun ErrorRecoveryCard(
    kind: ApiErrorKind,
    message: String,
    detail: String,
    onTryAgain: () -> Unit,
    onChangeModel: () -> Unit,
    onCheckKey: () -> Unit,
    onDismiss: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    val title = when (kind) {
        ApiErrorKind.INVALID_KEY -> "Invalid API key"
        ApiErrorKind.RATE_LIMIT -> "You're going too fast"
        ApiErrorKind.MODEL_NOT_FOUND -> "Model unavailable"
        ApiErrorKind.CONTEXT_TOO_LARGE -> "Conversation is getting long"
        ApiErrorKind.TIMEOUT -> "The request timed out"
        ApiErrorKind.SERVER -> "The server had a problem"
        ApiErrorKind.OFFLINE -> "You're offline"
        else -> "Couldn't reach the server"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (kind == ApiErrorKind.RATE_LIMIT) c.warningTint else c.card)
            .border(1.dp, c.border, RoundedCornerShape(20.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = t.cardTitle, color = c.textPrimary)
        Text(message, style = t.desc, color = c.textSecondary)
        if (detail.isNotBlank()) {
            Text(
                detail.take(140),
                style = t.monoSmall,
                color = c.textMuted
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            when (kind) {
                ApiErrorKind.INVALID_KEY -> {
                    AsterButton(text = "Check API Key", modifier = Modifier.weight(1f), height = 42.dp, radius = 13.dp, onClick = onCheckKey)
                    AsterButton(text = "Retry", variant = ButtonVariant.NEUTRAL, modifier = Modifier.weight(1f), height = 42.dp, radius = 13.dp, onClick = onTryAgain)
                }
                ApiErrorKind.MODEL_NOT_FOUND -> {
                    AsterButton(text = "Change Model", modifier = Modifier.weight(1f), height = 42.dp, radius = 13.dp, onClick = onChangeModel)
                    AsterButton(text = "Retry", variant = ButtonVariant.NEUTRAL, modifier = Modifier.weight(1f), height = 42.dp, radius = 13.dp, onClick = onTryAgain)
                }
                ApiErrorKind.CONTEXT_TOO_LARGE -> {
                    AsterButton(text = "Start New Chat", modifier = Modifier.weight(1f), height = 42.dp, radius = 13.dp, onClick = onDismiss)
                    AsterButton(text = "Continue", variant = ButtonVariant.NEUTRAL, modifier = Modifier.weight(1f), height = 42.dp, radius = 13.dp, onClick = onTryAgain)
                }
                else -> {
                    AsterButton(text = "Try Again", modifier = Modifier.weight(1f), height = 42.dp, radius = 13.dp, onClick = onTryAgain)
                    AsterButton(text = "Dismiss", variant = ButtonVariant.NEUTRAL, modifier = Modifier.weight(1f), height = 42.dp, radius = 13.dp, onClick = onDismiss)
                }
            }
        }
    }
}

// ── Empty chat (17) ──────────────────────────────────────────────────────────

@Composable
private fun EmptyChat(onPick: (String) -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("What can I help with?", style = t.heroTitle.copy(fontSize = 22.sp), color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ask anything — or start with an idea below.",
            style = t.caption.copy(fontSize = 14.sp),
            color = c.textSecondary
        )
        Spacer(Modifier.height(24.dp))
        val suggestions = listOf(
            IconsL.lightbulb to ("Explain a concept" to "Like you're new to the topic"),
            IconsL.mail to ("Draft an email" to "Polished and to the point"),
            IconsL.bug to ("Debug my code" to "Find the bug, suggest a fix"),
            IconsL.map to ("Plan a trip" to "Itineraries and local tips")
        )
        val prompts = mapOf(
            "Explain a concept" to "Explain how HTTPS works, like I'm new to networking.",
            "Draft an email" to "Draft a short, polite email to reschedule my meeting.",
            "Debug my code" to "Why does my retry logic throw after the second attempt?",
            "Plan a trip" to "Plan three days in Kyoto in late October."
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            suggestions.chunked(2).forEach { rowItems ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowItems.forEach { (icon, pair) ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(18.dp))
                                .background(c.card)
                                .border(1.dp, c.border, RoundedCornerShape(18.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { prompts[pair.first]?.let(onPick) }
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(icon, null, tint = c.accent, modifier = Modifier.size(18.dp))
                            Text(pair.first, style = t.rowTitle.copy(fontWeight = FontWeight.SemiBold), color = c.textPrimary)
                            Text(
                                pair.second,
                                style = t.tiny.copy(fontSize = 11.5.sp, lineHeight = 16.sp),
                                color = c.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Composer (17 / 18 / 21) ──────────────────────────────────────────────────

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    generating: Boolean,
    onHome: () -> Unit,
    placeholder: String,
    enterToSend: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onMic: () -> Unit,
    onModelClick: () -> Unit,
    modelLabel: String
) {
    val c = LocalScheme.current
    val t = LocalType.current
    val d = LocalDensity.current
    val canSend = value.isNotBlank() && !generating

    Column(modifier = Modifier.fillMaxWidth().imePadding()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(21.dp),
            contentAlignment = Alignment.Center
        ) {
            if (modelLabel.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(99.dp))
                        .background(c.fill)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onModelClick
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    StatusDot(online = true)
                    Text(
                        "$modelLabel · Connected".replaceFirstChar { it.uppercase() },
                        style = t.tiny.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                        color = c.textSecondary
                    )
                }
            }
        }

        Row(
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
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.card)
                    .border(1.dp, c.border, RoundedCornerShape(22.dp))
                    .heightIn(min = 44.dp, max = 140.dp)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                textStyle = t.body.copy(fontSize = 15.sp, color = c.textPrimary),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = if (enterToSend) ImeAction.Send else ImeAction.Default),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                maxLines = 5,
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.Bottom) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (value.isEmpty()) {
                                Text(
                                    placeholder,
                                    style = t.body.copy(fontSize = 15.sp),
                                    color = c.textMuted,
                                    maxLines = 1
                                )
                            }
                            inner()
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            IconsL.mic, "Voice input",
                            tint = c.textSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onMic
                                )
                        )
                    }
                }
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            generating -> c.fill
                            canSend -> c.accent
                            else -> c.card
                        }
                    )
                    .then(
                        if (!canSend && !generating) Modifier.border(1.dp, c.border, CircleShape) else Modifier
                    )
                    .alpha(if (!canSend && !generating) 0.45f else 1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = generating || canSend
                    ) {
                        if (generating) onStop() else onSend()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (generating) IconsL.square else IconsL.arrowUp,
                    if (generating) "Stop" else "Send",
                    tint = if (generating) c.textPrimary else Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

// ── Model sheet (14) ─────────────────────────────────────────────────────────

@Composable
private fun ActionMenuItem(
    icon: ImageVector,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, null, tint = if (danger) c.danger else c.textPrimary, modifier = Modifier.size(16.dp))
        Text(label, style = t.bodyTight.copy(fontWeight = FontWeight.SemiBold), color = if (danger) c.danger else c.textPrimary)
    }
}

private fun shareText(context: android.content.Context, text: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Share"))
}
