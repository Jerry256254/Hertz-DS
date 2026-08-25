package com.hertzds.data.repo

import com.hertzds.data.db.AttachmentDao
import com.hertzds.data.db.AttachmentEntity
import com.hertzds.data.db.ChatDao
import com.hertzds.data.db.ChatEntity
import com.hertzds.data.db.MessageDao
import com.hertzds.data.db.MessageEntity
import com.hertzds.data.db.UsageDao
import com.hertzds.data.db.UsageEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

object MessageRole {
    const val USER = "user"
    const val ASSISTANT = "assistant"
    const val TOOL = "tool"
    const val SYSTEM = "system"
}

object MessageStatus {
    const val PENDING = "pending"
    const val STREAMING = "streaming"
    const val DONE = "done"
    const val ERROR = "error"
    const val CANCELLED = "cancelled"
}

class ChatRepository(
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val attachmentDao: AttachmentDao,
    private val usageDao: UsageDao,
) {

    val chats: Flow<List<ChatEntity>> = chatDao.observeAll()

    fun observeChat(id: String): Flow<ChatEntity?> = chatDao.observe(id)

    fun observeMessages(chatId: String): Flow<List<MessageEntity>> =
        messageDao.observeForChat(chatId)

    fun observeAttachments(chatId: String): Flow<List<AttachmentEntity>> =
        attachmentDao.observeForChat(chatId)

    suspend fun getChat(id: String): ChatEntity? = chatDao.get(id)

    suspend fun messages(chatId: String): List<MessageEntity> = messageDao.forChat(chatId)

    suspend fun attachmentsFor(messageId: String): List<AttachmentEntity> =
        attachmentDao.forMessage(messageId)

    suspend fun createChat(
        title: String,
        model: String,
        systemPrompt: String?,
    ): ChatEntity {
        val now = System.currentTimeMillis()
        val chat = ChatEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            systemPrompt = systemPrompt,
            model = model,
            createdAt = now,
            updatedAt = now,
        )
        chatDao.upsert(chat)
        return chat
    }

    suspend fun ensureChat(model: String, systemPrompt: String?, fallbackTitle: String): ChatEntity =
        chatDao.mostRecent() ?: createChat(fallbackTitle, model, systemPrompt)

    suspend fun updateChat(chat: ChatEntity) =
        chatDao.update(chat.copy(updatedAt = System.currentTimeMillis()))

    suspend fun rename(chatId: String, title: String, auto: Boolean = false) =
        chatDao.rename(chatId, title, auto, System.currentTimeMillis())

    suspend fun setPinned(chatId: String, pinned: Boolean) = chatDao.setPinned(chatId, pinned)

    suspend fun deleteChat(chatId: String) = chatDao.delete(chatId)

    suspend fun clearMessages(chatId: String) = messageDao.clearChat(chatId)

    suspend fun userMessageCount(chatId: String): Int = messageDao.userMessageCount(chatId)

    suspend fun addMessage(message: MessageEntity): MessageEntity {
        messageDao.upsert(message)
        chatDao.touch(message.chatId, System.currentTimeMillis())
        return message
    }

    fun newMessage(
        chatId: String,
        role: String,
        content: String = "",
        status: String = MessageStatus.DONE,
        model: String? = null,
        toolCallsJson: String? = null,
        toolCallId: String? = null,
        toolName: String? = null,
    ) = MessageEntity(
        id = UUID.randomUUID().toString(),
        chatId = chatId,
        role = role,
        content = content,
        status = status,
        model = model,
        toolCallsJson = toolCallsJson,
        toolCallId = toolCallId,
        toolName = toolName,
        createdAt = System.currentTimeMillis(),
    )

    suspend fun update(message: MessageEntity) = messageDao.upsert(message)

    suspend fun deleteMessage(id: String) = messageDao.delete(id)

    suspend fun addAttachment(attachment: AttachmentEntity) = attachmentDao.upsert(attachment)

    suspend fun attachToMessage(attachmentId: String, messageId: String) =
        attachmentDao.attachTo(attachmentId, messageId)

    suspend fun setExtractedText(attachmentId: String, text: String?) =
        attachmentDao.setExtractedText(attachmentId, text)

    suspend fun deleteAttachment(id: String) = attachmentDao.deleteById(id)

    suspend fun recordUsage(
        keyId: String?,
        chatId: String?,
        model: String,
        promptTokens: Int,
        cachedTokens: Int,
        completionTokens: Int,
        costUsd: Double,
        peakPricing: Boolean,
    ) {
        usageDao.insert(
            UsageEntity(
                keyId = keyId,
                chatId = chatId,
                model = model,
                promptTokens = promptTokens,
                cachedTokens = cachedTokens,
                completionTokens = completionTokens,
                costUsd = costUsd,
                peakPricing = peakPricing,
                createdAt = System.currentTimeMillis(),
            ),
        )
        chatId?.let { chatDao.addCost(it, costUsd) }
    }
}
