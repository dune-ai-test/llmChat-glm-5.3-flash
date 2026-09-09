package com.mrrob.llmchat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mrrob.llmchat.data.ApiError
import com.mrrob.llmchat.data.ApiErrorKind
import com.mrrob.llmchat.data.AppRepository
import com.mrrob.llmchat.data.AppSettings
import com.mrrob.llmchat.data.ConnectionEntity
import com.mrrob.llmchat.data.ConversationEntity
import com.mrrob.llmchat.data.MessageEntity
import com.mrrob.llmchat.data.TestStep
import com.mrrob.llmchat.data.TestStepState
import com.mrrob.llmchat.data.WireMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which tab of the root scaffold is showing. */
enum class AsterTab { HOME, CHATS, VOICE, CONNECTIONS, SETTINGS }

/**
 * Hosts all app-global state: settings, connections, conversation lists,
 * the add-connection wizard, offline state, search and destructive-action
 * undo. Chat and voice screens use their own focused view models.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    val container: AppContainer = (app as AsterApp).container
    val repo = container.repository
    val settingsStore = container.settingsStore
    val client = container.client
    val appContext: android.content.Context = app.applicationContext

    /** Stack trace captured during the previous run, if the app crashed. */
    val lastCrash: String? = runCatching {
        java.io.File(appContext.filesDir, "crash.txt")
            .takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun clearCrashLog() {
        runCatching { java.io.File(appContext.filesDir, "crash.txt").delete() }
    }

    val settings: StateFlow<AppSettings> = settingsStore.settings

    fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)

    fun favoriteModels(): List<String> =
        settings.value.favoriteModels.lines().map(String::trim).filter(String::isNotBlank)

    fun toggleFavoriteModel(id: String) {
        settingsStore.update { st ->
            val list = st.favoriteModels.lines().map(String::trim).filter(String::isNotBlank)
            val next = if (id in list) list - id else list + id
            st.copy(favoriteModels = next.joinToString("\n"))
        }
    }

    /** Makes a connection's model the workspace default (used by the home model sheet). */
    fun setDefaultModel(connectionId: String, model: String) {
        viewModelScope.launch {
            repo.setConnectionModel(connectionId, model)
            repo.setDefaultConnection(connectionId)
        }
    }

    val connections: StateFlow<List<ConnectionEntity>> = repo.observeConnections()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val conversations: StateFlow<List<ConversationEntity>> = repo.observeConversations()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val archived: StateFlow<List<ConversationEntity>> = repo.observeArchived()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val todayMessageCount: StateFlow<Int> = repo.observeTodayCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val online: StateFlow<Boolean> = container.connectivity.observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, container.connectivity.isOnline())

    val queuedCount: StateFlow<Int> = repo.observeQueuedCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** The chat screen currently on screen, so the queue worker won't double-send. */
    var activeChatConversationId: String? = null

    init {
        // Process anything queued while offline whenever connectivity returns.
        viewModelScope.launch {
            var wasOnline = online.value
            online.collect { nowOnline ->
                if (nowOnline && !wasOnline) processQueue()
                wasOnline = nowOnline
            }
        }
        viewModelScope.launch { processQueue() }
    }

    // ── Tab + navigation intents ────────────────────────────────────────────────

    private val _tab = MutableStateFlow(AsterTab.HOME)
    val tab: StateFlow<AsterTab> = _tab.asStateFlow()

    /** One-shot route requests consumed by the root navigation host. */
    private val _navigate = MutableStateFlow<String?>(null)
    val navigate: StateFlow<String?> = _navigate.asStateFlow()
    fun consumeNavigation() { _navigate.value = null }

    private val _openConversationId = MutableStateFlow<String?>(null)
    val openConversationId: StateFlow<String?> = _openConversationId.asStateFlow()
    fun consumeOpenConversation() { _openConversationId.value = null }

    fun selectTab(tab: AsterTab) { _tab.value = tab }
    fun goChat(conversationId: String) { _openConversationId.value = conversationId }

    /** Open a conversation: voice chats continue in the Voice tab, text chats in Chat. */
    fun openConversation(id: String) {
        viewModelScope.launch {
            val conv = conversations.value.firstOrNull { it.id == id } ?: repo.conversation(id)
            if (conv != null && conv.voice) {
                _tab.value = AsterTab.VOICE
                _pendingVoice.value = id
            } else {
                goChat(id)
            }
        }
    }

    private val _pendingVoice = MutableStateFlow<String?>(null)
    val pendingVoice: StateFlow<String?> = _pendingVoice.asStateFlow()
    fun consumePendingVoice() { _pendingVoice.value = null }

    fun startNewChat() {
        viewModelScope.launch {
            val conv = repo.createConversation(kind = "TEXT")
            goChat(conv.id)
        }
    }

    /** Start a fresh voice session conversation. */
    fun startVoiceConversation() {
        _tab.value = AsterTab.VOICE
    }

    // ── Connection wizard ───────────────────────────────────────────────────────

    data class WizardState(
        val provider: String = "OPENAI",
        val name: String = "OpenAI",
        val baseUrl: String = "https://api.openai.com",
        val apiKey: String = "",
        val fetchedModels: List<String> = emptyList(),
        val selectedModels: List<String> = emptyList(),
        val activeModel: String = "",
        val manualModel: String = "",
        val fetchingModels: Boolean = false,
        val modelsError: String? = null,
        val testRunning: Boolean = false,
        val steps: Map<TestStep, TestStepState> = emptyMap(),
        val stepDetails: Map<TestStep, String> = emptyMap(),
        val testError: ApiError? = null,
        val latencyMs: Long = 0,
        val streamingOk: Boolean = false,
        val setAsDefault: Boolean = true,
        val saved: Boolean = false,
        val hasExistingKey: Boolean = false,
        val editingConnectionId: String? = null
    ) {
        val models: List<String> get() = (selectedModels + activeModel.takeIf { it.isNotBlank() }.orEmpty()).distinct()
    }

    private val _wizard = MutableStateFlow(WizardState())
    val wizard: StateFlow<WizardState> = _wizard.asStateFlow()

    fun wizardState(update: (WizardState) -> WizardState) {
        _wizard.value = update(_wizard.value)
    }

    /** The key to use for live calls: freshly typed, else the encrypted saved one. */
    private fun effectiveKey(w: WizardState): String =
        w.apiKey.ifBlank { w.editingConnectionId?.let { settingsStore.apiKey(it) } ?: "" }

    fun startWizard() {
        _wizard.value = WizardState()
        _navigate.value = "wizard/provider"
    }

    private fun providerDefaults(provider: String): Triple<String, String, String> = when (provider) {
        "OPENAI" -> Triple("OpenAI", "https://api.openai.com", "gpt-5")
        "OPENAI_COMPATIBLE" -> Triple("OpenAI Compatible", "https://", "")
        "LOCAL_SERVER" -> Triple("Local Server", "http://192.168.1.42:8080", "")
        else -> Triple("Custom API", "https://", "")
    }

    fun wizardChooseProvider(provider: String) {
        val (name, url, model) = providerDefaults(provider)
        _wizard.update {
            it.copy(
                provider = provider,
                name = if (it.editingConnectionId == null) name else it.name,
                baseUrl = if (it.editingConnectionId == null) url else it.baseUrl,
                activeModel = if (it.editingConnectionId == null) model else it.activeModel,
                fetchedModels = it.fetchedModels.ifEmpty { settingsStore.cachedModels(url) }
            )
        }
    }

    /** When the URL changes, restore any model list previously fetched for that server. */
    fun wizardUrlChanged(url: String) {
        _wizard.update {
            it.copy(baseUrl = url, fetchedModels = settingsStore.cachedModels(url).ifEmpty { it.fetchedModels })
        }
    }

    fun wizardToggleModel(model: String) {
        _wizard.update { st ->
            val next = if (model in st.selectedModels) st.selectedModels - model
            else (st.selectedModels + model).distinct()
            var act = st.activeModel
            if (model in next && act.isBlank()) act = model
            if (act.isNotEmpty() && act !in next) act = next.firstOrNull().orEmpty()
            st.copy(
                selectedModels = next,
                activeModel = act,
                fetchedModels = if (model in st.fetchedModels) st.fetchedModels else st.fetchedModels + model
            )
        }
    }

    fun wizardSetDefaultModel(model: String) {
        _wizard.update {
            it.copy(
                activeModel = model,
                selectedModels = (it.selectedModels + model).distinct(),
                fetchedModels = if (model in it.fetchedModels) it.fetchedModels else it.fetchedModels + model
            )
        }
    }

    /** Adds a typed or picked model: marks it selected and active if none set. */
    fun wizardAddModel(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        _wizard.update {
            it.copy(
                fetchedModels = (it.fetchedModels + clean).distinct(),
                selectedModels = (it.selectedModels + clean).distinct(),
                activeModel = it.activeModel.ifBlank { clean }
            )
        }
    }

    fun wizardFetchModels() {
        val w = _wizard.value
        if (w.baseUrl.isBlank()) {
            _wizard.update { it.copy(modelsError = "Enter a base URL first.") }
            return
        }
        _wizard.update { it.copy(fetchingModels = true, modelsError = null) }
        viewModelScope.launch {
            try {
                val models = client.listModels(tempApi(w))
                settingsStore.setCachedModels(w.baseUrl, models)
                _wizard.update {
                    it.copy(
                        fetchingModels = false,
                        fetchedModels = models,
                        activeModel = it.activeModel.ifBlank { models.firstOrNull().orEmpty() }
                    )
                }
            } catch (e: Exception) {
                _wizard.update { it.copy(fetchingModels = false, modelsError = e.message ?: "Could not load models.") }
            }
        }
    }

    private fun tempApi(w: WizardState) = com.mrrob.llmchat.data.ResolvedApi(
        name = w.name,
        baseUrl = w.baseUrl,
        apiKey = effectiveKey(w),
        endpointPath = settingsStore.settings.value.endpointPath
    )

    fun wizardRunTest() {
        val w = _wizard.value
        if (w.testRunning) return
        _wizard.update {
            it.copy(
                testRunning = true,
                testError = null,
                steps = TestStep.entries.associateWith { TestStepState.RUNNING },
                stepDetails = emptyMap()
            )
        }
        viewModelScope.launch {
            try {
                val model = w.activeModel.ifBlank { w.selectedModels.firstOrNull().orEmpty() }
                val outcome = client.test(tempApi(w), model) { step, state, detail ->
                    _wizard.update {
                        it.copy(
                            steps = it.steps + (step to state),
                            stepDetails = it.stepDetails + (step to (detail ?: it.stepDetails[step].orEmpty()))
                        )
                    }
                }
                _wizard.update {
                    it.copy(
                        testRunning = false,
                        latencyMs = outcome.latencyMs,
                        streamingOk = true,
                        fetchedModels = outcome.models,
                        steps = it.steps.mapValues { TestStepState.DONE }
                    )
                }
                settingsStore.setCachedModels(w.baseUrl, outcome.models)
            } catch (e: ApiError) {
                _wizard.update { it.copy(testRunning = false, testError = e) }
            } catch (e: Exception) {
                _wizard.update {
                    it.copy(testRunning = false, testError = ApiError(ApiErrorKind.NETWORK, e.message ?: "Connection failed."))
                }
            }
        }
    }

    suspend fun wizardSaveAndFinish() {
        val w = _wizard.value
        val models = (w.selectedModels + w.activeModel.takeIf { it.isNotBlank() }.orEmpty()).distinct()
        val saved = repo.saveConnection(
            id = w.editingConnectionId,
            name = w.name.ifBlank { "New connection" },
            provider = w.provider,
            baseUrl = w.baseUrl,
            models = models.ifEmpty { listOf(w.activeModel).filter(String::isNotBlank) },
            activeModel = w.activeModel,
            apiKey = w.apiKey,
            setAsDefault = w.setAsDefault
        )
        val fullyTested = w.steps.isNotEmpty() && w.steps.values.all { it == TestStepState.DONE }
        repo.setConnectionStatus(
            saved.id,
            when {
                fullyTested && w.testError == null -> "CONNECTED"
                w.testError != null -> "OFFLINE"
                else -> "UNKNOWN" // saved without a connection test
            }
        )
        settingsStore.update { it.copy(onboardingDone = true) }
        _wizard.update { it.copy(saved = true, editingConnectionId = saved.id) }
    }

    fun startEditConnection(connection: ConnectionEntity) {
        val savedKey = settingsStore.apiKey(connection.id)
        val models = (repo.modelsOf(connection) + settingsStore.cachedModels(connection.baseUrl)).distinct()
        _wizard.value = WizardState(
            provider = connection.provider,
            name = connection.name,
            baseUrl = connection.baseUrl,
            apiKey = "", // the saved key is never shown — blank means "keep the stored one"
            fetchedModels = models,
            selectedModels = repo.modelsOf(connection),
            activeModel = connection.activeModel,
            setAsDefault = connection.isDefault,
            hasExistingKey = savedKey.isNotBlank(),
            editingConnectionId = connection.id
        )
        _navigate.value = "wizard/config"
    }

    fun resetWizard() { _wizard.value = WizardState() }

    // ── Connection quick actions ────────────────────────────────────────────────

    fun testExistingConnection(connection: ConnectionEntity) {
        viewModelScope.launch {
            repo.setConnectionStatus(connection.id, "UNKNOWN")
            val result = runCatching { client.test(repo.resolveApi(connection), connection.activeModel) { _, _, _ -> } }
            repo.setConnectionStatus(connection.id, if (result.isSuccess) "CONNECTED" else "OFFLINE")
        }
    }

    fun deleteConnection(connection: ConnectionEntity) {
        viewModelScope.launch { repo.deleteConnection(connection) }
    }

    fun duplicateConnection(connection: ConnectionEntity) {
        viewModelScope.launch { repo.duplicateConnection(connection) }
    }

    fun renameConnection(connection: ConnectionEntity, name: String) {
        viewModelScope.launch { repo.renameConnection(connection.id, name) }
    }

    fun setDefaultConnection(id: String) {
        viewModelScope.launch { repo.setDefaultConnection(id) }
    }

    fun toggleConnectionEnabled(connection: ConnectionEntity, enabled: Boolean) {
        viewModelScope.launch { repo.setConnectionEnabled(connection.id, enabled) }
    }

    fun setConnectionModel(connection: ConnectionEntity, model: String) {
        viewModelScope.launch { repo.setConnectionModel(connection.id, model) }
    }

    // ── Conversations ───────────────────────────────────────────────────────────

    fun renameConversation(id: String, title: String) {
        viewModelScope.launch { repo.renameConversation(id, title) }
    }

    fun togglePin(id: String) {
        viewModelScope.launch {
            repo.conversation(id)?.let { repo.setPinned(id, !it.pinned) }
        }
    }

    fun toggleStar(id: String) {
        viewModelScope.launch {
            repo.conversation(id)?.let { repo.setStarred(id, !it.starred) }
        }
    }

    fun toggleArchive(id: String) {
        viewModelScope.launch {
            repo.conversation(id)?.let { repo.setArchived(id, !it.archived) }
        }
    }

    fun duplicateConversation(id: String) {
        viewModelScope.launch { repo.duplicateConversation(id) }
    }

    data class UndoState(val conversation: ConversationEntity, val messages: List<MessageEntity>)

    private val _undo = MutableStateFlow<UndoState?>(null)
    val undo: StateFlow<UndoState?> = _undo.asStateFlow()

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            val conv = repo.conversation(id) ?: return@launch
            val messages = repo.messagesOf(id)
            repo.deleteConversation(conv)
            _undo.value = UndoState(conv, messages)
            launch {
                delay(6000)
                if (_undo.value?.conversation?.id == id) _undo.value = null
            }
        }
    }

    fun undoDelete() {
        val snapshot = _undo.value ?: return
        viewModelScope.launch {
            repo.restoreConversation(snapshot.conversation, snapshot.messages)
            _undo.value = null
        }
    }

    // ── Search ──────────────────────────────────────────────────────────────────

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow("all")
    val filter: StateFlow<String> = _filter.asStateFlow()

    private val _searchResults = MutableStateFlow<List<ConversationEntity>>(emptyList())
    val searchResults: StateFlow<List<ConversationEntity>> = _searchResults.asStateFlow()

    private var searchJob: Job? = null

    fun setQuery(q: String) {
        _query.value = q
        runSearch(q, _filter.value)
    }

    fun setFilter(f: String) {
        _filter.value = f
        runSearch(_query.value, f)
    }

    private fun runSearch(q: String, filter: String) {
        searchJob?.cancel()
        if (q.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            repo.searchConversations(q.trim()).collect { list ->
                _searchResults.value = when (filter) {
                    "voice" -> list.filter { it.voice }
                    "starred" -> list.filter { it.starred }
                    else -> list
                }
            }
        }
    }

    fun recentSearches(): List<String> =
        settingsStore.settings.value.recentSearches
            .split('\n')
            .map(String::trim)
            .filter(String::isNotBlank)
            .take(6)

    fun recordRecentSearch(q: String) {
        val trimmed = q.trim()
        if (trimmed.isBlank()) return
        val list = (listOf(trimmed) + recentSearches()).distinct().take(6)
        settingsStore.update { it.copy(recentSearches = list.joinToString("\n")) }
    }

    fun clearRecentSearches() {
        settingsStore.update { it.copy(recentSearches = "") }
    }

    // ── Offline queue ───────────────────────────────────────────────────────────

    private var queueWorkerActive = false

    private suspend fun processQueue() {
        if (queueWorkerActive) return
        queueWorkerActive = true
        try {
            if (!container.connectivity.isOnline()) return
            val queued = repo.queuedMessages()
            for (message in queued) {
                if (message.conversationId == activeChatConversationId) continue
                val conv = repo.conversation(message.conversationId) ?: continue
                val connection = repo.connection(conv.connectionId) ?: continue
                if (!connection.enabled) continue
                val history = repo.messagesOf(conv.id).takeWhile { it.id != message.id }
                val wire = repo.wireMessages(history) + WireMessage("user", message.text)
                try {
                    repo.updateMessage(message.copy(status = "OK"))
                    val result = client.complete(
                        repo.resolveApi(connection),
                        conv.model.ifBlank { connection.activeModel },
                        wire,
                        systemPrompt = conv.systemPrompt
                    )
                    repo.insertMessage(
                        MessageEntity(
                            conversationId = conv.id,
                            role = "assistant",
                            text = result.text,
                            model = conv.model.ifBlank { connection.activeModel },
                            latencyMs = result.latencyMs,
                            tokensIn = result.tokensIn,
                            tokensOut = result.tokensOut
                        )
                    )
                    repo.touchConversation(conv.id, message.text.take(80))
                    autoTitleIfNeeded(conv, message.text, result.text)
                } catch (e: ApiError) {
                    val retryable = e.kind in setOf(
                        ApiErrorKind.NETWORK, ApiErrorKind.TIMEOUT,
                        ApiErrorKind.SERVER, ApiErrorKind.RATE_LIMIT, ApiErrorKind.OFFLINE
                    )
                    if (retryable) {
                        repo.updateMessage(message.copy(status = "QUEUED"))
                    } else {
                        repo.updateMessage(message.copy(status = "FAILED"))
                        repo.insertMessage(
                            MessageEntity(
                                conversationId = conv.id,
                                role = "system",
                                text = e.message
                            )
                        )
                        repo.touchConversation(conv.id, e.message)
                    }
                } catch (_: Exception) {
                    repo.updateMessage(message.copy(status = "FAILED"))
                }
            }
        } finally {
            queueWorkerActive = false
        }
    }

    private suspend fun autoTitleIfNeeded(conv: ConversationEntity, userText: String, assistantText: String) {
        val s = settingsStore.settings.value
        if (!s.autoTitle || conv.title != "New conversation") return
        val title = repo.generateTitle(
            repo.connection(conv.connectionId) ?: return,
            conv.model, userText, assistantText
        )
        repo.renameConversation(conv.id, title)
    }

    // ── Data controls ───────────────────────────────────────────────────────────

    suspend fun exportAllData(): String = repo.exportJson()

    suspend fun importData(raw: String): AppRepository.ImportResult = repo.importJson(raw)

    fun clearAllData() {
        viewModelScope.launch {
            container.database.clearAllTables()
            settingsStore.clearAll()
            _undo.value = null
        }
    }

    fun resetSettings() {
        viewModelScope.launch {
            val keep = settingsStore.settings.value.onboardingDone
            settingsStore.clearAll()
            settingsStore.update { it.copy(onboardingDone = keep) }
        }
    }

    companion object {
        fun factory(app: Application) = object : ViewModelProvider.AndroidViewModelFactory(app) {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                AppViewModel(app) as T
        }
    }
}
