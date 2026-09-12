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
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext

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

    /** Open a conversation: judge sessions continue in Judge Mode, voice chats in the Voice tab, text chats in Chat. */
    fun openConversation(id: String) {
        viewModelScope.launch {
            val conv = conversations.value.firstOrNull { it.id == id } ?: repo.conversation(id)
            if (conv != null && conv.kind == "JUDGE") {
                _pendingJudge.value = id
                _navigate.value = "judge"
            } else if (conv != null && conv.voice) {
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

    private val _pendingJudge = MutableStateFlow<String?>(null)
    val pendingJudge: StateFlow<String?> = _pendingJudge.asStateFlow()
    /** Returns the pending judge request once: null = none, "" = new session, else a conversation id to resume. */
    fun consumePendingJudge(): String? {
        val v = _pendingJudge.value
        _pendingJudge.value = null
        return v
    }

    fun startNewChat() {
        viewModelScope.launch {
            val conv = repo.createConversation(kind = "TEXT")
            goChat(conv.id)
        }
    }

    /** Start a fresh judge-mode chat (the Home Judge Mode card). */
    fun startJudgeChat() {
        _pendingJudge.value = ""
        _navigate.value = "judge"
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
                    "judge" -> list.filter { it.kind == "JUDGE" }
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

    /** Encrypted backup bytes staged for the SAF document launcher. */
    private val _exportBytes = MutableStateFlow<ByteArray?>(null)
    val exportBytes: StateFlow<ByteArray?> = _exportBytes.asStateFlow()
    fun consumeExportBytes() { _exportBytes.value = null }

    private val _backupPasswordSet = MutableStateFlow(settingsStore.backupPassword().isNotEmpty())
    val backupPasswordSet: StateFlow<Boolean> = _backupPasswordSet.asStateFlow()

    fun setBackupPassword(pwd: String) {
        settingsStore.setBackupPassword(pwd)
        _backupPasswordSet.value = pwd.isNotEmpty()
        refreshAutoBackupStatus()
    }

    /** Encrypts all data with the stored backup password and stages the bytes. */
    fun prepareExport() {
        viewModelScope.launch {
            _dataOp.value = DataOp.Running("Encrypting\u2026")
            try {
                val password = settingsStore.backupPassword()
                if (password.isEmpty()) {
                    throw com.mrrob.llmchat.data.Vault.VaultException(
                        "Set a backup password first - it is saved on this device and reused silently."
                    )
                }
                val json = repo.exportJson()
                val bytes = com.mrrob.llmchat.data.Vault.encrypt(json, password)
                _dataOp.value = DataOp.Idle
                _exportBytes.value = bytes
            } catch (e: com.mrrob.llmchat.data.Vault.VaultException) {
                _dataOp.value = DataOp.Done(e.message ?: "Set a backup password first.")
            } catch (e: Exception) {
                _dataOp.value = DataOp.Done("Export failed: ${e.message}")
            }
        }
    }

    /** Writes the staged bytes while the SAF write grant is definitely alive. */
    // ── Tree URI helpers (DocumentsContract's own parser rejects some picker
    //    URIs for nested folders with "Invalid URI", so we decode the raw path
    //    ourselves and talk to the provider directly) ─────────────────────────

    /** Document id of a tree URI in any shape: tree/<enc>, tree/<enc>/document/<enc>,
     *  or the OEM form with raw slashes (tree/primary%3ADocs/sub). */
    private fun treeDocId(uri: android.net.Uri): String? {
        val raw = uri.encodedPath ?: return null
        val segs = raw.trimStart('/').split('/')
        if (segs.isEmpty() || android.net.Uri.decode(segs.first()) != "tree") return null
        val docIdx = segs.indexOfFirst { android.net.Uri.decode(it) == "document" }
        val rest = if (docIdx >= 0) segs.drop(docIdx + 1) else segs.drop(1)
        if (rest.isEmpty()) return null
        return rest.joinToString("/") { android.net.Uri.decode(it) }
    }

    /**
     * Some pickers return nested-folder trees with a RAW slash
     * (tree/primary%3ADocs/sub), which DocumentsContract's parser rejects with
     * "Invalid URI". Rebuild the canonical single-segment form so every public
     * DocumentsContract helper accepts it.
     */
    private fun canonicalTreeUri(uri: android.net.Uri): android.net.Uri {
        val docId = treeDocId(uri) ?: return uri
        val enc = android.net.Uri.encode(docId)
        return android.net.Uri.parse(
            "content://${uri.authority}/tree/$enc/document/$enc"
        )
    }

    /**
     * Creates a file inside a tree folder. Tries the public DocumentsContract
     * API on the canonicalized URI; if the framework parser still chokes
     * ("Invalid URI"), calls the provider directly with manually built URIs
     * and the stable IPC string constants - mirroring what DocumentsContract
     * does internally, minus its fragile segment parsing.
     */
    private fun createInFolder(
        resolver: android.content.ContentResolver,
        treeUri: android.net.Uri,
        displayName: String
    ): android.net.Uri {
        val canonical = canonicalTreeUri(treeUri)
        val why = StringBuilder()
        runCatching {
            android.provider.DocumentsContract.createDocument(
                resolver, canonical, "application/octet-stream", displayName
            )
        }.fold(
            onSuccess = { doc -> doc?.let { return it } ?: why.append("public=null; ") },
            onFailure = { why.append("public=${it.javaClass.simpleName}:${it.message}; ") }
        )
        val docId = treeDocId(canonical)
            ?: throw IllegalStateException("bad folder URI")
        val enc = android.net.Uri.encode(docId)
        val authority = canonical.authority ?: throw IllegalStateException("bad folder URI")
        val parentDocUri = "content://$authority/document/$enc"
        val args = android.os.Bundle().apply {
            putString("android.provider.extra.PARENT_URI", parentDocUri)
            putString("display_name", displayName)
            putString("mime_type", "application/octet-stream")
        }
        runCatching {
            val result = resolver.call(
                canonical,
                "android.provider.CREATE_DOCUMENT",
                null,
                args
            )
            val created = result?.getString("android.provider.extra.RESULT")
            if (created != null) return android.net.Uri.parse(created)
            why.append("direct=null-result; ")
        }.onFailure { why.append("direct=${it.javaClass.simpleName}:${it.message}; ") }
        throw IllegalStateException(why.toString().ifBlank { "folder rejects new files" })
    }

    /** Guaranteed fallback: the system Downloads collection needs no folder grant. */
    private fun writeToDownloads(bytes: ByteArray, name: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) return false
        return runCatching {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            }
            val uri = appContext.contentResolver.insert(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return false
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: return false
            true
        }.getOrDefault(false)
    }

    /** Stores a persistable tree URI so exports never ask again - after probing it. */
    fun setExportFolder(uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            var grantNote = ""
            val outcome = runCatching {
                val grant = runCatching {
                    appContext.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                }
                if (grant.isFailure) {
                    // Some pickers allow only session grants (0x3) - the live
                    // grant still permits writes now, so probe anyway.
                    grantNote = "grant=${grant.exceptionOrNull()?.message ?: "denied"}; "
                }
                val probe = createInFolder(
                    appContext.contentResolver, uri, "aster-probe.tmp"
                )
                runCatching {
                    android.provider.DocumentsContract.deleteDocument(appContext.contentResolver, probe)
                }
                true
            }
            if (outcome.getOrDefault(false)) {
                updateSettings { it.copy(exportFolderUri = canonicalTreeUri(uri).toString()) }
                refreshAutoBackupStatus()
                _dataOp.value = DataOp.Done(
                    if (grantNote.isEmpty()) {
                        "Export folder set - backups save there automatically."
                    } else {
                        "Export folder set. This phone only grants temporary folder access, " +
                            "so re-pick it if auto backups stop working."
                    }
                )
            } else {
                // USB/SD volumes ("home:", "public:", ...) reject SAF document
                // creation on most devices - name that case explicitly.
                val volume = runCatching {
                    if (uri.authority == "com.android.externalstorage.documents")
                        (treeDocId(uri) ?: "").substringBefore(":")
                    else ""
                }.getOrDefault("")
                _dataOp.value = if (volume.isNotEmpty() && volume != "primary") {
                    DataOp.Done(
                        "USB drives and SD cards can't accept new files from apps on most phones. " +
                            "Pick a folder in phone storage, e.g. Documents - then move the backup " +
                            "to the drive with your file manager."
                    )
                } else {
                    DataOp.Done(
                        "That folder can't store files (build ${com.mrrob.llmchat.BuildConfig.VERSION_NAME}: " +
                            "$grantNote${outcome.exceptionOrNull()?.message ?: "unknown error"}). " +
                            "Pick a regular folder, e.g. Documents."
                    )
                }
            }
        }
    }

    /** Writes the staged backup straight into the saved export folder. */
    fun writeExportToFolder() {
        val bytes = _exportBytes.value ?: return
        val treeStr = settings.value.exportFolderUri
        if (treeStr.isBlank()) return
        val tree = canonicalTreeUri(android.net.Uri.parse(treeStr))
        viewModelScope.launch(Dispatchers.IO) {
            val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                .format(java.util.Date())
            val name = "aster-backup-$stamp.llm"
            val result = runCatching {
                val doc = createInFolder(appContext.contentResolver, tree, name)
                appContext.contentResolver.openOutputStream(doc)?.use { out ->
                    out.write(bytes)
                    out.flush()
                } ?: throw IllegalStateException("Could not open the file for writing.")
            }
            if (result.isSuccess) {
                _exportBytes.value = null
                _dataOp.value = DataOp.Done(
                    "Saved \"$name\" to your export folder (${bytes.size / 1024} KB, encrypted)."
                )
            } else if (writeToDownloads(bytes, name)) {
                _exportBytes.value = null
                _dataOp.value = DataOp.Done(
                    "Saved \"$name\" to Downloads (${bytes.size / 1024} KB, encrypted) - " +
                        "your phone blocked the folder."
                )
            } else {
                // Folder unusable (e.g. Downloads tree): forget it and use the save sheet instead.
                updateSettings { it.copy(exportFolderUri = "") }
                _dataOp.value = DataOp.NeedSheet(
                    "The saved folder can't store files (${result.exceptionOrNull()?.message}). " +
                        "Choose where to save this backup instead."
                )
            }
        }
    }

    fun writeExportTo(uri: android.net.Uri) {
        val bytes = _exportBytes.value ?: return
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        val result = runCatching {
            appContext.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: throw IllegalStateException("Could not open the file for writing.")
        }
        viewModelScope.launch {
            _dataOp.value = if (result.isSuccess) {
                DataOp.Done("Backup exported (${bytes.size / 1024} KB, encrypted).")
            } else {
                DataOp.Done("Export failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /** Reads a backup; asks the UI for a password when the file is a vault. */
    fun importBackup(uri: android.net.Uri, password: String) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            _dataOp.value = DataOp.Running("Importing\u2026")
            try {
                val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read the file.")
                val json = if (com.mrrob.llmchat.data.Vault.isVault(bytes)) {
                    if (password.isEmpty()) {
                        throw com.mrrob.llmchat.data.Vault.VaultException(
                            "This backup is password-protected - enter its password."
                        )
                    }
                    try {
                        com.mrrob.llmchat.data.Vault.decrypt(bytes, password)
                    } catch (ve: com.mrrob.llmchat.data.Vault.VaultException) {
                        throw ve
                    } catch (e: Exception) {
                        // Any platform crypto failure must offer another password, never dead-end.
                        throw com.mrrob.llmchat.data.Vault.VaultException(
                            "The backup could not be unlocked - check the password. " +
                                "(${e.message ?: e.javaClass.simpleName})"
                        )
                    }
                } else {
                    String(bytes, Charsets.UTF_8)
                }
                emitImport(repo.importJson(json))
            } catch (e: com.mrrob.llmchat.data.Vault.VaultException) {
                _dataOp.value = DataOp.NeedPassword(e.message ?: "Password required.")
            } catch (e: Exception) {
                _dataOp.value = DataOp.Done("Import failed: ${e.message}")
            }
        }
    }

    private fun emitImport(r: AppRepository.ImportResult) {
        _dataOp.value = when (r) {
            is AppRepository.ImportResult.Success -> DataOp.Done(
                "Imported ${r.conversations} conversation${if (r.conversations == 1) "" else "s"}" +
                    if (r.connections > 0) " and ${r.connections} connection${if (r.connections == 1) "" else "s"}." else "."
            )
            is AppRepository.ImportResult.Failure -> DataOp.Done(r.reason)
        }
    }

    /**
     * File-pick path: a vault is first tried with the device's stored backup
     * password (silent restore of our own files); the UI only prompts when
     * that fails or no password exists yet. Plain JSON imports straight away.
     */
    fun importBackupAuto(uri: android.net.Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            _dataOp.value = DataOp.Running("Importing\u2026")
            try {
                val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: throw IllegalStateException("Could not read the file.")
                val json = if (com.mrrob.llmchat.data.Vault.isVault(bytes)) {
                    val stored = settingsStore.backupPassword()
                    if (stored.isEmpty()) {
                        throw com.mrrob.llmchat.data.Vault.VaultException(
                            "This backup is password-protected - enter the password you used when exporting it."
                        )
                    }
                    try {
                        com.mrrob.llmchat.data.Vault.decrypt(bytes, stored)
                    } catch (ve: com.mrrob.llmchat.data.Vault.VaultException) {
                        throw ve
                    } catch (e: Exception) {
                        throw com.mrrob.llmchat.data.Vault.VaultException(
                            "The backup could not be unlocked with this device's password - enter its password."
                        )
                    }
                } else {
                    String(bytes, Charsets.UTF_8)
                }
                emitImport(repo.importJson(json))
            } catch (e: com.mrrob.llmchat.data.Vault.VaultException) {
                _dataOp.value = DataOp.NeedPassword(e.message ?: "Password required.")
            } catch (e: Exception) {
                _dataOp.value = DataOp.Done("Import failed: ${e.message}")
            }
        }
    }

    // ── Daily automatic backup (needs the backup password + an export folder) ──

    private val _autoBackupStatus = MutableStateFlow("")
    val autoBackupStatus: StateFlow<String> = _autoBackupStatus.asStateFlow()

    private fun refreshAutoBackupStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            _autoBackupStatus.value = autoBackupStatusNow()
        }
    }

    private fun autoBackupStatusNow(): String = when {
        settingsStore.backupPassword().isEmpty() -> "Off - set a backup password to enable."
        settings.value.exportFolderUri.isBlank() -> "On - saving daily backups to Downloads."
        else -> {
            val want = treeDocId(android.net.Uri.parse(settings.value.exportFolderUri))
            val persisted = appContext.contentResolver.persistedUriPermissions.any {
                it.isWritePermission && treeDocId(it.uri) == want
            }
            if (!persisted) {
                "On - this phone blocks permanent folder access; backups go to Downloads."
            } else {
                val last = settingsStore.prefsString("last_auto_backup")
                if (last.isEmpty()) "On - first run will create today's backup."
                else "On - last backup $last."
            }
        }
    }

    /** One encrypted snapshot per calendar day, newest 7 auto files kept. */
    fun maybeAutoBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _autoBackupStatus.value = autoBackupStatusNow()
                val password = settingsStore.backupPassword()
                if (password.isEmpty()) return@launch
                val treeStr = settings.value.exportFolderUri
                val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                    .format(java.util.Date())
                if (settingsStore.prefsString("last_auto_backup") == today) return@launch
                val json = repo.exportJson()
                val bytes = com.mrrob.llmchat.data.Vault.encrypt(json, password)
                val name = "aster-auto-$today.llm"
                val resolver = appContext.contentResolver
                val tree = if (treeStr.isNotBlank())
                    canonicalTreeUri(android.net.Uri.parse(treeStr)) else null
                val ok = (tree?.let { t ->
                    runCatching {
                        val doc = createInFolder(resolver, t, name)
                        resolver.openOutputStream(doc)?.use { out ->
                            out.write(bytes)
                            out.flush()
                        } ?: return@runCatching false
                        true
                    }.getOrDefault(false)
                } ?: false) || writeToDownloads(bytes, name)
                if (ok) {
                    val files = settingsStore.prefsString("auto_backup_files")
                        .lineSequence().filter { it.isNotBlank() }.toMutableList()
                    files += name
                    settingsStore.prefsPutString("auto_backup_files", files.joinToString("\n"))
                    settingsStore.prefsPutString("last_auto_backup", today)
                    pruneAutoBackups(tree, resolver)
                }
                _autoBackupStatus.value = autoBackupStatusNow()
                if (!ok) {
                    _autoBackupStatus.value =
                        "On - today's backup could not be written - re-pick the export folder."
                }
            } catch (e: Exception) {
                // Auto backup is silent by contract: it never dialogs, never blocks startup.
                android.util.Log.w("Aster", "auto backup failed", e)
            }
        }
    }

    private fun pruneAutoBackups(
        tree: android.net.Uri?,
        resolver: android.content.ContentResolver
    ) {
        val files = settingsStore.prefsString("auto_backup_files")
            .lineSequence().filter { it.isNotBlank() }.toMutableList()
        while (files.size > 7) {
            val old = files.removeAt(0)
            val removedFromFolder = tree?.let { t ->
                runCatching { pruneFindAndDelete(t, resolver, old) }.getOrDefault(false)
            } ?: false
            if (!removedFromFolder && android.os.Build.VERSION.SDK_INT >= 29) {
                runCatching {
                    resolver.delete(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        "display_name = ?",
                        arrayOf(old)
                    )
                }
            }
        }
        settingsStore.prefsPutString("auto_backup_files", files.joinToString("\n"))
    }

    private fun pruneFindAndDelete(
        tree: android.net.Uri,
        resolver: android.content.ContentResolver,
        old: String
    ): Boolean = runCatching {
        val children = android.provider.DocumentsContract
            .buildChildDocumentsUriUsingTree(
                tree, android.provider.DocumentsContract.getTreeDocumentId(tree)
            )
        val doc = resolver.query(
            children,
            arrayOf(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            "${android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME} = ?",
            arrayOf(old),
            null
        )?.use { c ->
            if (c.moveToFirst()) android.provider.DocumentsContract.buildDocumentUriUsingTree(
                tree, c.getString(0)
            ) else null
        }
        if (doc != null) android.provider.DocumentsContract.deleteDocument(resolver, doc)
        doc != null
    }.getOrDefault(false)

    sealed class DataOp {
        data object Idle : DataOp()
        data class Running(val label: String) : DataOp()
        data class Done(val message: String) : DataOp()
        data class NeedPassword(val reason: String) : DataOp()
        /** Folder write failed - the UI should fall back to the save-file sheet. */
        data class NeedSheet(val reason: String) : DataOp()
    }

    private val _dataOp = MutableStateFlow<DataOp>(DataOp.Idle)
    val dataOp: StateFlow<DataOp> = _dataOp.asStateFlow()
    fun clearDataOp() { _dataOp.value = DataOp.Idle }

    /** Buffer for the uri waiting on a password during import. */
    var pendingImportUri: android.net.Uri? = null

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
