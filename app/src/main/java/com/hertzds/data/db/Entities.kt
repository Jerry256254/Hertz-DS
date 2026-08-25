package com.hertzds.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val title: String,
    val systemPrompt: String? = null,
    val model: String,
    val pinned: Boolean = false,
    /** True while the title is still a placeholder or auto-generated suggestion. */
    val titleIsAuto: Boolean = true,
    val toolsEnabled: Boolean = true,
    val temperature: Double? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val totalCostUsd: Double = 0.0,
)

@Entity(
    tableName = "messages",
    indices = [Index("chatId"), Index("createdAt")],
    foreignKeys = [
        ForeignKey(
            entity = ChatEntity::class,
            parentColumns = ["id"],
            childColumns = ["chatId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    /** user | assistant | tool | system */
    val role: String,
    val content: String = "",
    val reasoning: String? = null,
    val toolCallsJson: String? = null,
    val toolCallId: String? = null,
    val toolName: String? = null,
    /** pending | streaming | done | error | cancelled */
    val status: String = "done",
    val error: String? = null,
    val model: String? = null,
    val promptTokens: Int = 0,
    val cachedTokens: Int = 0,
    val completionTokens: Int = 0,
    val costUsd: Double = 0.0,
    val peakPricing: Boolean = false,
    val createdAt: Long,
)

@Entity(
    tableName = "attachments",
    indices = [Index("messageId"), Index("chatId")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String?,
    val chatId: String,
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** image | pdf | text | other */
    val kind: String,
    /** OCR result or extracted document text, fed to the model. */
    val extractedText: String? = null,
    val createdAt: Long,
)

@Entity(tableName = "memories", indices = [Index("chatId")])
data class MemoryEntity(
    @PrimaryKey val id: String,
    /** null = global memory shared by every ghost chat. */
    val chatId: String? = null,
    val title: String,
    val content: String,
    /** agent | user | system */
    val source: String = "agent",
    val pinned: Boolean = false,
    val useCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
)

@Fts4(contentEntity = MemoryEntity::class)
@Entity(tableName = "memories_fts")
data class MemoryFts(
    val title: String,
    val content: String,
)

@Entity(tableName = "api_keys")
data class ApiKeyEntity(
    @PrimaryKey val id: String,
    val label: String,
    /** Ciphertext produced by SecretStore; the plaintext key never hits the disk. */
    val encryptedKey: String,
    val maskedKey: String,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val lastBalanceUsd: Double? = null,
    val lastBalanceCheckedAt: Long? = null,
    /** Manually entered top-up total, used when the balance endpoint is unreachable. */
    val manualToppedUpUsd: Double? = null,
    val lastError: String? = null,
    /** Epoch millis until which this key is skipped after a rate limit / 402. */
    val cooldownUntil: Long? = null,
    val createdAt: Long,
)

@Entity(tableName = "usage", indices = [Index("createdAt"), Index("keyId")])
data class UsageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyId: String?,
    val chatId: String?,
    val model: String,
    val promptTokens: Int,
    val cachedTokens: Int,
    val completionTokens: Int,
    val costUsd: Double,
    val peakPricing: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "scheduled_tasks")
data class ScheduledTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val prompt: String,
    /** Minutes between runs; 1440 = daily. */
    val intervalMinutes: Int,
    /** Minutes past local midnight for the first run of the day, null = relative. */
    val timeOfDayMinutes: Int? = null,
    val chatId: String? = null,
    val enabled: Boolean = true,
    val notifyOnComplete: Boolean = true,
    val lastRunAt: Long? = null,
    val nextRunAt: Long,
    val lastResult: String? = null,
    val createdAt: Long,
)
