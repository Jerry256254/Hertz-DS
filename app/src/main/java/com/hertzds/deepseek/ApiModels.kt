package com.hertzds.deepseek

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ApiMessage>,
    val stream: Boolean = true,
    @SerialName("stream_options") val streamOptions: StreamOptions? = null,
    val tools: List<ToolSpec>? = null,
    @SerialName("tool_choice") val toolChoice: String? = null,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
)

@Serializable
data class StreamOptions(@SerialName("include_usage") val includeUsage: Boolean = true)

@Serializable
data class ApiMessage(
    val role: String,
    val content: JsonElement? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall,
)

@Serializable
data class FunctionCall(
    val name: String = "",
    val arguments: String = "",
)

@Serializable
data class ToolSpec(
    val type: String = "function",
    val function: FunctionSpec,
)

@Serializable
data class FunctionSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

@Serializable
data class ChatResponse(
    val id: String? = null,
    val choices: List<Choice> = emptyList(),
    val usage: Usage? = null,
)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: ResponseMessage? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class ResponseMessage(
    val role: String = "assistant",
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
)

@Serializable
data class StreamChunk(
    val id: String? = null,
    val choices: List<StreamChoice> = emptyList(),
    val usage: Usage? = null,
)

@Serializable
data class StreamChoice(
    val index: Int = 0,
    val delta: Delta = Delta(),
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Delta(
    val role: String? = null,
    val content: String? = null,
    @SerialName("reasoning_content") val reasoningContent: String? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCallDelta>? = null,
)

@Serializable
data class ToolCallDelta(
    val index: Int = 0,
    val id: String? = null,
    val type: String? = null,
    val function: FunctionDelta? = null,
)

@Serializable
data class FunctionDelta(
    val name: String? = null,
    val arguments: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("prompt_cache_hit_tokens") val promptCacheHitTokens: Int? = null,
    @SerialName("prompt_cache_miss_tokens") val promptCacheMissTokens: Int? = null,
) {
    /** Falls back to "everything was a cache miss" when the fields are absent. */
    val cacheHit: Int get() = promptCacheHitTokens ?: 0
    val cacheMiss: Int get() = promptCacheMissTokens ?: (promptTokens - cacheHit).coerceAtLeast(0)
}

@Serializable
data class BalanceResponse(
    @SerialName("is_available") val isAvailable: Boolean = false,
    @SerialName("balance_infos") val balanceInfos: List<BalanceInfo> = emptyList(),
)

@Serializable
data class BalanceInfo(
    val currency: String = "USD",
    @SerialName("total_balance") val totalBalance: String = "0",
    @SerialName("granted_balance") val grantedBalance: String = "0",
    @SerialName("topped_up_balance") val toppedUpBalance: String = "0",
)

@Serializable
data class ApiErrorEnvelope(val error: ApiErrorBody? = null)

@Serializable
data class ApiErrorBody(
    val message: String = "",
    val type: String? = null,
    val code: String? = null,
)

/** Events emitted while streaming one assistant turn. */
sealed interface StreamEvent {
    data class Content(val text: String) : StreamEvent
    data class Reasoning(val text: String) : StreamEvent
    data class ToolCalls(val calls: List<ToolCall>) : StreamEvent
    data class Finished(val finishReason: String?, val usage: Usage?) : StreamEvent
}

/** Why a request failed, so key rotation can decide whether to try the next key. */
enum class ApiFailure { AUTH, INSUFFICIENT_BALANCE, RATE_LIMIT, SERVER, NETWORK, BAD_REQUEST, CANCELLED }

class DeepSeekException(
    val failure: ApiFailure,
    message: String,
    val httpCode: Int? = null,
) : Exception(message) {
    /** Whether rotating to another API key could plausibly help. */
    val shouldRotateKey: Boolean
        get() = failure == ApiFailure.AUTH ||
            failure == ApiFailure.INSUFFICIENT_BALANCE ||
            failure == ApiFailure.RATE_LIMIT
}
