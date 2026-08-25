package com.hertzds.agent.tools

import com.hertzds.agent.AgentTool
import com.hertzds.agent.ToolContext
import com.hertzds.agent.ToolResult
import com.hertzds.agent.bool
import com.hertzds.agent.boolProp
import com.hertzds.agent.int
import com.hertzds.agent.intProp
import com.hertzds.agent.requireString
import com.hertzds.agent.schema
import com.hertzds.agent.string
import com.hertzds.agent.stringProp
import com.hertzds.data.repo.MemoryRepository
import kotlinx.serialization.json.JsonObject

class RememberTool(private val memories: MemoryRepository) : AgentTool {
    override val name = "remember"
    override val description =
        "Store a durable fact, preference or conclusion in long-term memory so it survives across chats. " +
            "Use for things the user will expect you to know later, not for chat small talk."
    override val parameters = schema(
        "title" to stringProp("Short label for the memory, e.g. 'Preferred programming language'."),
        "content" to stringProp("The fact to remember, written so it stands alone without context."),
        "scope" to stringProp("'global' (default) or 'chat' to keep it to this conversation.", listOf("global", "chat")),
        "pinned" to boolProp("Pin it so it is always injected into the system prompt."),
        required = listOf("title", "content"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val title = args.requireString("title")
        val content = args.requireString("content")
        val chatScoped = args.string("scope") == "chat"
        context.onProgress(title)
        val entity = memories.remember(
            title = title,
            content = content,
            chatId = if (chatScoped) context.chatId else null,
            pinned = args.bool("pinned") ?: false,
        )
        return ToolResult("Remembered \"${entity.title}\" (id ${entity.id.take(8)}).")
    }
}

class RecallTool(private val memories: MemoryRepository) : AgentTool {
    override val name = "recall"
    override val description =
        "Search long-term memory for previously stored facts. Use before saying you don't know something " +
            "the user may have told you earlier."
    override val parameters = schema(
        "query" to stringProp("What to look for."),
        "limit" to intProp("Maximum memories to return (default 6)."),
        required = listOf("query"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val query = args.requireString("query")
        context.onProgress(query)
        val results = memories.search(query, context.chatId, (args.int("limit") ?: 6).coerceIn(1, 20))
        if (results.isEmpty()) return ToolResult("No memories match \"$query\".")
        val rendered = results.joinToString("\n\n") { "[${it.id.take(8)}] ${it.title}\n${it.content}" }
        return ToolResult("Memories matching \"$query\":\n\n$rendered")
    }
}

class ForgetTool(private val memories: MemoryRepository) : AgentTool {
    override val name = "forget"
    override val description = "Delete a stored memory by its id (as shown by recall)."
    override val parameters = schema(
        "id" to stringProp("Memory id or its 8-character prefix."),
        required = listOf("id"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val id = args.requireString("id")
        val exact = memories.get(id)
        if (exact != null) {
            memories.forget(exact.id)
            return ToolResult("Forgot \"${exact.title}\".")
        }
        val match = memories.recent(null, 200).firstOrNull { it.id.startsWith(id) }
            ?: return ToolResult.error("no memory with id $id")
        memories.forget(match.id)
        return ToolResult("Forgot \"${match.title}\".")
    }
}
