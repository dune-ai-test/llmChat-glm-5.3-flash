package com.mrrob.llmchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrrob.llmchat.data.ConnectionEntity
import com.mrrob.llmchat.data.JudgePrompts
import com.mrrob.llmchat.data.WireMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One seat of the panel: a model on a specific connection. */
data class JudgeSeat(val connectionId: String, val model: String)

/** How far a single deliberation has got. */
enum class TurnPhase { ANSWERING, REVIEWING, JUDGING, DONE, FAILED }

/**
 * Runs Judge Mode sessions: three models answer the same question
 * independently, each then peer-reviews the whole panel, and the chosen
 * judge model consolidates everything into one Consensus Answer. Follow-ups
 * re-run the same pipeline with the earlier consensus as context.
 * Nothing is persisted - a session lives as long as the view model.
 */
class JudgeViewModel(private val app: AppViewModel) : ViewModel() {

    data class SeatResult(val text: String, val error: String?, val latencyMs: Long)

    data class Turn(
        val question: String,
        val sentAt: Long,
        val phase: TurnPhase,
        val answers: List<SeatResult?> = listOf(null, null, null),
        val reviews: List<SeatResult?> = listOf(null, null, null),
        val consensus: SeatResult? = null,
        val failure: String? = null
    ) {
        val running: Boolean
            get() = phase == TurnPhase.ANSWERING || phase == TurnPhase.REVIEWING || phase == TurnPhase.JUDGING
        val answered: Int get() = answers.count { it != null }
        val reviewed: Int get() = reviews.count { it != null }
    }

    data class JudgeState(
        val seats: List<JudgeSeat?> = listOf(null, null, null),
        val judgeSeat: Int = 0,
        val turns: List<Turn> = emptyList()
    ) {
        val panelReady: Boolean get() = seats.all { it != null }
        val busy: Boolean get() = turns.any { it.running }
        val lastTurn: Turn? get() = turns.lastOrNull()
    }

    private val _state = MutableStateFlow(JudgeState())
    val state: StateFlow<JudgeState> = _state.asStateFlow()

    private var runJob: Job? = null

    val connections: StateFlow<List<ConnectionEntity>> = app.connections

    fun connectionOf(seat: JudgeSeat?): ConnectionEntity? =
        seat?.let { s -> app.connections.value.firstOrNull { it.id == s.connectionId } }

    /** Connections with at least one model, for the seat pickers. */
    fun sheetModels(): List<Pair<ConnectionEntity, List<String>>> =
        app.connections.value
            .filter { it.enabled }
            .map { conn ->
                conn to (app.repo.modelsOf(conn) + conn.activeModel.takeIf { it.isNotBlank() }.orEmpty())
                    .filter(String::isNotBlank).distinct()
            }
            .filter { it.second.isNotEmpty() }

    fun setSeat(index: Int, seat: JudgeSeat?) {
        _state.update {
            val seats = it.seats.toMutableList()
            if (index in seats.indices) seats[index] = seat
            // Keep the judge label pointing at a filled seat.
            var judge = it.judgeSeat
            if (seats.getOrElse(judge) { null } == null) {
                judge = seats.indexOfFirst { s -> s != null }.coerceAtLeast(0)
            }
            it.copy(seats = seats, judgeSeat = judge)
        }
    }

    fun setJudgeSeat(index: Int) {
        _state.update { if (it.seats.getOrNull(index) != null) it.copy(judgeSeat = index) else it }
    }

    fun clearSession() {
        if (_state.value.busy) return
        _state.update { it.copy(turns = emptyList()) }
    }

    fun cancel() {
        runJob?.cancel()
        runJob = null
        updateLast { it.copy(phase = TurnPhase.FAILED, failure = "Deliberation stopped.") }
    }

    /** Re-runs the last question (after a failure or to try again). */
    fun retryLast() {
        val last = _state.value.lastTurn ?: return
        if (_state.value.busy) return
        _state.update { it.copy(turns = it.turns.dropLast(1)) }
        ask(last.question)
    }

    /** Asks a question (or follow-up) and runs the full three-stage deliberation. */
    fun ask(text: String) {
        val question = text.trim()
        val initial = _state.value
        if (question.isEmpty() || initial.busy || !initial.panelReady) return
        val seats = initial.seats.filterNotNull()
        // Context = every earlier question and its consensus (oldest first).
        val context = initial.turns
            .filter { it.phase == TurnPhase.DONE }
            .mapNotNull { t -> t.consensus?.text?.takeIf { it.isNotBlank() }?.let { t.question to it } }
        val turn = Turn(question = question, sentAt = System.currentTimeMillis(), phase = TurnPhase.ANSWERING)
        _state.update { it.copy(turns = it.turns + turn) }

        runJob = viewModelScope.launch {
            try {
                // 1 - independent answers, in parallel; each lands as it completes.
                coroutineScope {
                    seats.mapIndexed { i, seat ->
                        async {
                            val r = call(seat, JudgePrompts.analystSystem(JudgePrompts.labels.getOrElse(i) { "${i + 1}" }), question, context)
                            updateLast { it.copy(answers = it.answers.setAt(i, r)) }
                        }
                    }.awaitAll()
                }
                updateLast { it.copy(phase = TurnPhase.REVIEWING) }

                // 2 - each seat reviews the whole panel, seeing every answer.
                val answerPairs = usable(_state.value.lastTurn?.answers ?: return@launch, seats)
                if (answerPairs.isEmpty()) {
                    updateLast {
                        it.copy(
                            phase = TurnPhase.FAILED,
                            failure = "None of the three models answered. Check the panel connections and try again."
                        )
                    }
                    return@launch
                }
                coroutineScope {
                    seats.mapIndexed { i, seat ->
                        async {
                            val r = call(
                                seat,
                                JudgePrompts.reviewSystem(JudgePrompts.labels.getOrElse(i) { "${i + 1}" }),
                                JudgePrompts.reviewUser(question, answerPairs),
                                context
                            )
                            updateLast { it.copy(reviews = it.reviews.setAt(i, r)) }
                        }
                    }.awaitAll()
                }
                updateLast { it.copy(phase = TurnPhase.JUDGING) }

                // 3 - the judge consolidates everything into the verdict.
                val reviewPairs = usable(_state.value.lastTurn?.reviews ?: return@launch, seats)
                val judgeSeat = seats.getOrNull(_state.value.judgeSeat) ?: seats.first()
                val verdict = call(
                    judgeSeat,
                    JudgePrompts.judgeSystem(),
                    JudgePrompts.judgeUser(question, answerPairs, reviewPairs),
                    context
                )
                updateLast {
                    if (verdict.error != null && verdict.text.isBlank()) {
                        it.copy(phase = TurnPhase.FAILED, consensus = verdict, failure = verdict.error)
                    } else {
                        it.copy(phase = TurnPhase.DONE, consensus = verdict)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                updateLast { it.copy(phase = TurnPhase.FAILED, failure = e.message ?: "The deliberation failed.") }
            }
        }
    }

    // ── internals ─────────────────────────────────────────────────────────────

    private suspend fun call(
        seat: JudgeSeat,
        system: String,
        userText: String,
        context: List<Pair<String, String>>
    ): SeatResult {
        val conn = app.repo.connection(seat.connectionId)
            ?: return SeatResult("", "That connection no longer exists.", 0)
        val content = if (context.isEmpty()) userText
        else JudgePrompts.contextBlock(context) + "\n\n" + userText
        return try {
            val result = app.client.complete(
                app.repo.resolveApi(conn), seat.model, listOf(WireMessage("user", content)), systemPrompt = system
            )
            SeatResult(result.text, null, result.latencyMs)
        } catch (e: Exception) {
            SeatResult("", e.message ?: "The model did not respond.", 0)
        }
    }

    /** Seat results with usable text, labelled A/B/C, failures dropped. */
    private fun usable(field: List<SeatResult?>, seats: List<JudgeSeat>): List<Pair<String, String>> =
        field.mapIndexedNotNull { i, r ->
            val text = r?.text?.takeIf { it.isNotBlank() && r.error == null } ?: return@mapIndexedNotNull null
            JudgePrompts.labels.getOrElse(i) { "${i + 1}" } to text
        }

    private fun updateLast(transform: (Turn) -> Turn) {
        _state.update { st ->
            val turns = st.turns.toMutableList()
            if (turns.isNotEmpty()) turns[turns.lastIndex] = transform(turns.last())
            st.copy(turns = turns)
        }
    }

    private fun <T> List<T?>.setAt(index: Int, value: T): List<T?> =
        mapIndexed { i, old -> if (i == index) value else old }
}
