package com.mrrob.llmchat.data

import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Single source of truth over Room + the encrypted key store. All screens
 * and view models read/write through this.
 */
class AppRepository(
    private val db: AsterDatabase,
    val settingsStore: SettingsStore,
    val client: LlmClient
) {
    private val connectionDao = db.connections()
    private val conversationDao = db.conversations()
    private val messageDao = db.messages()

    // ── Connections ─────────────────────────────────────────────────────────────

    fun observeConnections(): Flow<List<ConnectionEntity>> = connectionDao.observeAll()
    suspend fun connection(id: String): ConnectionEntity? = connectionDao.byId(id)
    suspend fun defaultConnection(): ConnectionEntity? = connectionDao.default()
    private suspend fun allConnections(): List<ConnectionEntity> = connectionDao.allOnce()

    fun modelsOf(connection: ConnectionEntity): List<String> =
        try {
            val arr = JSONArray(connection.modelsJson)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            if (connection.activeModel.isNotBlank()) listOf(connection.activeModel) else emptyList()
        }

    /** Per-connection custom headers kept in (unencrypted) prefs as JSON. */
    fun customHeadersOf(connection: ConnectionEntity): Map<String, String> =
        try {
            val obj = JSONObject(settingsStore.prefsString("headers_${connection.id}", "{}"))
            val result = LinkedHashMap<String, String>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = obj.getString(k)
            }
            result
        } catch (_: Exception) {
            emptyMap()
        }

    fun setHeaders(connectionId: String, json: String) =
        settingsStore.prefsPutString("headers_$connectionId", json)

    fun apiKeyOf(connectionId: String): String = settingsStore.apiKey(connectionId)

    suspend fun saveConnection(
        id: String? = null,
        name: String,
        provider: String,
        baseUrl: String,
        models: List<String>,
        activeModel: String,
        apiKey: String,
        setAsDefault: Boolean = false
    ): ConnectionEntity {
        val existing = id?.let { connectionDao.byId(it) }
        val entity = (existing ?: ConnectionEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            provider = provider,
            baseUrl = baseUrl,
            sortOrder = connectionDao.nextSort()
        )).copy(
            name = name,
            provider = provider,
            baseUrl = baseUrl,
            modelsJson = JSONArray(models).toString(),
            activeModel = activeModel,
            isDefault = setAsDefault || (existing?.isDefault ?: false)
        )
        if (apiKey.isNotBlank()) settingsStore.setApiKey(entity.id, apiKey)
        connectionDao.upsert(entity)
        if (setAsDefault || existing == null) makeSureOneDefault(entity.id)
        return entity
    }

    suspend fun deleteConnection(connection: ConnectionEntity) {
        connectionDao.delete(connection)
        settingsStore.removeApiKey(connection.id)
        settingsStore.prefsPutString("headers_${connection.id}", "{}")
        makeSureOneDefault(null)
    }

    suspend fun duplicateConnection(source: ConnectionEntity) {
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            name = "${source.name} copy",
            isDefault = false,
            sortOrder = connectionDao.nextSort(),
            createdAt = System.currentTimeMillis()
        )
        connectionDao.upsert(copy)
        settingsStore.setApiKey(copy.id, settingsStore.apiKey(source.id))
        settingsStore.prefsPutString("headers_${copy.id}", settingsStore.prefsString("headers_${source.id}", "{}"))
    }

    suspend fun setDefaultConnection(id: String) = makeSureOneDefault(id)

    private suspend fun makeSureOneDefault(preferredId: String?) {
        val list = allConnections()
        val chosen = preferredId ?: list.firstOrNull { it.isDefault }?.id ?: list.firstOrNull()?.id
        list.forEach { conn ->
            val wanted = conn.id == chosen
            if (conn.isDefault != wanted) connectionDao.upsert(conn.copy(isDefault = wanted))
        }
    }

    suspend fun renameConnection(id: String, name: String) {
        connectionDao.byId(id)?.let { connectionDao.upsert(it.copy(name = name)) }
    }

    suspend fun setConnectionEnabled(id: String, enabled: Boolean) {
        connectionDao.byId(id)?.let { connectionDao.upsert(it.copy(enabled = enabled)) }
    }

    suspend fun setConnectionStatus(id: String, status: String) {
        connectionDao.byId(id)?.let { connectionDao.upsert(it.copy(status = status)) }
    }

    suspend fun setConnectionModel(id: String, model: String) {
        connectionDao.byId(id)?.let { connectionDao.upsert(it.copy(activeModel = model)) }
    }

    fun resolveApi(connection: ConnectionEntity): ResolvedApi = ResolvedApi(
        name = connection.name,
        baseUrl = connection.baseUrl,
        apiKey = settingsStore.apiKey(connection.id),
        endpointPath = settingsStore.settings.value.endpointPath,
        customHeaders = customHeadersOf(connection),
        apiVersion = settingsStore.settings.value.apiVersion
    )

    // ── Conversations ───────────────────────────────────────────────────────────

    fun observeConversations(): Flow<List<ConversationEntity>> = conversationDao.observeAll()
    fun observeArchived(): Flow<List<ConversationEntity>> = conversationDao.observeArchived()
    suspend fun conversation(id: String): ConversationEntity? = conversationDao.byId(id)

    fun searchConversations(query: String): Flow<List<ConversationEntity>> =
        conversationDao.search(query)

    suspend fun createConversation(
        kind: String = "TEXT",
        title: String = "New conversation",
        connectionId: String = "",
        model: String = "",
        systemPrompt: String = "",
        branchOf: String = ""
    ): ConversationEntity {
        val resolvedConnection = connectionId.ifBlank { defaultConnection()?.id.orEmpty() }
        val resolvedModel = model.ifBlank { defaultConnection()?.activeModel.orEmpty() }
        val entity = ConversationEntity(
            title = title,
            connectionId = resolvedConnection,
            model = resolvedModel,
            systemPrompt = systemPrompt,
            kind = kind,
            voice = kind == "VOICE",
            branchOf = branchOf
        )
        conversationDao.upsert(entity)
        return entity
    }

    suspend fun updateConversation(conversation: ConversationEntity) =
        conversationDao.update(conversation)

    suspend fun touchConversation(id: String, preview: String) {
        conversationDao.byId(id)?.let {
            conversationDao.update(it.copy(updatedAt = System.currentTimeMillis(), lastPreview = preview))
        }
    }

    suspend fun renameConversation(id: String, title: String) {
        conversationDao.byId(id)?.let { conversationDao.update(it.copy(title = title)) }
    }

    suspend fun setPinned(id: String, pinned: Boolean) {
        conversationDao.byId(id)?.let { conversationDao.update(it.copy(pinned = pinned)) }
    }

    suspend fun setStarred(id: String, starred: Boolean) {
        conversationDao.byId(id)?.let { conversationDao.update(it.copy(starred = starred)) }
    }

    suspend fun setArchived(id: String, archived: Boolean) {
        conversationDao.byId(id)?.let { conversationDao.update(it.copy(archived = archived)) }
    }

    suspend fun deleteConversation(conversation: ConversationEntity) =
        conversationDao.delete(conversation)

    suspend fun restoreConversation(conversation: ConversationEntity, messages: List<MessageEntity>) {
        conversationDao.upsert(conversation)
        messages.forEach { messageDao.insert(it.copy(id = 0)) }
    }

    suspend fun duplicateConversation(id: String): String? {
        val source = conversationDao.byId(id) ?: return null
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            title = "${source.title} copy",
            pinned = false,
            updatedAt = System.currentTimeMillis()
        )
        conversationDao.upsert(copy)
        messageDao.listFor(id).forEach { messageDao.insert(it.copy(id = 0, conversationId = copy.id)) }
        return copy.id
    }

    /** Edit-and-continue branching: copy messages before the edited one, re-queue it. */
    suspend fun createBranch(
        sourceId: String,
        uptoMessageId: Long,
        editedText: String
    ): ConversationEntity? {
        val source = conversationDao.byId(sourceId) ?: return null
        val branch = source.copy(
            id = UUID.randomUUID().toString(),
            title = "${source.title} — Branch 1",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            pinned = false,
            starred = false,
            archived = false,
            branchOf = sourceId,
            draft = "",
            lastPreview = editedText.take(80)
        )
        conversationDao.upsert(branch)
        val original = messageDao.listFor(sourceId)
        val kept = original.takeWhile { it.id != uptoMessageId }
        kept.forEach { messageDao.insert(it.copy(id = 0, conversationId = branch.id)) }
        messageDao.insert(
            MessageEntity(
                conversationId = branch.id,
                role = "user",
                text = editedText,
                model = source.model,
                status = "QUEUED"
            )
        )
        return branch
    }

    suspend fun setDraft(conversationId: String, draft: String) {
        conversationDao.byId(conversationId)?.let { conversationDao.update(it.copy(draft = draft)) }
    }

    // ── Messages ────────────────────────────────────────────────────────────────

    fun observeMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.observeFor(conversationId)

    suspend fun messagesOf(conversationId: String): List<MessageEntity> = messageDao.listFor(conversationId)
    suspend fun messageById(id: Long): MessageEntity? = messageDao.byId(id)

    suspend fun insertMessage(message: MessageEntity): Long = messageDao.insert(message)
    suspend fun updateMessage(message: MessageEntity) = messageDao.update(message)
    suspend fun deleteMessage(message: MessageEntity) = messageDao.delete(message)

    fun observeTodayCount(): Flow<Int> = messageDao.observeCountSince(startOfToday())
    fun observeQueuedCount(): Flow<Int> = messageDao.observeQueuedCount()

    suspend fun queuedMessages(): List<MessageEntity> = messageDao.queuedOnce()

    /** OpenAI wire messages for a conversation, capped to the most recent turns. */
    fun wireMessages(messages: List<MessageEntity>, limit: Int = 40): List<WireMessage> =
        messages.filter { (it.role == "user" || it.role == "assistant") && it.text.isNotBlank() }
            .takeLast(limit)
            .map { WireMessage(it.role, it.text) }

    // ── Titles ──────────────────────────────────────────────────────────────────

    suspend fun generateTitle(
        connection: ConnectionEntity,
        model: String,
        userText: String,
        assistantText: String
    ): String {
        val prompt = "Give this conversation a title of 2 to 4 words. Reply with only the title, " +
            "no quotes or punctuation.\nUser: ${userText.take(400)}\nAssistant: ${assistantText.take(400)}"
        return try {
            val result = client.complete(resolveApi(connection), model, listOf(WireMessage("user", prompt)), systemPrompt = "")
            val cleaned = result.text.trim().trim('"', '\'').take(40)
            if (cleaned.isBlank()) fallbackTitle(userText) else cleaned
        } catch (_: Exception) {
            fallbackTitle(userText)
        }
    }

    private fun fallbackTitle(userText: String): String {
        val words = userText.trim().split(Regex("\\s+")).take(4).joinToString(" ")
        return if (words.isBlank()) "New conversation" else words.replaceFirstChar { it.uppercase() }
    }

    // ── Export / Import ─────────────────────────────────────────────────────────

    suspend fun exportJson(): String {
        val out = JSONObject()
        out.put("version", 1)
        out.put("exportedAt", System.currentTimeMillis())
        val convs = JSONArray()
        conversationDao.allOnce().forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("title", c.title)
            obj.put("createdAt", c.createdAt)
            obj.put("updatedAt", c.updatedAt)
            obj.put("connectionId", c.connectionId)
            obj.put("model", c.model)
            obj.put("systemPrompt", c.systemPrompt)
            obj.put("kind", c.kind)
            obj.put("pinned", c.pinned)
            obj.put("starred", c.starred)
            obj.put("archived", c.archived)
            val msgs = JSONArray()
            messageDao.listFor(c.id).forEach { m ->
                val mo = JSONObject()
                mo.put("role", m.role)
                mo.put("text", m.text)
                mo.put("createdAt", m.createdAt)
                mo.put("model", m.model)
                mo.put("variants", m.variantsJson)
                msgs.put(mo)
            }
            obj.put("messages", msgs)
            convs.put(obj)
        }
        out.put("conversations", convs)
        return out.toString(2)
    }

    /** Import conversations from JSON. Invalid data never touches existing rows. */
    suspend fun importJson(raw: String): ImportResult {
        val root = try {
            JSONObject(raw)
        } catch (_: Exception) {
            return ImportResult.Failure("That file isn't valid JSON.")
        }
        val convs = root.optJSONArray("conversations")
            ?: return ImportResult.Failure("No conversations found in the file.")
        var imported = 0
        for (i in 0 until convs.length()) {
            val c = convs.optJSONObject(i) ?: continue
            val title = c.optString("title")
            val messages = c.optJSONArray("messages") ?: continue
            if (title.isBlank()) continue
            val entity = ConversationEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                createdAt = c.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = c.optLong("updatedAt", System.currentTimeMillis()),
                connectionId = c.optString("connectionId"),
                model = c.optString("model"),
                systemPrompt = c.optString("systemPrompt"),
                kind = c.optString("kind", "TEXT"),
                lastPreview = messages.optJSONObject(messages.length() - 1)
                    ?.optString("text")?.take(80).orEmpty()
            )
            conversationDao.upsert(entity)
            for (j in 0 until messages.length()) {
                val m = messages.optJSONObject(j) ?: continue
                messageDao.insert(
                    MessageEntity(
                        conversationId = entity.id,
                        role = m.optString("role", "user"),
                        text = m.optString("text", ""),
                        createdAt = m.optLong("createdAt", System.currentTimeMillis()),
                        model = m.optString("model"),
                        variantsJson = m.optString("variants", "")
                    )
                )
            }
            imported++
        }
        return if (imported == 0) {
            ImportResult.Failure("The file contained no importable conversations.")
        } else {
            ImportResult.Success(imported)
        }
    }

    sealed class ImportResult {
        data class Success(val count: Int) : ImportResult()
        data class Failure(val reason: String) : ImportResult()
    }

    companion object {
        fun startOfToday(): Long =
            java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }.timeInMillis
    }
}
