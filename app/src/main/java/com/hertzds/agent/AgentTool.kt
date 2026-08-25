package com.hertzds.agent

import android.content.Context
import com.hertzds.data.prefs.Settings
import com.hertzds.deepseek.FunctionSpec
import com.hertzds.deepseek.ToolSpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Everything a tool may touch while running. */
class ToolContext(
    val appContext: Context,
    val chatId: String,
    val settings: Settings,
    /** Emits human-readable progress shown under the "using tool" chip. */
    val onProgress: (String) -> Unit = {},
)

data class ToolResult(
    /** What the model sees. */
    val content: String,
    /** Optional richer payload for the UI (e.g. a generated image). */
    val imageUri: String? = null,
    val isError: Boolean = false,
) {
    companion object {
        fun error(message: String) = ToolResult("ERROR: $message", isError = true)
    }
}

interface AgentTool {
    val name: String
    val description: String
    val parameters: JsonObject

    suspend fun execute(args: JsonObject, context: ToolContext): ToolResult

    fun spec(): ToolSpec = ToolSpec(
        function = FunctionSpec(name = name, description = description, parameters = parameters),
    )
}

// ---- JSON schema helpers -----------------------------------------------------

fun schema(
    vararg properties: Pair<String, JsonObject>,
    required: List<String> = emptyList(),
): JsonObject = buildJsonObject {
    put("type", "object")
    putJsonObject("properties") {
        properties.forEach { (key, value) -> put(key, value) }
    }
    put("required", kotlinx.serialization.json.JsonArray(required.map { JsonPrimitive(it) }))
}

fun stringProp(description: String, enum: List<String>? = null): JsonObject = buildJsonObject {
    put("type", "string")
    put("description", description)
    enum?.let { put("enum", kotlinx.serialization.json.JsonArray(it.map { v -> JsonPrimitive(v) })) }
}

fun intProp(description: String): JsonObject = buildJsonObject {
    put("type", "integer")
    put("description", description)
}

fun boolProp(description: String): JsonObject = buildJsonObject {
    put("type", "boolean")
    put("description", description)
}

fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString || it.content.isNotEmpty() }?.content

fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

fun JsonObject.bool(key: String): Boolean? = when (val value = this[key]) {
    is JsonPrimitive -> value.content.toBooleanStrictOrNull()
    else -> null
}

fun JsonObject.requireString(key: String): String =
    string(key)?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("missing required argument '$key'")
