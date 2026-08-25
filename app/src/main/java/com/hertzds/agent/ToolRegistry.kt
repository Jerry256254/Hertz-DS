package com.hertzds.agent

import com.hertzds.data.prefs.Settings
import com.hertzds.deepseek.ToolSpec

/** The agent's toolbox, filtered by what the user has enabled in settings. */
class ToolRegistry(private val tools: List<AgentTool>) {

    fun get(name: String): AgentTool? = tools.firstOrNull { it.name == name }

    fun enabledFor(settings: Settings): List<AgentTool> = tools.filter { tool ->
        when (tool.name) {
            "web_search", "fetch_url" -> settings.webSearchEnabled
            "list_files", "read_file", "write_file", "delete_file", "make_dir", "search_files" ->
                settings.fileToolsEnabled
            "remember", "recall", "forget" -> settings.memoryEnabled
            else -> true
        }
    }

    fun specsFor(settings: Settings): List<ToolSpec> = enabledFor(settings).map { it.spec() }

    fun all(): List<AgentTool> = tools
}
