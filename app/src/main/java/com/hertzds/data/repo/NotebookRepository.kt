package com.hertzds.data.repo

import com.hertzds.data.db.NotebookDao
import com.hertzds.data.db.NotebookEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/** User notebooks — a personal notepad, optionally shared into the AI's context. */
class NotebookRepository(private val dao: NotebookDao) {

    val notebooks: Flow<List<NotebookEntity>> = dao.observeAll()

    suspend fun create(title: String): NotebookEntity {
        val now = System.currentTimeMillis()
        val entity = NotebookEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim().ifBlank { "Untitled" },
            createdAt = now,
            updatedAt = now,
        )
        dao.upsert(entity)
        return entity
    }

    suspend fun get(id: String): NotebookEntity? = dao.get(id)

    suspend fun save(notebook: NotebookEntity) =
        dao.upsert(notebook.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(id: String) = dao.delete(id)

    /** What the AI is allowed to see — only notebooks the user explicitly shared. */
    suspend fun sharedContext(): String? {
        val shared = dao.sharedWithAi()
        if (shared.isEmpty()) return null
        return shared.joinToString("\n\n") { "### ${it.title}\n${it.content}" }
    }
}
