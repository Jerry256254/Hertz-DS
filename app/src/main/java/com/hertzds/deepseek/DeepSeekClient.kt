package com.hertzds.deepseek

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * Thin OpenAI-compatible client for https://api.deepseek.com.
 * Streaming is parsed by hand so a turn can be cancelled mid-token and so
 * DeepSeek-specific fields (reasoning_content, cache hit/miss usage) survive.
 */
class DeepSeekClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }

    fun streamChat(apiKey: String, request: ChatRequest): Flow<StreamEvent> = flow {
        val body = json.encodeToString(ChatRequest.serializer(), request.copy(stream = true))
        val httpRequest = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        val call = http.newCall(httpRequest)
        val response = try {
            call.execute()
        } catch (e: IOException) {
            throw DeepSeekException(ApiFailure.NETWORK, e.message ?: "network error")
        }

        response.use { resp ->
            if (!resp.isSuccessful) {
                throw errorFor(resp.code, resp.body?.string().orEmpty())
            }
            val source = resp.body?.source()
                ?: throw DeepSeekException(ApiFailure.SERVER, "empty response body")

            val toolAccumulator = ToolCallAccumulator()
            var usage: Usage? = null
            var finishReason: String? = null

            while (true) {
                currentCoroutineContext().ensureActive()
                val line = try {
                    source.readUtf8Line()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    throw DeepSeekException(ApiFailure.NETWORK, e.message ?: "stream interrupted")
                } ?: break

                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload.isEmpty()) continue
                if (payload == "[DONE]") break

                val chunk = runCatching {
                    json.decodeFromString(StreamChunk.serializer(), payload)
                }.getOrNull() ?: continue

                chunk.usage?.let { usage = it }
                val choice = chunk.choices.firstOrNull()
                if (choice != null) {
                    choice.delta.reasoningContent?.takeIf { it.isNotEmpty() }?.let {
                        emit(StreamEvent.Reasoning(it))
                    }
                    choice.delta.content?.takeIf { it.isNotEmpty() }?.let {
                        emit(StreamEvent.Content(it))
                    }
                    choice.delta.toolCalls?.forEach(toolAccumulator::accept)
                    choice.finishReason?.let { finishReason = it }
                }
            }

            val calls = toolAccumulator.build()
            if (calls.isNotEmpty()) emit(StreamEvent.ToolCalls(calls))
            emit(StreamEvent.Finished(finishReason, usage))
        }
    }.flowOn(Dispatchers.IO)

    /** Non-streaming call, used for cheap side tasks like auto-naming a chat. */
    suspend fun complete(apiKey: String, request: ChatRequest): ChatResponse =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(
                ChatRequest.serializer(),
                request.copy(stream = false, streamOptions = null),
            )
            val httpRequest = Request.Builder()
                .url("$baseUrl/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            try {
                http.newCall(httpRequest).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw errorFor(resp.code, text)
                    json.decodeFromString(ChatResponse.serializer(), text)
                }
            } catch (e: IOException) {
                throw DeepSeekException(ApiFailure.NETWORK, e.message ?: "network error")
            }
        }

    /** GET /user/balance — the real remaining credit for this key. */
    suspend fun balance(apiKey: String): BalanceResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/user/balance")
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()
        try {
            http.newCall(request).execute().use { resp ->
                val text = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) throw errorFor(resp.code, text)
                json.decodeFromString(BalanceResponse.serializer(), text)
            }
        } catch (e: IOException) {
            throw DeepSeekException(ApiFailure.NETWORK, e.message ?: "network error")
        }
    }

    private fun errorFor(code: Int, body: String): DeepSeekException {
        val message = runCatching {
            json.decodeFromString(ApiErrorEnvelope.serializer(), body).error?.message
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: body.take(300).ifBlank { "HTTP $code" }

        val failure = when {
            code == 401 || code == 403 -> ApiFailure.AUTH
            code == 402 -> ApiFailure.INSUFFICIENT_BALANCE
            code == 429 -> ApiFailure.RATE_LIMIT
            code in 500..599 -> ApiFailure.SERVER
            code == 400 || code == 422 -> ApiFailure.BAD_REQUEST
            else -> ApiFailure.SERVER
        }
        return DeepSeekException(failure, message, code)
    }
}

/**
 * Streamed tool calls arrive as fragments keyed by index: the name comes once,
 * the JSON arguments trickle in character by character.
 */
private class ToolCallAccumulator {
    private data class Partial(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())

    private val byIndex = linkedMapOf<Int, Partial>()

    fun accept(delta: ToolCallDelta) {
        val partial = byIndex.getOrPut(delta.index) { Partial() }
        delta.id?.let { partial.id = it }
        delta.function?.name?.let { if (it.isNotEmpty()) partial.name = it }
        delta.function?.arguments?.let { partial.args.append(it) }
    }

    fun build(): List<ToolCall> = byIndex.values
        .filter { it.name.isNotEmpty() }
        .mapIndexed { i, p ->
            ToolCall(
                id = p.id.ifEmpty { "call_$i" },
                function = FunctionCall(name = p.name, arguments = p.args.toString().ifEmpty { "{}" }),
            )
        }
}
