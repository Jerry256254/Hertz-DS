package com.hertzds.agent.tools

import android.net.Uri
import androidx.core.net.toUri
import com.hertzds.agent.AgentTool
import com.hertzds.agent.ToolContext
import com.hertzds.agent.ToolResult
import com.hertzds.agent.int
import com.hertzds.agent.intProp
import com.hertzds.agent.requireString
import com.hertzds.agent.schema
import com.hertzds.agent.string
import com.hertzds.agent.stringProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder

/**
 * Free image generation through Pollinations.ai — plain URL, no API key.
 * The bytes are saved into the workspace so the chat can render them offline.
 */
class GenerateImageTool(private val http: OkHttpClient) : AgentTool {

    override val name = "generate_image"
    override val description =
        "Generate an image from a text prompt (free, no API key) and save it into the workspace. " +
            "Returns the saved path; the image is shown in the chat automatically."
    override val parameters = schema(
        "prompt" to stringProp("Detailed English description of the image to generate."),
        "width" to intProp("Image width in pixels (256-1536, default 1024)."),
        "height" to intProp("Image height in pixels (256-1536, default 1024)."),
        "seed" to intProp("Seed for reproducible output."),
        "model" to stringProp("Generation model; leave empty for the default."),
        required = listOf("prompt"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult =
        withContext(Dispatchers.IO) {
            val prompt = args.requireString("prompt")
            val width = (args.int("width") ?: 1024).coerceIn(256, 1536)
            val height = (args.int("height") ?: 1024).coerceIn(256, 1536)
            context.onProgress(prompt.take(60))

            val url = buildString {
                append("https://image.pollinations.ai/prompt/")
                append(URLEncoder.encode(prompt, "UTF-8").replace("+", "%20"))
                append("?width=$width&height=$height&nologo=true&referrer=hertz-ds")
                args.int("seed")?.let { append("&seed=$it") }
                args.string("model")?.takeIf { it.isNotBlank() }?.let { append("&model=$it") }
            }

            runCatching {
                http.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext ToolResult.error("image service returned HTTP ${response.code}")
                    }
                    val bytes = response.body?.bytes()
                        ?: return@withContext ToolResult.error("empty image response")

                    val imagesDir = File(Workspace.root(context.appContext), "images").apply { mkdirs() }
                    val safeName = prompt.lowercase()
                        .replace(Regex("[^a-z0-9]+"), "-")
                        .trim('-')
                        .take(40)
                        .ifEmpty { "image" }
                    val file = File(imagesDir, "$safeName-${System.currentTimeMillis()}.jpg")
                    file.writeBytes(bytes)

                    val relative = Workspace.relative(context.appContext, file)
                    ToolResult(
                        content = "Generated image saved to $relative (${Workspace.humanSize(file.length())}). " +
                            "It is already displayed to the user.",
                        imageUri = Uri.fromFile(file).toString(),
                    )
                }
            }.getOrElse { ToolResult.error("image generation failed: ${it.message}") }
        }
}

/** OCR over a workspace file or an attachment URI. */
class OcrTool(private val engine: OcrEngine) : AgentTool {

    override val name = "ocr_image"
    override val description =
        "Extract text from an image using on-device OCR (falls back to Mistral OCR when configured). " +
            "Accepts a workspace path or a content:// URI from an attachment."
    override val parameters = schema(
        "path" to stringProp("Workspace-relative image path, or a content:// / file:// URI."),
        required = listOf("path"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val path = args.requireString("path")
        context.onProgress(path)

        val uri: Uri = when {
            path.startsWith("content://") || path.startsWith("file://") -> path.toUri()
            else -> {
                val file = Workspace.resolve(context.appContext, path)
                    ?: return ToolResult.error("path escapes the workspace")
                if (!file.isFile) return ToolResult.error("no such file: $path")
                Uri.fromFile(file)
            }
        }

        val result = engine.recognize(context.appContext, uri, context.settings.mistralOcrKey)
        return result.fold(
            onSuccess = { ocr ->
                if (ocr.text.isBlank()) ToolResult("No text found in $path.")
                else ToolResult("Text extracted from $path via ${ocr.engine}:\n\n${ocr.text}")
            },
            onFailure = { ToolResult.error("OCR failed: ${it.message}") },
        )
    }
}
