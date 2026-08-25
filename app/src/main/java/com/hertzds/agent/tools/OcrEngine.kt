package com.hertzds.agent.tools

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume

/**
 * OCR with automatic source chaining: on-device ML Kit first (free, offline,
 * private), DeepSeek OCR as the fallback when a DeepSeek key is available
 * and the local pass came back empty or failed.
 * DeepSeek OCR uses the vision-capable chat model to transcribe the image.
 */
class OcrEngine(
    private val http: OkHttpClient,
    private val json: Json,
) {

    data class Result(val text: String, val engine: String)

    suspend fun recognize(
        context: Context,
        uri: Uri,
        deepSeekKey: String?,
    ): kotlin.Result<Result> {
        val local = runCatching { mlKit(context, uri) }
        val localText = local.getOrNull()?.trim().orEmpty()
        if (localText.isNotEmpty()) return kotlin.Result.success(Result(localText, "ML Kit"))

        if (!deepSeekKey.isNullOrBlank()) {
            val remote = runCatching { deepSeekOcr(context, uri, deepSeekKey) }
            remote.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                return kotlin.Result.success(Result(it, "DeepSeek OCR"))
            }
            remote.exceptionOrNull()?.let { return kotlin.Result.failure(it) }
        }

        local.exceptionOrNull()?.let { return kotlin.Result.failure(it) }
        return kotlin.Result.success(Result("", "ML Kit"))
    }

    // Kept for backwards compat — delegates to DeepSeek path
    @Deprecated("Use DeepSeek OCR", ReplaceWith("recognize(context, uri, deepSeekKey)"))
    suspend fun recognizeWithMistral(context: Context, uri: Uri, key: String?): kotlin.Result<Result> =
        recognize(context, uri, key)

    private suspend fun mlKit(context: Context, uri: Uri): String =
        withContext(Dispatchers.IO) {
            val image = InputImage.fromFilePath(context, uri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            try {
                suspendCancellableCoroutine { continuation ->
                    recognizer.process(image)
                        .addOnSuccessListener { continuation.resume(it.text) }
                        .addOnFailureListener { continuation.resume("") }
                }
            } finally {
                recognizer.close()
            }
        }

    private suspend fun deepSeekOcr(context: Context, uri: Uri, apiKey: String): String =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("cannot read image")
            if (bytes.size > 4 * 1024 * 1024) error("image too large for DeepSeek OCR (>4MB)")
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val dataUri = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

            // Use chat completions with vision — DeepSeek vision transcribes the image
            val contentArray = kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("type", "text")
                    put("text", "Transcribe all text visible in this image exactly, preserving line breaks and layout. Return only the transcribed text, nothing else.")
                })
                add(kotlinx.serialization.json.buildJsonObject {
                    put("type", "image_url")
                    put("image_url", kotlinx.serialization.json.buildJsonObject { put("url", dataUri) })
                })
            }
            val messagesArray = kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.buildJsonObject {
                    put("role", "user")
                    put("content", contentArray)
                })
            }
            val payload: JsonObject = buildJsonObject {
                put("model", "deepseek-chat")
                put("messages", messagesArray)
                put("temperature", 0.0)
                put("max_tokens", 4096)
            }
            val request = Request.Builder()
                .url("https://api.deepseek.com/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON_MEDIA))
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("DeepSeek OCR HTTP ${response.code}: ${body.take(300)}")
                json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray
                    ?.firstOrNull()?.jsonObject?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
                    .orEmpty().trim()
            }
        }

    // Legacy Mistral path kept as thin wrapper for any lingering calls
    private suspend fun mistral(context: Context, uri: Uri, apiKey: String): String =
        deepSeekOcr(context, uri, apiKey)

    companion object {
        const val MISTRAL_OCR_MODEL = "deepseek-ocr"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
