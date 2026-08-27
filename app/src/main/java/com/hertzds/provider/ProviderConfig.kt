package com.hertzds.provider

import com.hertzds.data.prefs.Settings

/**
 * A connection target for any OpenAI-compatible chat-completions API.
 *
 * The app was originally DeepSeek-only; this abstraction lets the user point it
 * at DeepSeek, OpenAI, OpenRouter, Groq, a local Ollama instance, or any other
 * endpoint that speaks the OpenAI `/chat/completions` SSE protocol. Only the
 * DeepSeek preset enables the balance endpoint and per-token pricing, because
 * those are DeepSeek-specific.
 */
enum class ProviderId(val id: String, val label: String) {
    DEEPSEEK("deepseek", "DeepSeek"),
    OPENAI("openai", "OpenAI"),
    OPENROUTER("openrouter", "OpenRouter"),
    GROQ("groq", "Groq"),
    OLLAMA("ollama", "Ollama (local)"),
    CUSTOM("custom", "Custom / Other");

    companion object {
        fun from(id: String): ProviderId = entries.firstOrNull { it.id == id } ?: DEEPSEEK
    }
}

data class ProviderConfig(
    val id: String,
    val label: String,
    val baseUrl: String,
    val chatPath: String = "/chat/completions",
    val authScheme: String = "Bearer",
    val suggestedModels: List<String>,
    val defaultModel: String,
    val isDeepSeek: Boolean = false,
    val supportsBalance: Boolean = false,
    val balancePath: String? = null,
) {
    val resolvedModel: String
        get() = defaultModel.ifBlank { suggestedModels.firstOrNull().orEmpty() }
}

/**
 * Builds the active [ProviderConfig] from saved [Settings]. When the custom
 * provider is selected the user-supplied base URL and model win; otherwise the
 * preset's defaults apply but the model can still be overridden per chat/setting.
 */
fun Settings.toProviderConfig(): ProviderConfig {
    val preset = ProviderId.from(providerId)
    val model = defaultModel.takeIf { it.isNotBlank() }
    return when (preset) {
        ProviderId.DEEPSEEK -> ProviderConfig(
            id = preset.id, label = preset.label,
            baseUrl = "https://api.deepseek.com",
            suggestedModels = com.hertzds.deepseek.Models.ALL,
            defaultModel = model ?: com.hertzds.deepseek.Models.FLASH,
            isDeepSeek = true, supportsBalance = true, balancePath = "/user/balance",
        )
        ProviderId.OPENAI -> ProviderConfig(
            id = preset.id, label = preset.label,
            baseUrl = "https://api.openai.com/v1",
            suggestedModels = listOf(
                "gpt-4o-mini", "gpt-4o", "gpt-4.1", "gpt-4.1-mini", "o3-mini", "o4-mini",
            ),
            defaultModel = model ?: "gpt-4o-mini",
        )
        ProviderId.OPENROUTER -> ProviderConfig(
            id = preset.id, label = preset.label,
            baseUrl = "https://openrouter.ai/api/v1",
            suggestedModels = listOf(
                "openai/gpt-4o-mini", "openai/gpt-4o", "anthropic/claude-3.5-sonnet",
                "meta-llama/llama-3.1-70b-instruct", "mistralai/mixtral-8x7b-instruct",
                "google/gemini-flash-1.5",
            ),
            defaultModel = model ?: "openai/gpt-4o-mini",
        )
        ProviderId.GROQ -> ProviderConfig(
            id = preset.id, label = preset.label,
            baseUrl = "https://api.groq.com/openai/v1",
            suggestedModels = listOf(
                "llama-3.3-70b-versatile", "llama-3.1-8b-instant",
                "llama-3.2-90b-vision-preview", "mixtral-8x7b-32768",
            ),
            defaultModel = model ?: "llama-3.3-70b-versatile",
        )
        ProviderId.OLLAMA -> ProviderConfig(
            id = preset.id, label = preset.label,
            baseUrl = (customBaseUrl ?: "http://localhost:11434/v1").removeSuffix("/"),
            suggestedModels = listOf("llama3", "llama3.1", "qwen2.5", "mistral", "gemma2"),
            defaultModel = model ?: "llama3",
            authScheme = "",
        )
        ProviderId.CUSTOM -> ProviderConfig(
            id = preset.id, label = preset.label,
            baseUrl = (customBaseUrl ?: "").removeSuffix("/"),
            suggestedModels = emptyList(),
            defaultModel = model ?: "",
        )
    }
}
