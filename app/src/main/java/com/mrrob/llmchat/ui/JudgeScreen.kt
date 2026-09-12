package com.mrrob.llmchat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mrrob.llmchat.AppViewModel
import com.mrrob.llmchat.JudgeSeat
import com.mrrob.llmchat.JudgeViewModel
import com.mrrob.llmchat.TurnPhase
import com.mrrob.llmchat.data.ConnectionEntity
import com.mrrob.llmchat.data.JudgePrompts
import com.mrrob.llmchat.ui.kit.AsterButton
import com.mrrob.llmchat.ui.kit.AsterSegmented
import com.mrrob.llmchat.ui.kit.CircleIconButton
import com.mrrob.llmchat.ui.kit.IconsL
import com.mrrob.llmchat.ui.kit.IconTile
import com.mrrob.llmchat.ui.kit.MarkdownText
import com.mrrob.llmchat.ui.kit.StatusDot
import com.mrrob.llmchat.ui.kit.noRipple
import com.mrrob.llmchat.ui.theme.LocalScheme
import com.mrrob.llmchat.ui.theme.LocalType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Judge Mode (screens 44-46): one question goes to three models, they answer
 * independently, peer-review the panel, and the chosen judge writes the
 * Consensus Answer. The transcript is collapsible section by section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JudgeScreen(app: AppViewModel, vm: JudgeViewModel, onBack: () -> Unit) {
    val c = LocalScheme.current
    val state by vm.state.collectAsStateWithLifecycle()
    val connections by vm.connections.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    var showPanel by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Keep the newest turn in view while the panel is working.
    val busy = state.busy
    LaunchedEffect(state.turns.size, busy, state.turns.lastOrNull()?.answers?.count { it != null },
        state.turns.lastOrNull()?.reviews?.count { it != null }, state.turns.lastOrNull()?.phase) {
        if (busy && state.turns.isNotEmpty()) listState.animateScrollToItem(state.turns.lastIndex)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .statusBarsPadding()
    ) {
        JudgeHeader(onBack = onBack, onPanel = { showPanel = true })
        StageRow(state.lastTurn)

        Box(modifier = Modifier.weight(1f)) {
            if (state.turns.isEmpty()) {
                JudgeEmpty(panelReady = state.panelReady, onPanel = { showPanel = true })
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(state.turns) { idx, turn ->
                        TurnBlock(
                            turn = turn,
                            isLast = idx == state.turns.lastIndex,
                            seats = state.seats,
                            vm = vm,
                            connections = connections,
                            clipboard = clipboard
                        )
                    }
                }
            }
        }

        JudgeComposer(
            value = input,
            onValueChange = { input = it },
            hasTurns = state.turns.isNotEmpty(),
            busy = busy,
            onSend = {
                if (!state.panelReady) showPanel = true
                else if (input.isNotBlank()) {
                    vm.ask(input)
                    input = ""
                }
            }
        )
    }

    if (showPanel) {
        PanelSheet(vm = vm, onDismiss = { showPanel = false })
    }
}

// ── header + stages ──────────────────────────────────────────────────────────

@Composable
private fun JudgeHeader(onBack: () -> Unit, onPanel: () -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Judge Mode", style = t.cardTitle.copy(fontSize = 17.sp), color = c.textPrimary)
            Text(
                "3 models · independent → review → consensus",
                style = t.micro.copy(fontSize = 11.sp),
                color = c.textSecondary
            )
        }
        CircleIconButton(
            icon = IconsL.arrowBack, onClick = onBack, size = 38.dp, iconSize = 19.dp,
            tint = c.textPrimary, bordered = false, modifier = Modifier.align(Alignment.CenterStart)
        )
        CircleIconButton(
            icon = IconsL.more, onClick = onPanel, size = 38.dp, iconSize = 19.dp,
            bordered = false, modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
    Box(Modifier.fillMaxWidth().height(0.7.dp).background(c.border))
}

private enum class StageState { PENDING, ACTIVE, DONE }

@Composable
private fun StageRow(turn: JudgeViewModel.Turn?) {
    val c = LocalScheme.current
    val t = LocalType.current
    val states = when (turn?.phase) {
        null -> listOf(StageState.PENDING, StageState.PENDING, StageState.PENDING)
        TurnPhase.ANSWERING -> listOf(StageState.ACTIVE, StageState.PENDING, StageState.PENDING)
        TurnPhase.REVIEWING -> listOf(StageState.DONE, StageState.ACTIVE, StageState.PENDING)
        TurnPhase.JUDGING -> listOf(StageState.DONE, StageState.DONE, StageState.ACTIVE)
        TurnPhase.DONE -> listOf(StageState.DONE, StageState.DONE, StageState.DONE)
        TurnPhase.FAILED -> listOf(StageState.DONE, StageState.DONE, StageState.PENDING)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf("Independent", "Cross-review", "Consensus").forEachIndexed { i, label ->
            StagePill(i + 1, label, states[i])
        }
    }
}

@Composable
private fun StagePill(step: Int, label: String, stage: StageState) {
    val c = LocalScheme.current
    val t = LocalType.current
    val bg = when (stage) {
        StageState.ACTIVE -> c.accentTint
        StageState.DONE -> c.successTint
        StageState.PENDING -> c.fill
    }
    val fg = when (stage) {
        StageState.ACTIVE -> c.accent
        StageState.DONE -> c.success
        StageState.PENDING -> c.textMuted
    }
    val badgeBg = when (stage) {
        StageState.ACTIVE -> c.accent
        StageState.DONE -> c.success
        StageState.PENDING -> c.border
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(14.dp).clip(CircleShape).background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            if (stage == StageState.DONE) {
                Icon(IconsL.check, null, tint = Color.White, modifier = Modifier.size(9.dp))
            } else {
                Text(
                    "$step",
                    style = t.tiny.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = if (stage == StageState.ACTIVE) Color.White else c.textMuted
                )
            }
        }
        Text(label, style = t.micro.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = fg)
    }
}

// ── one question and its deliberation ────────────────────────────────────────

@Composable
private fun TurnBlock(
    turn: JudgeViewModel.Turn,
    isLast: Boolean,
    seats: List<JudgeSeat?>,
    vm: JudgeViewModel,
    connections: List<ConnectionEntity>,
    clipboard: androidx.compose.ui.platform.ClipboardManager
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // YOUR QUESTION bubble
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "YOUR QUESTION",
                style = t.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
                color = c.textMuted
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.accent)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(turn.question, style = t.body.copy(fontSize = 14.5.sp, lineHeight = 21.sp), color = Color.White)
            }
            Text(metaLine(turn), style = t.micro.copy(fontSize = 10.5.sp), color = c.textMuted)
        }

        // Independent answers (auto-open while answering, collapsed when done)
        var showAnswers by remember(turn) { mutableStateOf(turn.phase == TurnPhase.ANSWERING) }
        LaunchedEffect(turn.phase) { if (turn.phase == TurnPhase.ANSWERING) showAnswers = true }
        CollapsibleBar(
            title = "Independent answers",
            count = "${turn.answers.count { it != null }}",
            expanded = showAnswers,
            onToggle = { showAnswers = !showAnswers }
        )
        if (showAnswers) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                seats.forEachIndexed { i, seat ->
                    SeatCard(
                        seat = seat,
                        connection = seat?.let { s -> connections.firstOrNull { it.id == s.connectionId } },
                        result = turn.answers.getOrNull(i),
                        label = JudgePrompts.labels.getOrElse(i) { "${i + 1}" },
                        running = turn.running && turn.phase == TurnPhase.ANSWERING
                    )
                }
            }
        }

        // Cross-review
        var showReviews by remember(turn) { mutableStateOf(turn.phase == TurnPhase.REVIEWING) }
        LaunchedEffect(turn.phase) { if (turn.phase == TurnPhase.REVIEWING) showReviews = true }
        if (turn.phase != TurnPhase.ANSWERING) {
            CrossReviewCard(
                turn = turn,
                seats = seats,
                connections = connections,
                expanded = showReviews,
                onToggle = { showReviews = !showReviews }
            )
        }

        // Consensus
        if (turn.consensus != null || turn.phase == TurnPhase.JUDGING || turn.phase == TurnPhase.FAILED) {
            ConsensusCard(
                turn = turn,
                judgeLabel = JudgePrompts.labels.getOrElse(vm.state.value.judgeSeat) { "A" },
                canRetry = isLast,
                onCopy = {
                    turn.consensus?.text?.let { clipboard.setText(AnnotatedString(it)) }
                },
                onRetry = { vm.retryLast() }
            )
        }
    }
}

private fun metaLine(turn: JudgeViewModel.Turn): String {
    val time = SimpleDateFormat("h:mm a", Locale.US).format(Date(turn.sentAt))
    val suffix = when (turn.phase) {
        TurnPhase.ANSWERING -> "${turn.answered} of 3 answered"
        TurnPhase.REVIEWING -> "cross-reviewing · ${turn.reviewed} of 3"
        TurnPhase.JUDGING -> "the judge is deliberating…"
        TurnPhase.DONE -> "tap a section to expand"
        TurnPhase.FAILED -> turn.failure ?: "stopped"
    }
    return "Sent $time · $suffix"
}

@Composable
private fun CollapsibleBar(title: String, count: String, expanded: Boolean, onToggle: () -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(14.dp))
            .noRipple(onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = t.desc.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = c.textPrimary)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(c.fill)
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(count, style = t.micro.copy(fontSize = 10.5.sp, fontWeight = FontWeight.Bold), color = c.textSecondary)
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.noRipple(onToggle)
        ) {
            Text(
                if (expanded) "Hide" else "Show",
                style = t.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                color = c.accent
            )
            Icon(
                IconsL.chevronDown,
                null,
                tint = c.accent,
                modifier = Modifier
                    .size(14.dp)
                    .rotate(if (expanded) 180f else 0f)
            )
        }
    }
}

@Composable
private fun SeatCard(
    seat: JudgeSeat?,
    connection: ConnectionEntity?,
    result: JudgeViewModel.SeatResult?,
    label: String,
    running: Boolean
) {
    val c = LocalScheme.current
    val t = LocalType.current
    var expanded by remember(result) { mutableStateOf(false) }
    val seatIcon = when (label) {
        "B" -> IconsL.shuffle
        "C" -> IconsL.hardDrive
        else -> IconsL.sparkles
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.card)
            .border(1.dp, c.border, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            IconTile(icon = seatIcon, size = 32.dp, tileRadius = 11.dp, iconSize = 15.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    seat?.model ?: "Seat $label",
                    style = t.desc.copy(fontSize = 14.5.sp, fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                    maxLines = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    StatusDot(online = connection?.enabled == true)
                    Text(
                        connection?.name ?: "Removed connection",
                        style = t.micro.copy(fontSize = 11.5.sp),
                        color = c.textSecondary
                    )
                }
            }
            if (result != null && result.error == null && result.latencyMs > 0) {
                Text(
                    "${(result.latencyMs / 1000f).let { if (it < 10f) "%.1f".format(it) else "%.0f".format(it) }}s",
                    style = t.micro.copy(fontSize = 11.sp),
                    color = c.textMuted
                )
            }
        }
        when {
            result == null && running -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = c.accent)
                Text("Thinking…", style = t.caption, color = c.textMuted)
            }
            result != null && result.error != null -> Text(
                result.error,
                style = t.caption.copy(color = c.danger)
            )
            result != null -> {
                val full = result.text
                val excerpt = if (full.length > 240) full.take(240).trimEnd() + "…" else full
                Text(
                    if (expanded) full else excerpt,
                    style = t.bodyTight.copy(fontSize = 13.5.sp, lineHeight = 20.sp),
                    color = c.textPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (full.length > 240) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.noRipple { expanded = !expanded }
                        ) {
                            Text(
                                if (expanded) "Show less" else "View full answer",
                                style = t.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                                color = c.accent
                            )
                            Icon(IconsL.chevronDown, null, tint = c.accent, modifier = Modifier.size(13.dp))
                        }
                    }
                    Text("Answer $label", style = t.micro.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = c.textMuted)
                }
            }
        }
    }
}

@Composable
private fun CrossReviewCard(
    turn: JudgeViewModel.Turn,
    seats: List<JudgeSeat?>,
    connections: List<ConnectionEntity>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.fill)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().noRipple(onToggle),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Icon(IconsL.chatSquare, null, tint = c.textSecondary, modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Cross-review", style = t.desc.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = c.textPrimary)
                Text("each reviewed the others", style = t.micro.copy(fontSize = 11.5.sp), color = c.textMuted)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.card)
                    .border(1.dp, c.border, RoundedCornerShape(99.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(
                    if (expanded) "Hide" else "Show",
                    style = t.micro.copy(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold),
                    color = c.accent
                )
                Icon(IconsL.chevronDown, null, tint = c.accent, modifier = Modifier.size(12.dp))
            }
        }
        if (expanded) {
            turn.reviews.forEachIndexed { i, r ->
                val label = JudgePrompts.labels.getOrElse(i) { "${i + 1}" }
                val model = seats.getOrNull(i)?.model ?: "Analyst $label"
                when {
                    r == null && turn.running -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(c.textMuted))
                        Text("Analyst $label is reviewing…", style = t.caption, color = c.textMuted)
                    }
                    r != null && r.error != null -> Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(c.danger).padding(top = 4.dp))
                        Text("Analyst $label · $model — ${r.error}", style = t.caption.copy(fontSize = 12.5.sp, lineHeight = 18.sp), color = c.danger)
                    }
                    r != null -> Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(c.textMuted))
                        Column {
                            Text(
                                "Analyst $label · $model",
                                style = t.micro.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                                color = c.textMuted
                            )
                            Text(
                                r.text,
                                style = t.caption.copy(fontSize = 12.5.sp, lineHeight = 18.sp),
                                color = c.textSecondary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConsensusCard(
    turn: JudgeViewModel.Turn,
    judgeLabel: String,
    canRetry: Boolean,
    onCopy: () -> Unit,
    onRetry: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    val shape = RoundedCornerShape(22.dp)
    val verdict = remember(turn.consensus) {
        turn.consensus?.text?.let { JudgePrompts.parseVerdict(it) }
    }
    var showReasoning by remember(turn) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(14.dp, shape, clip = false, ambientColor = c.accent.copy(alpha = 0.22f), spotColor = c.accent.copy(alpha = 0.22f))
            .clip(shape)
            .background(c.card)
            .border(1.5.dp, c.accent, shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            IconTile(
                icon = IconsL.scale, size = 38.dp, tileRadius = 12.dp, iconSize = 18.dp,
                background = c.accent, tint = Color.White
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Consensus Answer",
                    style = t.cardTitle.copy(fontSize = 16.5.sp, letterSpacing = (-0.2).sp),
                    color = c.textPrimary
                )
                Text(
                    when {
                        turn.phase == TurnPhase.JUDGING -> "the judge is weighing the panel…"
                        turn.phase == TurnPhase.FAILED -> turn.failure ?: "the judge could not answer"
                        else -> "Judged from ${turn.answers.count { it?.error == null && !it?.text.isNullOrBlank() }} responses · Analyst $judgeLabel"
                    },
                    style = t.micro.copy(fontSize = 11.5.sp),
                    color = c.textSecondary
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(c.accentTint)
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Text(
                    "FINAL",
                    style = t.tiny.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                    color = c.accent
                )
            }
        }
        if (turn.phase == TurnPhase.JUDGING) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = c.accent)
                Text("Deliberating…", style = t.caption, color = c.textMuted)
            }
        }
        verdict?.let { (consensus, disagreed, reasoning) ->
            MarkdownText(consensus)
            if (disagreed.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.fill)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(IconsL.warning, null, tint = c.warning, modifier = Modifier.size(14.dp))
                    Text(disagreed, style = t.caption.copy(fontSize = 12.sp, lineHeight = 17.sp), color = c.textSecondary)
                }
            }
            if (reasoning.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.noRipple { showReasoning = !showReasoning }
                ) {
                    Text(
                        if (showReasoning) "Hide judging reasoning" else "Show judging reasoning",
                        style = t.caption.copy(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold),
                        color = c.accent
                    )
                    Icon(IconsL.chevronDown, null, tint = c.accent, modifier = Modifier.size(13.dp))
                }
                if (showReasoning) MarkdownText(reasoning)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            CircleIconButton(
                icon = IconsL.copy, onClick = onCopy, size = 32.dp, iconSize = 14.dp,
                background = c.fill, tint = c.textSecondary, bordered = false
            )
            if (canRetry) {
                CircleIconButton(
                    icon = IconsL.refresh, onClick = onRetry, size = 32.dp, iconSize = 14.dp,
                    background = c.fill, tint = c.textSecondary, bordered = false
                )
            }
        }
    }
}

// ── empty state + composer ───────────────────────────────────────────────────

@Composable
private fun JudgeEmpty(panelReady: Boolean, onPanel: () -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        IconTile(icon = IconsL.scale, size = 72.dp, tileRadius = 22.dp, iconSize = 34.dp)
        Spacer(Modifier.height(18.dp))
        Text("One question. Three minds.", style = t.heroTitle, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Three models answer independently, challenge each other's reasoning, " +
                "and a judge consolidates everything into one Consensus Answer.",
            style = t.desc,
            color = c.textSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        AsterButton(
            text = if (panelReady) "Change your panel" else "Choose your panel",
            modifier = Modifier.width(260.dp),
            onClick = onPanel
        )
        if (!panelReady) {
            Spacer(Modifier.height(10.dp))
            Text("Pick a model for each seat A, B and C to start.", style = t.caption, color = c.textMuted)
        }
    }
}

@Composable
private fun JudgeComposer(
    value: String,
    onValueChange: (String) -> Unit,
    hasTurns: Boolean,
    busy: Boolean,
    onSend: () -> Unit
) {
    val c = LocalScheme.current
    val t = LocalType.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasTurns) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(99.dp))
                    .background(c.fill)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(IconsL.scale, null, tint = c.accent, modifier = Modifier.size(11.dp))
                Text(
                    "Follow-ups go to all 3 models",
                    style = t.tiny.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold),
                    color = c.textSecondary
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp, max = 120.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(c.card)
                    .border(1.dp, c.border, RoundedCornerShape(22.dp))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = !busy,
                    textStyle = t.body.copy(color = c.textPrimary),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth()
                )
                if (value.isEmpty()) {
                    Text(
                        if (hasTurns) "Ask a follow-up…" else "Ask a question worth three opinions…",
                        style = t.body.copy(color = c.textMuted)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (busy) c.accent.copy(alpha = 0.45f) else c.accent)
                    .noRipple(onSend),
                contentAlignment = Alignment.Center
            ) {
                Icon(IconsL.arrowUp, "Send", tint = Color.White, modifier = Modifier.size(19.dp))
            }
        }
    }
}

// ── panel sheet ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PanelSheet(vm: JudgeViewModel, onDismiss: () -> Unit) {
    val c = LocalScheme.current
    val t = LocalType.current
    val state by vm.state.collectAsStateWithLifecycle()
    val groups = remember { vm.sheetModels() }
    val firstUnset = state.seats.indexOfFirst { it == null }.let { if (it >= 0) it else 0 }
    var pickSeat by remember { mutableIntStateOf(firstUnset) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, containerColor = c.card) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Judge Panel", style = t.cardTitle.copy(fontSize = 20.sp), color = c.textPrimary)
            Text(
                "Pick three models. Each answers independently, reviews the panel, " +
                    "and the judge you choose writes the consensus.",
                style = t.caption,
                color = c.textSecondary
            )
            AsterSegmented(
                options = JudgePrompts.labels.map { "Seat $it" },
                selectedIndex = pickSeat,
                onSelect = { pickSeat = it }
            )
            state.seats.getOrNull(pickSeat)?.let { seat ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Seat ${JudgePrompts.labels.getOrElse(pickSeat) { "?" }} · ${seat.model}",
                        style = t.caption,
                        color = c.textMuted
                    )
                    Text(
                        "Clear",
                        style = t.caption.copy(fontWeight = FontWeight.SemiBold),
                        color = c.danger,
                        modifier = Modifier.noRipple { vm.setSeat(pickSeat, null) }
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (groups.isEmpty()) {
                    Text(
                        "No models available. Add a connection first.",
                        style = t.desc,
                        color = c.textMuted,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                groups.forEach { (conn, models) ->
                    Text(
                        conn.name,
                        style = t.caption.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp),
                        color = c.textMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    models.forEach { model ->
                        val selected = state.seats.getOrNull(pickSeat)?.let {
                            it.connectionId == conn.id && it.model == model
                        } == true
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) c.accentTint else Color.Transparent)
                                .noRipple {
                                    vm.setSeat(pickSeat, JudgeSeat(conn.id, model))
                                    val next = state.seats.mapIndexed { i, s ->
                                        if (i == pickSeat) JudgeSeat(conn.id, model) else s
                                    }.indexOfFirst { it == null }
                                    if (next >= 0) pickSeat = next
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(model, style = t.rowTitle, color = c.textPrimary, maxLines = 1)
                                Text(conn.provider, style = t.micro, color = c.textMuted)
                            }
                            if (selected) Icon(IconsL.check, null, tint = c.accent, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Text("Final judge", style = t.rowTitle, color = c.textPrimary)
            AsterSegmented(
                options = JudgePrompts.labels.map { "Analyst $it" },
                selectedIndex = state.judgeSeat,
                onSelect = { vm.setJudgeSeat(it) }
            )
        }
    }
}
