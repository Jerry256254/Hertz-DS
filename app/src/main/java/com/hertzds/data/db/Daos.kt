package com.hertzds.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    fun observe(id: String): Flow<ChatEntity?>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun get(id: String): ChatEntity?

    @Query("SELECT * FROM chats ORDER BY pinned DESC, updatedAt DESC LIMIT 1")
    suspend fun mostRecent(): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(chat: ChatEntity)

    @Update
    suspend fun update(chat: ChatEntity)

    @Query("UPDATE chats SET title = :title, titleIsAuto = :auto, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, title: String, auto: Boolean, now: Long)

    @Query("UPDATE chats SET pinned = :pinned, updatedAt = updatedAt WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean)

    @Query("UPDATE chats SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("UPDATE chats SET totalCostUsd = totalCostUsd + :delta WHERE id = :id")
    suspend fun addCost(id: String, delta: Double)

    @Query("DELETE FROM chats WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT COUNT(*) FROM chats")
    suspend fun count(): Int
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, rowid ASC")
    fun observeForChat(chatId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC, rowid ASC")
    suspend fun forChat(chatId: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun get(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, status = :status WHERE id = :id")
    suspend fun updateContent(id: String, content: String, status: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun clearChat(chatId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND role = 'user'")
    suspend fun userMessageCount(chatId: String): Int
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE chatId = :chatId")
    fun observeForChat(chatId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM attachments WHERE messageId = :messageId")
    suspend fun forMessage(messageId: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(attachment: AttachmentEntity)

    @Query("UPDATE attachments SET messageId = :messageId WHERE id = :id")
    suspend fun attachTo(id: String, messageId: String)

    @Query("UPDATE attachments SET extractedText = :text WHERE id = :id")
    suspend fun setExtractedText(id: String, text: String?)

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE id = :id")
    suspend fun get(id: String): MemoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE memories SET useCount = useCount + 1 WHERE id = :id")
    suspend fun markUsed(id: String)

    @Query("SELECT * FROM memories WHERE pinned = 1 AND (chatId IS NULL OR chatId = :chatId) ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun pinnedFor(chatId: String?, limit: Int): List<MemoryEntity>

    @Transaction
    @Query(
        """
        SELECT memories.* FROM memories
        JOIN memories_fts ON memories.rowid = memories_fts.rowid
        WHERE memories_fts MATCH :query
          AND (memories.chatId IS NULL OR memories.chatId = :chatId)
        ORDER BY memories.pinned DESC, memories.useCount DESC, memories.updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, chatId: String?, limit: Int): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE chatId IS NULL OR chatId = :chatId ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(chatId: String?, limit: Int): List<MemoryEntity>
}

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAll(): Flow<List<ApiKeyEntity>>

    @Query("SELECT * FROM api_keys ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun all(): List<ApiKeyEntity>

    @Query("SELECT * FROM api_keys WHERE id = :id")
    suspend fun get(id: String): ApiKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(key: ApiKeyEntity)

    @Query("DELETE FROM api_keys WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE api_keys SET lastBalanceUsd = :balance, lastBalanceCheckedAt = :now, lastError = NULL WHERE id = :id")
    suspend fun setBalance(id: String, balance: Double?, now: Long)

    @Query("UPDATE api_keys SET lastError = :error, cooldownUntil = :cooldownUntil WHERE id = :id")
    suspend fun setError(id: String, error: String?, cooldownUntil: Long?)

    @Query("UPDATE api_keys SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("UPDATE api_keys SET manualToppedUpUsd = :amount WHERE id = :id")
    suspend fun setManualTopUp(id: String, amount: Double?)

    @Query("SELECT COUNT(*) FROM api_keys")
    suspend fun count(): Int
}

@Dao
interface UsageDao {
    @Insert
    suspend fun insert(usage: UsageEntity)

    @Query("SELECT COALESCE(SUM(costUsd), 0) FROM usage WHERE createdAt >= :since")
    fun observeSpendSince(since: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(costUsd), 0) FROM usage WHERE keyId = :keyId")
    suspend fun spendForKey(keyId: String): Double

    @Query("SELECT * FROM usage ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<UsageEntity>>

    @Query(
        """
        SELECT model AS model,
               SUM(promptTokens) AS promptTokens,
               SUM(cachedTokens) AS cachedTokens,
               SUM(completionTokens) AS completionTokens,
               SUM(costUsd) AS costUsd,
               COUNT(*) AS calls
        FROM usage WHERE createdAt >= :since GROUP BY model ORDER BY costUsd DESC
        """,
    )
    fun observeByModelSince(since: Long): Flow<List<ModelUsageSummary>>

    @Query("SELECT COALESCE(SUM(costUsd), 0) FROM usage WHERE peakPricing = 1 AND createdAt >= :since")
    fun observePeakSpendSince(since: Long): Flow<Double>
}

data class ModelUsageSummary(
    val model: String,
    val promptTokens: Int,
    val cachedTokens: Int,
    val completionTokens: Int,
    val costUsd: Double,
    val calls: Int,
)

@Dao
interface NotebookDao {
    @Query("SELECT * FROM notebooks ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NotebookEntity>>

    @Query("SELECT * FROM notebooks WHERE id = :id")
    suspend fun get(id: String): NotebookEntity?

    @Query("SELECT * FROM notebooks WHERE sharedWithAi = 1 ORDER BY updatedAt DESC")
    suspend fun sharedWithAi(): List<NotebookEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(notebook: NotebookEntity)

    @Query("DELETE FROM notebooks WHERE id = :id")
    suspend fun delete(id: String)
}

@Dao
interface ScheduledTaskDao {
    @Query("SELECT * FROM scheduled_tasks ORDER BY nextRunAt ASC")
    fun observeAll(): Flow<List<ScheduledTaskEntity>>

    @Query("SELECT * FROM scheduled_tasks ORDER BY nextRunAt ASC")
    suspend fun all(): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE enabled = 1 AND nextRunAt <= :now")
    suspend fun due(now: Long): List<ScheduledTaskEntity>

    @Query("SELECT * FROM scheduled_tasks WHERE id = :id")
    suspend fun get(id: String): ScheduledTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: ScheduledTaskEntity)

    @Query("DELETE FROM scheduled_tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE scheduled_tasks SET lastRunAt = :lastRunAt, nextRunAt = :nextRunAt, lastResult = :result WHERE id = :id")
    suspend fun markRun(id: String, lastRunAt: Long, nextRunAt: Long, result: String?)
}
