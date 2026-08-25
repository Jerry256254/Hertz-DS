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
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume

/**
 * OCR with automatic source chaining: on-device ML Kit first (free, offline,
 * private), Mistral OCR as the fallback when the user supplied a key and the
 * local pass came back empty or failed.
 */
class OcrEngine(
    private val http: OkHttpClient,
    private val json: Json,
) {

    data class Result(val text: String, val engine: String)

    suspend fun recognize(
        context: Context,
        uri: Uri,
        mistralKey: String?,
    ): kotlin.Result<Result> {
        val local = runCatching { mlKit(context, uri) }
        val localText = local.getOrNull()?.trim().orEmpty()
        if (localText.isNotEmpty()) return kotlin.Result.success(Result(localText, "ML Kit"))

        if (!mistralKey.isNullOrBlank()) {
            val remote = runCatching { mistral(context, uri, mistralKey) }
            remote.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                return kotlin.Result.success(Result(it, "Mistral OCR"))
            }
            remote.exceptionOrNull()?.let { return kotlin.Result.failure(it) }
        }

        local.exceptionOrNull()?.let { return kotlin.Result.failure(it) }
        return kotlin.Result.success(Result("", "ML Kit"))
    }

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

    private suspend fun mistral(context: Context, uri: Uri, apiKey: String): String =
        withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: error("cannot read image")
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val dataUri = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)

            val payload: JsonObject = buildJsonObject {
                put("model", MISTRAL_OCR_MODEL)
                putJsonObject("document") {
                    put("type", "image_url")
                    put("image_url", dataUri)
                }
            }
            val request = Request.Builder()
                .url("https://api.mistral.ai/v1/ocr")
                .addHeader("Authorization", "Bearer $apiKey")
                .post(json.encodeToString(JsonObject.serializer(), payload).toRequestBody(JSON_MEDIA))
                .build()

            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Mistral OCR HTTP ${response.code}: ${body.take(200)}")
                json.parseToJsonElement(body).jsonObject["pages"]?.jsonArray
                    ?.mapNotNull { it.jsonObject["markdown"]?.jsonPrimitive?.content }
                    ?.joinToString("\n\n")
                    .orEmpty()
            }
        }

    companion object {
        const val MISTRAL_OCR_MODEL = "mistral-ocr-latest"
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
