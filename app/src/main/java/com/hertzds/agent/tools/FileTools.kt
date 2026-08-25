package com.hertzds.agent.tools

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.io.File

/**
 * The agent's writable sandbox. Everything lives under
 * Android/data/com.hertzds/files/workspace, so no storage permission is needed and
 * uninstalling the app takes the data with it. Paths are canonicalised and checked
 * so the model cannot escape the sandbox with "../".
 */
object Workspace {

    fun root(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "workspace")
            .apply { if (!exists()) mkdirs() }

    fun resolve(context: Context, path: String): File? {
        val root = root(context).canonicalFile
        val cleaned = path.trim().removePrefix("/").ifEmpty { "." }
        val target = File(root, cleaned).canonicalFile
        return if (target.path == root.path || target.path.startsWith(root.path + File.separator)) {
            target
        } else {
            null
        }
    }

    fun relative(context: Context, file: File): String {
        val root = root(context).canonicalFile.path
        return file.canonicalFile.path.removePrefix(root).removePrefix(File.separator).ifEmpty { "." }
    }

    fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }
}

private const val MAX_READ_CHARS = 60_000

class ListFilesTool : AgentTool {
    override val name = "list_files"
    override val description =
        "List files and folders inside the agent workspace. Path is relative to the workspace root."
    override val parameters = schema(
        "path" to stringProp("Relative folder path, empty or '.' for the workspace root."),
        "recursive" to boolProp("Walk subfolders too (default false)."),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult =
        withContext(Dispatchers.IO) {
            val target = Workspace.resolve(context.appContext, args.string("path") ?: ".")
                ?: return@withContext ToolResult.error("path escapes the workspace")
            if (!target.exists()) return@withContext ToolResult.error("no such folder: ${args.string("path")}")
            if (!target.isDirectory) return@withContext ToolResult.error("not a folder")

            val recursive = args.bool("recursive") ?: false
            val entries = if (recursive) {
                target.walkTopDown().maxDepth(6).filter { it != target }.toList()
            } else {
                target.listFiles()?.toList().orEmpty()
            }.sortedBy { it.path }

            if (entries.isEmpty()) return@withContext ToolResult("(empty)")
            val rendered = entries.take(300).joinToString("\n") { file ->
                val marker = if (file.isDirectory) "DIR " else "FILE"
                "$marker  ${Workspace.relative(context.appContext, file)}  ${Workspace.humanSize(file.length())}"
            }
            ToolResult("Workspace listing (${entries.size} entries):\n$rendered")
        }
}

class ReadFileTool : AgentTool {
    override val name = "read_file"
    override val description =
        "Read a text file (txt, md, json, csv, log, code) from the workspace and return its content."
    override val parameters = schema(
        "path" to stringProp("Relative file path inside the workspace."),
        "max_chars" to intProp("Maximum characters to return (default 20000)."),
        required = listOf("path"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult =
        withContext(Dispatchers.IO) {
            val path = args.requireString("path")
            val file = Workspace.resolve(context.appContext, path)
                ?: return@withContext ToolResult.error("path escapes the workspace")
            if (!file.isFile) return@withContext ToolResult.error("no such file: $path")
            context.onProgress(path)

            val limit = (args.int("max_chars") ?: 20_000).coerceIn(100, MAX_READ_CHARS)
            val text = runCatching { file.readText() }
                .getOrElse { return@withContext ToolResult.error("cannot read $path: ${it.message}") }
            val body = text.take(limit)
            val suffix = if (text.length > limit) "\n[truncated, file is ${text.length} chars]" else ""
            ToolResult("$path (${Workspace.humanSize(file.length())}):\n$body$suffix")
        }
}

class WriteFileTool : AgentTool {
    override val name = "write_file"
    override val description =
        "Create or overwrite a text file in the workspace. Parent folders are created automatically."
    override val parameters = schema(
        "path" to stringProp("Relative file path inside the workspace."),
        "content" to stringProp("Full text content to write."),
        "append" to boolProp("Append instead of overwriting (default false)."),
        required = listOf("path", "content"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult =
        withContext(Dispatchers.IO) {
            val path = args.requireString("path")
            val content = args.string("content").orEmpty()
            val file = Workspace.resolve(context.appContext, path)
                ?: return@withContext ToolResult.error("path escapes the workspace")
            context.onProgress(path)
            runCatching {
                file.parentFile?.mkdirs()
                if (args.bool("append") == true) file.appendText(content) else file.writeText(content)
            }.getOrElse { return@withContext ToolResult.error("cannot write $path: ${it.message}") }
            ToolResult("Wrote ${content.length} characters to $path (${Workspace.humanSize(file.length())}).")
        }
}

class DeleteFileTool : AgentTool {
    override val name = "delete_file"
    override val description = "Delete a file or an empty folder from the workspace."
    override val parameters = schema(
        "path" to stringProp("Relative path inside the workspace."),
        required = listOf("path"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult =
        withContext(Dispatchers.IO) {
            val path = args.requireString("path")
            val file = Workspace.resolve(context.appContext, path)
                ?: return@withContext ToolResult.error("path escapes the workspace")
            if (!file.exists()) return@withContext ToolResult.error("no such path: $path")
            if (file.isDirectory && file.list()?.isNotEmpty() == true) {
                return@withContext ToolResult.error("folder $path is not empty")
            }
            if (!file.delete()) return@withContext ToolResult.error("could not delete $path")
            ToolResult("Deleted $path.")
        }
}

class MakeDirTool : AgentTool {
    override val name = "make_dir"
    override val description = "Create a folder (including parents) inside the workspace."
    override val parameters = schema(
        "path" to stringProp("Relative folder path inside the workspace."),
        required = listOf("path"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult =
        withContext(Dispatchers.IO) {
            val path = args.requireString("path")
            val dir = Workspace.resolve(context.appContext, path)
                ?: return@withContext ToolResult.error("path escapes the workspace")
            if (dir.exists()) return@withContext ToolResult("$path already exists.")
            if (!dir.mkdirs()) return@withContext ToolResult.error("could not create $path")
            ToolResult("Created folder $path.")
        }
}

class SearchFilesTool : AgentTool {
    override val name = "search_files"
    override val description =
        "Search the workspace for files whose name or text content contains a string."
    override val parameters = schema(
        "query" to stringProp("Text to look for."),
        "max_results" to intProp("Maximum matches to return (default 20)."),
        required = listOf("query"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult =
        withContext(Dispatchers.IO) {
            val query = args.requireString("query")
            val limit = (args.int("max_results") ?: 20).coerceIn(1, 100)
            val root = Workspace.root(context.appContext)
            context.onProgress(query)

            val matches = root.walkTopDown()
                .maxDepth(6)
                .filter { it.isFile && it.length() < 2_000_000 }
                .mapNotNull { file ->
                    val relative = Workspace.relative(context.appContext, file)
                    when {
                        relative.contains(query, ignoreCase = true) -> "$relative (name match)"
                        runCatching { file.readText().contains(query, ignoreCase = true) }.getOrDefault(false) ->
                            "$relative (content match)"
                        else -> null
                    }
                }
                .take(limit)
                .toList()

            if (matches.isEmpty()) ToolResult("No workspace file matches \"$query\".")
            else ToolResult("Matches for \"$query\":\n" + matches.joinToString("\n"))
        }
}
