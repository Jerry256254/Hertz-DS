package com.hertzds.agent

import android.content.Context
import com.hertzds.data.db.AttachmentEntity
import com.hertzds.data.db.MessageEntity
import com.hertzds.data.prefs.Settings
import com.hertzds.data.repo.ApiKeyRepository
import com.hertzds.data.repo.ChatRepository
import com.hertzds.data.repo.MemoryRepository
import com.hertzds.data.repo.MessageRole
import com.hertzds.data.repo.NotebookRepository
import com.hertzds.data.repo.MessageStatus
import com.hertzds.deepseek.ApiMessage
import com.hertzds.deepseek.ChatRequest
import com.hertzds.deepseek.DeepSeekException
import com.hertzds.deepseek.DeepSeekPricing
import com.hertzds.deepseek.LlmClient
import com.hertzds.deepseek.Models
import com.hertzds.deepseek.StreamEvent
import com.hertzds.deepseek.StreamOptions
import com.hertzds.deepseek.ToolCall
import com.hertzds.deepseek.Usage
import com.hertzds.provider.ProviderConfig
import com.hertzds.provider.toProviderConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.Instant

sealed interface AgentEvent {
    /** New assistant text, already persisted; use for streaming TTS. */
    data class Delta(val messageId: String, val text: String) : AgentEvent
    data class Reasoning(val messageId: String, val text: String) : AgentEvent
    data class ToolStarted(val toolName: String, val detail: String) : AgentEvent
    data class ToolFinished(val toolName: String, val success: Boolean) : AgentEvent
    data class Spent(val costUsd: Double, val peakPricing: Boolean) : AgentEvent
    data class Failed(val message: String, val recoverable: Boolean) : AgentEvent
    data object Completed : AgentEvent
}

/**
 * The agentic loop: send the conversation, run whatever tools the model asks for,
 * feed the results back, repeat until it answers in plain text.
 */
class AgentEngine(
    private val appContext: Context,
    private val client: LlmClient,
    private val chats: ChatRepository,
    private val keys: ApiKeyRepository,
    private val memories: MemoryRepository,
    private val notebooks: NotebookRepository,
    private val registry: ToolRegistry,
    private val json: Json,
) {

    companion object {
        /** Persist streamed text at most this often to keep the DB from thrashing. */
        private const val FLUSH_INTERVAL_MS = 120L
        private const val MAX_TOOL_RESULT_CHARS = 12_000
        private const val HISTORY_MESSAGE_LIMIT = 60
    }

    fun runTurn(chatId: String, settings: Settings): Flow<AgentEvent> = channelFlow {
        val chat = chats.getChat(chatId)
        if (chat == null) {
            send(AgentEvent.Failed("chat not found", recoverable = false))
            return@channelFlow
        }

        val provider = settings.toProviderConfig()
        val model = chat.model
        if (provider.baseUrl.isBlank()) {
            send(AgentEvent.Failed("provider_no_url", recoverable = true))
            return@channelFlow
        }
        if (model.isBlank()) {
            send(AgentEvent.Failed("no_model_selected", recoverable = true))
            return@channelFlow
        }
        val triedKeys = mutableSetOf<String>()
        var iteration = 0

        while (iteration < settings.maxToolIterations) {
            iteration++

            val resolvedKey = keys.nextKey(exclude = triedKeys)
            if (resolvedKey == null) {
                val message = if (triedKeys.isEmpty()) {
                    "no_api_key"
                } else {
                    "all_keys_exhausted"
                }
                send(AgentEvent.Failed(message, recoverable = true))
                return@channelFlow
            }

            val apiMessages = runCatching { buildApiMessages(chatId, chat.systemPrompt, settings, model) }
                .getOrElse { 
                    send(AgentEvent.Failed("Failed to build messages: ${it.message}", recoverable = true))
                    return@channelFlow
                }
            val toolSpecs = if (chat.toolsEnabled) runCatching { registry.specsFor(settings) }.getOrNull() else null

            val assistantMessage = runCatching {
                chats.addMessage(
                    chats.newMessage(
                        chatId = chatId,
                        role = MessageRole.ASSISTANT,
                        status = MessageStatus.STREAMING,
                        model = model,
                    ),
                )
            }.getOrElse { 
                send(AgentEvent.Failed("Failed to create assistant message: ${it.message}", recoverable = true))
                return@channelFlow
            }

            val contentBuilder = StringBuilder()
            val reasoningBuilder = StringBuilder()
            var toolCalls: List<ToolCall> = emptyList()
            var usage: Usage? = null
            var lastFlush = 0L

            try {
                client.streamChat(
                    provider = provider,
                    apiKey = resolvedKey.secret,
                    request = ChatRequest(
                        model = model,
                        messages = apiMessages,
                        stream = true,
                        streamOptions = StreamOptions(includeUsage = true),
                        tools = toolSpecs,
                        temperature = chat.temperature ?: settings.temperature,
                    ),
                ).collect { event ->
                    when (event) {
                        is StreamEvent.Content -> {
                            contentBuilder.append(event.text)
                            send(AgentEvent.Delta(assistantMessage.id, event.text))
                            val now = System.currentTimeMillis()
                            if (now - lastFlush > FLUSH_INTERVAL_MS) {
                                lastFlush = now
                                chats.update(
                                    assistantMessage.copy(
                                        content = contentBuilder.toString(),
                                        reasoning = reasoningBuilder.toString().ifEmpty { null },
                                        status = MessageStatus.STREAMING,
                                    ),
                                )
                            }
                        }

                        is StreamEvent.Reasoning -> {
                            reasoningBuilder.append(event.text)
                            send(AgentEvent.Reasoning(assistantMessage.id, event.text))
                        }

                        is StreamEvent.ToolCalls -> toolCalls = event.calls

                        is StreamEvent.Finished -> usage = event.usage
                    }
                }
                keys.reportSuccess(resolvedKey.id)
            } catch (cancellation: CancellationException) {
                chats.update(
                    assistantMessage.copy(
                        content = contentBuilder.toString(),
                        reasoning = reasoningBuilder.toString().ifEmpty { null },
                        status = MessageStatus.CANCELLED,
                    ),
                )
                throw cancellation
            } catch (error: DeepSeekException) {
                chats.deleteMessage(assistantMessage.id)
                keys.reportFailure(resolvedKey.id, error)
                if (error.shouldRotateKey) {
                    triedKeys += resolvedKey.id
                    continue
                }
                send(AgentEvent.Failed(error.message ?: "request failed", recoverable = true))
                return@channelFlow
            } catch (error: Exception) {
                chats.update(
                    assistantMessage.copy(
                        content = contentBuilder.toString(),
                        status = MessageStatus.ERROR,
                        error = error.message,
                    ),
                )
                send(AgentEvent.Failed(error.message ?: "unexpected error", recoverable = true))
                return@channelFlow
            }

            // Bill this round trip.
            usage?.let { u ->
                if (provider.isDeepSeek) {
                    runCatching {
                        val now = Instant.now()
                        val peak = DeepSeekPricing.isPeak(now)
                        val cost = DeepSeekPricing.cost(
                            model = model,
                            cacheHitTokens = u.cacheHit,
                            cacheMissTokens = u.cacheMiss,
                            outputTokens = u.completionTokens,
                            at = now,
                        )
                        chats.recordUsage(
                            keyId = resolvedKey.id,
                            chatId = chatId,
                            model = model,
                            promptTokens = u.promptTokens,
                            cachedTokens = u.cacheHit,
                            completionTokens = u.completionTokens,
                            costUsd = cost,
                            peakPricing = peak,
                        )
                        send(AgentEvent.Spent(cost, peak))

                        chats.update(
                            assistantMessage.copy(
                                content = contentBuilder.toString(),
                                reasoning = reasoningBuilder.toString().ifEmpty { null },
                                toolCallsJson = toolCalls.takeIf { it.isNotEmpty() }
                                    ?.let { json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ToolCall.serializer()), it) },
                                status = MessageStatus.DONE,
                                promptTokens = u.promptTokens,
                                cachedTokens = u.cacheHit,
                                completionTokens = u.completionTokens,
                                costUsd = cost,
                                peakPricing = peak,
                            ),
                        )
                    }.onFailure { send(AgentEvent.Failed("Failed to record usage: ${it.message}", recoverable = false)) }
                } else {
                    // Non-DeepSeek providers don't share the peak/off-peak pricing model.
                    runCatching {
                        chats.update(
                            assistantMessage.copy(
                                content = contentBuilder.toString(),
                                reasoning = reasoningBuilder.toString().ifEmpty { null },
                                toolCallsJson = toolCalls.takeIf { it.isNotEmpty() }
                                    ?.let { json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ToolCall.serializer()), it) },
                                status = MessageStatus.DONE,
                                promptTokens = u.promptTokens,
                                completionTokens = u.completionTokens,
                                costUsd = 0.0,
                                peakPricing = false,
                            ),
                        )
                    }.onFailure { send(AgentEvent.Failed("Failed to update message: ${it.message}", recoverable = false)) }
                }
            } ?: runCatching {
                chats.update(
                    assistantMessage.copy(
                        content = contentBuilder.toString(),
                        reasoning = reasoningBuilder.toString().ifEmpty { null },
                        toolCallsJson = toolCalls.takeIf { it.isNotEmpty() }
                            ?.let { json.encodeToString(kotlinx.serialization.builtins.ListSerializer(ToolCall.serializer()), it) },
                        status = MessageStatus.DONE,
                    ),
                )
            }.onFailure { send(AgentEvent.Failed("Failed to update message: ${it.message}", recoverable = false)) }

            if (toolCalls.isEmpty()) {
                send(AgentEvent.Completed)
                return@channelFlow
            }

            // Run every requested tool and append its result as a `tool` message.
            for (call in toolCalls) {
                val tool = registry.get(call.function.name)
                val toolMessage = runCatching {
                    chats.newMessage(
                        chatId = chatId,
                        role = MessageRole.TOOL,
                        status = MessageStatus.PENDING,
                        toolCallId = call.id,
                        toolName = call.function.name,
                    )
                }.getOrElse { 
                    send(AgentEvent.ToolFinished(call.function.name, false))
                    continue
                }
                runCatching { chats.addMessage(toolMessage) }
                    .onFailure { send(AgentEvent.ToolFinished(call.function.name, false)) }
                send(AgentEvent.ToolStarted(call.function.name, ""))

                val result = if (tool == null) {
                    ToolResult.error("unknown tool '${call.function.name}'")
                } else {
                    val args = runCatching {
                        json.parseToJsonElement(call.function.arguments.ifBlank { "{}" }).jsonObject
                    }.getOrElse { JsonObject(emptyMap()) }

                    runCatching {
                        tool.execute(
                            args,
                            ToolContext(
                                appContext = appContext,
                                chatId = chatId,
                                settings = settings,
                                onProgress = { detail ->
                                    trySend(AgentEvent.ToolStarted(call.function.name, detail))
                                },
                            ),
                        )
                    }.getOrElse { ToolResult.error(it.message ?: it::class.simpleName ?: "tool failed") }
                }

                runCatching {
                    chats.update(
                        toolMessage.copy(
                            content = result.content.take(MAX_TOOL_RESULT_CHARS),
                            status = if (result.isError) MessageStatus.ERROR else MessageStatus.DONE,
                            error = if (result.isError) result.content else null,
                        ),
                    )
                }.onFailure { send(AgentEvent.ToolFinished(call.function.name, false)) }
                
                result.imageUri?.let { uri ->
                    runCatching {
                        chats.addAttachment(
                            AttachmentEntity(
                                id = java.util.UUID.randomUUID().toString(),
                                messageId = toolMessage.id,
                                chatId = chatId,
                                uri = uri,
                                name = uri.substringAfterLast('/'),
                                mimeType = "image/jpeg",
                                sizeBytes = 0,
                                kind = "image",
                                createdAt = System.currentTimeMillis(),
                            ),
                        )
                    }.onFailure { /* ignore attachment errors */ }
                }
                send(AgentEvent.ToolFinished(call.function.name, !result.isError))
            }
        }

        send(AgentEvent.Failed("tool_loop_limit", recoverable = true))
    }

    /** Turns the stored conversation into the payload the provider expects. */
    private suspend fun buildApiMessages(
        chatId: String,
        chatSystemPrompt: String?,
        settings: Settings,
        model: String,
    ): List<ApiMessage> {
        val provider = settings.toProviderConfig()
        val stored = chats.messages(chatId).takeLast(HISTORY_MESSAGE_LIMIT)
        val lastUserText = stored.lastOrNull { it.role == MessageRole.USER }?.content.orEmpty()

        val memoryBlock = if (settings.memoryEnabled) {
            memories.contextFor(chatId, lastUserText).takeIf { it.isNotEmpty() }?.joinToString("\n") {
                "- ${it.title}: ${it.content}"
            }
        } else {
            null
        }

        val isGhost = chatSystemPrompt?.contains("ghost mode", ignoreCase = true) == true
        val notesBlock = if (!isGhost) runCatching { notebooks.sharedContext() }.getOrNull() else null

        val systemText = buildString {
            append(chatSystemPrompt?.takeIf { it.isNotBlank() } ?: settings.defaultSystemPrompt)
            appendLine()
            appendLine()
            appendLine("Workspace: files you read or write live in a private sandbox folder; use relative paths.")
            if (provider.isDeepSeek) {
                append("Billing: DeepSeek charges double during peak hours (01:00-04:00 and 06:00-10:00 UTC, Mon-Fri). ")
                appendLine("Call get_time if the user asks about cost or timing.")
            }
            if (!memoryBlock.isNullOrBlank()) {
                appendLine()
                appendLine("Long-term memory (retrieved for this turn):")
                append(memoryBlock)
            }
            if (!notesBlock.isNullOrBlank()) {
                appendLine()
                appendLine("The user's notes (shared with you):")
                append(notesBlock)
            }
        }

        val result = mutableListOf(
            ApiMessage(role = MessageRole.SYSTEM, content = JsonPrimitive(systemText)),
        )

        for (message in stored) {
            when (message.role) {
                MessageRole.USER -> result += userApiMessage(message, model)

                MessageRole.ASSISTANT -> {
                    val calls = message.toolCallsJson?.let { raw ->
                        runCatching {
                            json.decodeFromString(
                                kotlinx.serialization.builtins.ListSerializer(ToolCall.serializer()),
                                raw,
                            )
                        }.getOrNull()
                    }
                    if (message.content.isBlank() && calls.isNullOrEmpty()) continue
                    result += ApiMessage(
                        role = MessageRole.ASSISTANT,
                        content = message.content.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) },
                        toolCalls = calls,
                    )
                }

                MessageRole.TOOL -> result += ApiMessage(
                    role = MessageRole.TOOL,
                    content = JsonPrimitive(message.content.ifBlank { "(no output)" }),
                    toolCallId = message.toolCallId,
                )
            }
        }
        return result
    }

    /** Attachments ride along as vision parts when the model supports it, as text otherwise. */
    private suspend fun userApiMessage(message: MessageEntity, model: String): ApiMessage {
        val attachments = chats.attachmentsFor(message.id)
        if (attachments.isEmpty()) {
            return ApiMessage(role = MessageRole.USER, content = JsonPrimitive(message.content))
        }

        val images = attachments.filter { it.kind == "image" }
        val documents = attachments - images.toSet()

        val textPart = buildString {
            append(message.content)
            documents.forEach { attachment ->
                appendLine()
                appendLine()
                appendLine("[Attached file: ${attachment.name} (${attachment.mimeType})]")
                attachment.extractedText?.takeIf { it.isNotBlank() }?.let {
                    append(it.take(MAX_TOOL_RESULT_CHARS))
                }
            }
            if (!Models.supportsVision(model)) {
                images.forEach { attachment ->
                    appendLine()
                    appendLine()
                    appendLine("[Attached image: ${attachment.name}]")
                    attachment.extractedText?.takeIf { it.isNotBlank() }?.let {
                        appendLine("Text recognised in the image:")
                        append(it.take(MAX_TOOL_RESULT_CHARS))
                    }
                }
            }
        }

        if (!Models.supportsVision(model) || images.isEmpty()) {
            return ApiMessage(role = MessageRole.USER, content = JsonPrimitive(textPart))
        }

        val parts: JsonArray = buildJsonArray {
            add(
                buildJsonObject {
                    put("type", "text")
                    put("text", textPart)
                },
            )
            images.forEach { attachment ->
                encodeImage(attachment)?.let { dataUri ->
                    add(
                        buildJsonObject {
                            put("type", "image_url")
                            putJsonObject("image_url") { put("url", dataUri) }
                        },
                    )
                }
            }
        }
        return ApiMessage(role = MessageRole.USER, content = parts)
    }

    private fun encodeImage(attachment: AttachmentEntity): String? = runCatching {
        val uri = android.net.Uri.parse(attachment.uri)
        val bytes = appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        if (bytes.size > 4 * 1024 * 1024) return null
        val mime = attachment.mimeType.ifBlank { "image/jpeg" }
        "data:$mime;base64," + android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }.getOrNull()
}
