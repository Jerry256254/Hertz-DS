package com.hertzds.data.repo

import com.hertzds.data.db.MemoryDao
import com.hertzds.data.db.MemoryEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Long-term memory. DeepSeek exposes no embedding endpoint, so retrieval runs on
 * Room's FTS4 index (keyword + prefix matching) rather than vectors — it works
 * fully offline and needs no extra model download.
 */
class MemoryRepository(private val dao: MemoryDao) {

    val memories: Flow<List<MemoryEntity>> = dao.observeAll()

    suspend fun remember(
        title: String,
        content: String,
        chatId: String? = null,
        source: String = "agent",
        pinned: Boolean = false,
    ): MemoryEntity {
        val now = System.currentTimeMillis()
        val entity = MemoryEntity(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            title = title.trim().take(120),
            content = content.trim(),
            source = source,
            pinned = pinned,
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun update(entity: MemoryEntity) =
        dao.upsert(entity.copy(updatedAt = System.currentTimeMillis()))

    suspend fun forget(id: String) = dao.delete(id)

    suspend fun get(id: String): MemoryEntity? = dao.get(id)

    suspend fun recent(chatId: String?, limit: Int = 20): List<MemoryEntity> =
        dao.recent(chatId, limit)

    suspend fun search(query: String, chatId: String?, limit: Int = 8): List<MemoryEntity> {
        val expression = toMatchExpression(query)
        if (expression.isBlank()) return emptyList()
        val results = runCatching { dao.search(expression, chatId, limit) }.getOrElse { emptyList() }
        results.forEach { dao.markUsed(it.id) }
        return results
    }

    /**
     * Builds the context block injected into the system prompt: pinned memories
     * always, plus whatever the latest user message matches.
     */
    suspend fun contextFor(chatId: String?, query: String, limit: Int = 8): List<MemoryEntity> {
        val pinned = dao.pinnedFor(chatId, limit)
        val matched = search(query, chatId, limit)
        return (pinned + matched).distinctBy { it.id }.take(limit)
    }

    /** FTS4 MATCH syntax: OR the significant tokens, each as a prefix query. */
    private fun toMatchExpression(query: String): String = query
        .lowercase()
        .split(Regex("[^\\p{L}\\p{Nd}]+"))
        .filter { it.length >= 3 }
        .distinct()
        .take(12)
        .joinToString(" OR ") { "$it*" }
}
