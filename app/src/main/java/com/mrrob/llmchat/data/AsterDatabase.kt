package com.mrrob.llmchat.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow
import java.util.UUID

// ── Entities ─────────────────────────────────────────────────────────────────

@Entity(tableName = "connections")
data class ConnectionEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String,
    /** OPENAI | OPENAI_COMPATIBLE | CUSTOM_API | LOCAL_SERVER */
    val provider: String,
    val baseUrl: String,
    /** JSON array of enabled model ids. */
    val modelsJson: String = "[]",
    val activeModel: String = "",
    val enabled: Boolean = true,
    val isDefault: Boolean = false,
    /** CONNECTED | OFFLINE | ERROR | UNKNOWN */
    val status: String = "UNKNOWN",
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val connectionId: String = "",
    val model: String = "",
    val systemPrompt: String = "",
    /** TEXT | VOICE */
    val kind: String = "TEXT",
    val pinned: Boolean = false,
    val starred: Boolean = false,
    val archived: Boolean = false,
    val branchOf: String = "",
    val draft: String = "",
    val lastPreview: String = "",
    val voice: Boolean = false
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val conversationId: String,
    /** user | assistant | system */
    val role: String,
    val text: String,
    /** JSON array of alternative assistant texts (regenerations). */
    val variantsJson: String = "",
    val activeVariant: Int = 0,
    val model: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /** OK | QUEUED | FAILED */
    val status: String = "OK",
    val latencyMs: Long = 0,
    val tokensIn: Int = -1,
    val tokensOut: Int = -1,
    val rating: Int = 0,
    /** JSON array of local attachment file paths (images). */
    val images: String = ""
)

// ── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface ConnectionDao {
    @Query("SELECT * FROM connections ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<ConnectionEntity>>

    @Query("SELECT * FROM connections WHERE id = :id")
    suspend fun byId(id: String): ConnectionEntity?

    @Query("SELECT * FROM connections WHERE isDefault = 1 LIMIT 1")
    suspend fun defaultConnection(): ConnectionEntity?

    @Query("SELECT * FROM connections")
    suspend fun allOnce(): List<ConnectionEntity>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) + 1 FROM connections")
    suspend fun nextSort(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(connection: ConnectionEntity)

    @Delete
    suspend fun delete(connection: ConnectionEntity)
}

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations")
    suspend fun allOnce(): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE archived = 1 ORDER BY updatedAt DESC")
    fun observeArchived(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun byId(id: String): ConversationEntity?

    @Query(
        """SELECT * FROM conversations WHERE archived = 0 AND (
               title LIKE '%' || :query || '%' OR EXISTS (
               SELECT 1 FROM messages WHERE messages.conversationId = conversations.id
               AND messages.text LIKE '%' || :query || '%'))
           ORDER BY pinned DESC, updatedAt DESC"""
    )
    fun search(query: String): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity)

    @Update
    suspend fun update(conversation: ConversationEntity)

    @Delete
    suspend fun delete(conversation: ConversationEntity)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    fun observeFor(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC, id ASC")
    suspend fun listFor(conversationId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: Long): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Delete
    suspend fun delete(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun clearFor(conversationId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE createdAt >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE role = 'user' AND status = 'QUEUED'")
    fun observeQueuedCount(): Flow<Int>

    @Query("SELECT * FROM messages WHERE role = 'user' AND status = 'QUEUED' ORDER BY createdAt ASC")
    suspend fun queuedOnce(): List<MessageEntity>
}

// ── Database ─────────────────────────────────────────────────────────────────

@Database(
    entities = [ConnectionEntity::class, ConversationEntity::class, MessageEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AsterDatabase : RoomDatabase() {
    abstract fun connections(): ConnectionDao
    abstract fun conversations(): ConversationDao
    abstract fun messages(): MessageDao

    companion object {
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE messages ADD COLUMN images TEXT NOT NULL DEFAULT ''")
            }
        }

        fun build(context: android.content.Context): AsterDatabase =
            androidx.room.Room.databaseBuilder(context, AsterDatabase::class.java, "aster.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
    }
}
