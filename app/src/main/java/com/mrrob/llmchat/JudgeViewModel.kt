package com.mrrob.llmchat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mrrob.llmchat.data.ConnectionEntity
import com.mrrob.llmchat.data.JudgePrompts
import com.mrrob.llmchat.data.MessageEntity
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
import org.json.JSONArray
import org.json.JSONObject

/** One seat of the panel: a model on a specific connection. */
data class JudgeSeat(val connectionId: String, val model: String)

/** How far a single deliberation has got. */
enum class TurnPhase { ANSWERING, REVIEWING, JUDGING, DONE, FAILED }

/**
 * Runs Judge Mode sessions: three models answer the same question
 * independently, each then peer-reviews the whole panel, and the chosen
 * judge model consolidates everything into one Consensus Answer. Follow-ups
 * re-run the same pipeline with the earlier consensus as context.
 *
 * Sessions are persisted as a `kind = "JUDGE"` conversation: one `system`
 * message carrying the panel, and one `judge` message per turn holding the
 * full deliberation as JSON - so chats/recents, search and backups all pick
 * them up like any other conversation.
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
        val failure: String? = null,
        val messageId: Long = 0
    ) {
        val running: Boolean
            get() = phase == TurnPhase.ANSWERING || phase == TurnPhase.REVIEWING || phase == TurnPhase.JUDGING
        val answered: Int get() = answers.count { it != null }
        val reviewed: Int get() = reviews.count { it != null }
    }

    data class JudgeState(
        val seats: List<JudgeSeat?> = listOf(null, null, null),
        val judgeSeat: Int = 0,
        val turns: List<Turn> = emptyList(),
        val conversationId: String? = null,
        val panelMessageId: Long = 0
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
        persistPanel()
    }

    fun setJudgeSeat(index: Int) {
        _state.update { if (it.seats.getOrNull(index) != null) it.copy(judgeSeat = index) else it }
        persistPanel()
    }

    /** Drop the current session and start a fresh one (panel kept for convenience). */
    fun newSession() {
        if (_state.value.busy) cancel()
        _state.update { it.copy(turns = emptyList(), conversationId = null, panelMessageId = 0) }
    }

    /** Load a saved judge conversation from the database. */
    fun resume(id: String) {
        if (_state.value.conversationId == id) return
        viewModelScope.launch {
            val conv = app.repo.conversation(id) ?: return@launch
            val msgs = app.repo.messagesOf(id)
            var seats = listOf<JudgeSeat?>(null, null, null)
            var judge = 0
            var panelId = 0L
            msgs.firstOrNull { it.role == "system" }?.let { m ->
                parsePanel(m.text)?.let { seats = it.first; judge = it.second }
                panelId = m.id
            }
            val turns = msgs.filter { it.role == "judge" }.mapNotNull { turnFromJson(it.text, it.id) }
            // A deliberation interrupted by an app restart becomes a failed partial.
            val fixed = turns.map {
                if (it.running) it.copy(phase = TurnPhase.FAILED, failure = "Deliberation was interrupted.") else it
            }
            _state.value = JudgeState(
                seats = seats, judgeSeat = judge, turns = fixed,
                conversationId = conv.id, panelMessageId = panelId
            )
            fixed.zip(turns).forEach { (f, orig) ->
                if (f !== orig && f.messageId != 0L) {
                    app.repo.messageById(f.messageId)?.let {
                        app.repo.updateMessage(it.copy(text = turnJson(f)))
                    }
                }
            }
        }
    }

    fun cancel() {
        runJob?.cancel()
        runJob = null
        viewModelScope.launch {
            updateLast { it.copy(phase = TurnPhase.FAILED, failure = "Deliberation stopped.") }
        }
    }

    /** Re-runs the last question (after a failure or to try again). */
    fun retryLast() {
        val last = _state.value.lastTurn ?: return
        if (_state.value.busy) return
        viewModelScope.launch {
            if (last.messageId != 0L) {
                app.repo.messageById(last.messageId)?.let { app.repo.deleteMessage(it) }
            }
            _state.update { it.copy(turns = it.turns.dropLast(1)) }
            ask(last.question)
        }
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
        runJob = viewModelScope.launch {
            val convId = ensureConversation(question)
            val turn = Turn(question = question, sentAt = System.currentTimeMillis(), phase = TurnPhase.ANSWERING)
            _state.update { it.copy(turns = it.turns + turn) }
            val messageId = app.repo.insertMessage(
                MessageEntity(conversationId = convId, role = "judge", text = turnJson(turn), createdAt = turn.sentAt)
            )
            _state.update { st ->
                st.copy(turns = st.turns.mapIndexed { i, t -> if (i == st.turns.lastIndex) t.copy(messageId = messageId) else t })
            }
            app.repo.touchConversation(convId, question.take(80))
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
                val answerPairs = usable(_state.value.lastTurn?.answers ?: return@launch)
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
                val reviewPairs = usable(_state.value.lastTurn?.reviews ?: return@launch)
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
                if (_state.value.lastTurn?.phase == TurnPhase.DONE) {
                    convId.let { app.repo.touchConversation(it, "Consensus: ${verdict.text.take(70)}") }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                updateLast { it.copy(phase = TurnPhase.FAILED, failure = e.message ?: "The deliberation failed.") }
            }
        }
    }

    // ── persistence ──────────────────────────────────────────────────────────

    /** Creates the JUDGE conversation + panel row on the first question. */
    private suspend fun ensureConversation(question: String): String {
        _state.value.conversationId?.let { return it }
        val conv = app.repo.createConversation(
            kind = "JUDGE",
            title = question.take(48).ifBlank { "Judge session" }
        )
        val panelId = app.repo.insertMessage(
            MessageEntity(conversationId = conv.id, role = "system", text = panelJson(_state.value))
        )
        _state.update { it.copy(conversationId = conv.id, panelMessageId = panelId) }
        return conv.id
    }

    /** Keeps the panel row current when seats change mid-session. */
    private fun persistPanel() {
        viewModelScope.launch {
            val st = _state.value
            val convId = st.conversationId ?: return@launch
            if (st.panelMessageId == 0L) {
                val id = app.repo.insertMessage(
                    MessageEntity(conversationId = convId, role = "system", text = panelJson(st))
                )
                _state.update { it.copy(panelMessageId = id) }
            } else {
                app.repo.messageById(st.panelMessageId)?.let {
                    app.repo.updateMessage(it.copy(text = panelJson(_state.value)))
                }
            }
        }
    }

    /** Updates the state and re-writes the turn's row so a crash never loses progress. */
    private suspend fun updateLast(transform: (Turn) -> Turn) {
        _state.update { st ->
            val turns = st.turns.toMutableList()
            if (turns.isNotEmpty()) turns[turns.lastIndex] = transform(turns.last())
            st.copy(turns = turns)
        }
        val st = _state.value
        val turn = st.lastTurn ?: return
        val convId = st.conversationId ?: return
        if (turn.messageId == 0L) return
        app.repo.messageById(turn.messageId)?.let {
            app.repo.updateMessage(it.copy(text = turnJson(turn)))
        }
    }

    private fun panelJson(st: JudgeState): String = JSONObject().apply {
        put("seats", JSONArray().apply {
            st.seats.forEach { s ->
                put(s?.let { JSONObject().put("c", it.connectionId).put("m", it.model) } ?: JSONObject.NULL)
            }
        })
        put("judge", st.judgeSeat)
    }.toString()

    /** @return (seats, judgeSeat) or null when the row isn't a panel. */
    private fun parsePanel(raw: String): Pair<List<JudgeSeat?>, Int>? = try {
        val o = JSONObject(raw)
        val arr = o.optJSONArray("seats") ?: return null
        val seats = (0 until arr.length()).map { i ->
            (arr.opt(i) as? JSONObject)?.let { JudgeSeat(it.optString("c"), it.optString("m")) }
        }
        seats to o.optInt("judge", 0)
    } catch (_: Exception) {
        null
    }

    private fun turnJson(t: Turn): String = JSONObject().apply {
        put("question", t.question)
        put("sentAt", t.sentAt)
        put("phase", t.phase.name)
        put("answers", JSONArray().apply { t.answers.forEach { put(seatResultJson(it)) } })
        put("reviews", JSONArray().apply { t.reviews.forEach { put(seatResultJson(it)) } })
        put("consensus", seatResultJson(t.consensus))
        put("failure", t.failure ?: JSONObject.NULL)
    }.toString()

    private fun seatResultJson(r: SeatResult?): Any =
        r?.let {
            JSONObject().put("text", it.text).put("error", it.error ?: JSONObject.NULL).put("latencyMs", it.latencyMs)
        } ?: JSONObject.NULL

    private fun parseSeatResult(o: Any?): SeatResult? = (o as? JSONObject)?.let {
        SeatResult(
            text = it.optString("text"),
            error = if (it.isNull("error")) null else it.optString("error").takeIf { e -> e.isNotBlank() },
            latencyMs = it.optLong("latencyMs")
        )
    }

    private fun turnFromJson(raw: String, messageId: Long): Turn? = try {
        val o = JSONObject(raw)
        fun list(key: String): List<SeatResult?> {
            val arr = o.optJSONArray(key) ?: return listOf(null, null, null)
            return (0 until arr.length()).map { parseSeatResult(arr.opt(it)) }
        }
        Turn(
            question = o.optString("question"),
            sentAt = o.optLong("sentAt"),
            phase = runCatching { TurnPhase.valueOf(o.optString("phase")) }.getOrDefault(TurnPhase.DONE),
            answers = list("answers"),
            reviews = list("reviews"),
            consensus = parseSeatResult(o.opt("consensus")),
            failure = o.optString("failure").takeIf { it.isNotBlank() },
            messageId = messageId
        )
    } catch (_: Exception) {
        null
    }

    // ── deliberation internals ────────────────────────────────────────────────

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
    private fun usable(field: List<SeatResult?>): List<Pair<String, String>> =
        field.mapIndexedNotNull { i, r ->
            val text = r?.text?.takeIf { it.isNotBlank() && r.error == null } ?: return@mapIndexedNotNull null
            JudgePrompts.labels.getOrElse(i) { "${i + 1}" } to text
        }

    private fun <T> List<T?>.setAt(index: Int, value: T): List<T?> =
        mapIndexed { i, old -> if (i == index) value else old }
}
