package com.hertzds.tools

import android.content.Context
import android.net.Uri
import com.hertzds.agent.tools.OcrEngine
import com.hertzds.data.db.AttachmentEntity
import okhttp3.OkHttpClient

/**
 * Prepares user attachments before a turn: images go through OCR (ML Kit, with
 * optional Mistral fallback), plain-text documents are read straight from disk.
 */
object OcrBridge {

    suspend fun extract(
        http: OkHttpClient,
        context: Context,
        uri: Uri,
        attachment: AttachmentEntity,
        mistralKey: String?,
    ): String? = try {
        when (attachment.kind) {
            "image" -> OcrEngine(http, kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
                .recognize(context, uri, mistralKey)
                .getOrNull()
                ?.text

            "text" -> readText(context, uri)
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun readText(context: Context, uri: Uri): String = runCatching {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = stream.readBytes()
            if (bytes.size > 512 * 1024) return String(bytes, 0, 512 * 1024, Charsets.UTF_8)
            String(bytes, Charsets.UTF_8)
        } ?: ""
    }.getOrDefault("")
}
