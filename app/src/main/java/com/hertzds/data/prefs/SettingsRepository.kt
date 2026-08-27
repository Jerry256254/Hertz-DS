package com.hertzds.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hertzds.deepseek.Models
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hertz_settings")

enum class AppLanguage(val tag: String) { SYSTEM("system"), CZECH("cs"), ENGLISH("en") }

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class Settings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val defaultModel: String = Models.FLASH,
    val providerId: String = "deepseek",
    val customBaseUrl: String? = null,
    val customModel: String? = null,
    val defaultSystemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val temperature: Double = 0.7,
    val maxToolIterations: Int = 12,
    val autoNameChats: Boolean = true, // always on — no UI toggle
    val hapticsEnabled: Boolean = true,
    val streamingTts: Boolean = true,
    val ttsEngine: String = "system",
    val ttsVoiceId: String? = null,
    val ttsSpeed: Float = 1.0f,
    val sttEngine: String = "system",
    val sttModelId: String? = null,
    val handsFree: Boolean = false,
    val creditAlertUsd: Double = 2.0,
    val offPeakHint: Boolean = true,
    val memoryEnabled: Boolean = true,
    val webSearchEnabled: Boolean = true,
    val fileToolsEnabled: Boolean = true,
    val eulaAccepted: Boolean = false,
    val updateCheckEnabled: Boolean = true,
    val lastSeenVersion: String? = null,
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are Hertz-DS, a fully local agentic assistant running on the user's Android device. " +
                "You have tools for web search, reading web pages, local files, OCR, image generation, " +
                "long-term memory and scheduling. Prefer using a tool over guessing. " +
                "Answer in the language the user writes in. Be concise and concrete."
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME = stringPreferencesKey("theme")
        val MODEL = stringPreferencesKey("default_model")
        val PROVIDER = stringPreferencesKey("provider_id")
        val CUSTOM_BASE_URL = stringPreferencesKey("custom_base_url")
        val CUSTOM_MODEL = stringPreferencesKey("custom_model")
        val SYSTEM_PROMPT = stringPreferencesKey("system_prompt")
        val TEMPERATURE = doublePreferencesKey("temperature")
        val MAX_TOOL_ITERATIONS = intPreferencesKey("max_tool_iterations")
        val AUTO_NAME = booleanPreferencesKey("auto_name_chats")
        val HAPTICS = booleanPreferencesKey("haptics_enabled")
        val STREAMING_TTS = booleanPreferencesKey("streaming_tts")
        val TTS_ENGINE = stringPreferencesKey("tts_engine")
        val TTS_VOICE = stringPreferencesKey("tts_voice")
        val TTS_SPEED = doublePreferencesKey("tts_speed")
        val STT_ENGINE = stringPreferencesKey("stt_engine")
        val STT_MODEL = stringPreferencesKey("stt_model")
        val HANDS_FREE = booleanPreferencesKey("hands_free")
        val CREDIT_ALERT = doublePreferencesKey("credit_alert_usd")
        val OFF_PEAK_HINT = booleanPreferencesKey("off_peak_hint")
        val MEMORY = booleanPreferencesKey("memory_enabled")
        val WEB_SEARCH = booleanPreferencesKey("web_search_enabled")
        val FILE_TOOLS = booleanPreferencesKey("file_tools_enabled")
        val EULA = booleanPreferencesKey("eula_accepted")
        val UPDATE_CHECK = booleanPreferencesKey("update_check")
        val LAST_SEEN_VERSION = stringPreferencesKey("last_seen_version")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings(): Settings {
        val defaults = Settings()
        return Settings(
            language = this[Keys.LANGUAGE]?.let { tag ->
                AppLanguage.entries.firstOrNull { it.tag == tag }
            } ?: defaults.language,
            themeMode = this[Keys.THEME]?.let { name ->
                runCatching { ThemeMode.valueOf(name) }.getOrNull()
            } ?: defaults.themeMode,
            defaultModel = this[Keys.MODEL] ?: defaults.defaultModel,
            providerId = this[Keys.PROVIDER] ?: defaults.providerId,
            customBaseUrl = this[Keys.CUSTOM_BASE_URL],
            customModel = this[Keys.CUSTOM_MODEL],
            defaultSystemPrompt = this[Keys.SYSTEM_PROMPT] ?: defaults.defaultSystemPrompt,
            temperature = this[Keys.TEMPERATURE] ?: defaults.temperature,
            maxToolIterations = this[Keys.MAX_TOOL_ITERATIONS] ?: defaults.maxToolIterations,
            autoNameChats = this[Keys.AUTO_NAME] ?: defaults.autoNameChats,
            hapticsEnabled = this[Keys.HAPTICS] ?: defaults.hapticsEnabled,
            streamingTts = this[Keys.STREAMING_TTS] ?: defaults.streamingTts,
            ttsEngine = this[Keys.TTS_ENGINE] ?: defaults.ttsEngine,
            ttsVoiceId = this[Keys.TTS_VOICE],
            ttsSpeed = (this[Keys.TTS_SPEED] ?: defaults.ttsSpeed.toDouble()).toFloat(),
            sttEngine = this[Keys.STT_ENGINE] ?: defaults.sttEngine,
            sttModelId = this[Keys.STT_MODEL],
            handsFree = this[Keys.HANDS_FREE] ?: defaults.handsFree,
            creditAlertUsd = this[Keys.CREDIT_ALERT] ?: defaults.creditAlertUsd,
            offPeakHint = this[Keys.OFF_PEAK_HINT] ?: defaults.offPeakHint,
            memoryEnabled = this[Keys.MEMORY] ?: defaults.memoryEnabled,
            webSearchEnabled = this[Keys.WEB_SEARCH] ?: defaults.webSearchEnabled,
            fileToolsEnabled = this[Keys.FILE_TOOLS] ?: defaults.fileToolsEnabled,
            eulaAccepted = this[Keys.EULA] ?: defaults.eulaAccepted,
            updateCheckEnabled = this[Keys.UPDATE_CHECK] ?: defaults.updateCheckEnabled,
            lastSeenVersion = this[Keys.LAST_SEEN_VERSION],
        )
    }

    suspend fun setLanguage(value: AppLanguage) = put { it[Keys.LANGUAGE] = value.tag }
    suspend fun setTheme(value: ThemeMode) = put { it[Keys.THEME] = value.name }
    suspend fun setDefaultModel(value: String) = put { it[Keys.MODEL] = value }
    suspend fun setProviderId(value: String) = put { it[Keys.PROVIDER] = value }
    suspend fun setCustomBaseUrl(value: String?) = put {
        if (value == null) it.remove(Keys.CUSTOM_BASE_URL) else it[Keys.CUSTOM_BASE_URL] = value
    }
    suspend fun setCustomModel(value: String?) = put {
        if (value == null) it.remove(Keys.CUSTOM_MODEL) else it[Keys.CUSTOM_MODEL] = value
    }
    suspend fun setSystemPrompt(value: String) = put { it[Keys.SYSTEM_PROMPT] = value }
    suspend fun setTemperature(value: Double) = put { it[Keys.TEMPERATURE] = value }
    suspend fun setMaxToolIterations(value: Int) = put { it[Keys.MAX_TOOL_ITERATIONS] = value }
    suspend fun setAutoNameChats(value: Boolean) = put { it[Keys.AUTO_NAME] = value }
    suspend fun setHapticsEnabled(value: Boolean) = put { it[Keys.HAPTICS] = value }
    suspend fun setStreamingTts(value: Boolean) = put { it[Keys.STREAMING_TTS] = value }
    suspend fun setTtsEngine(value: String) = put { it[Keys.TTS_ENGINE] = value }
    suspend fun setTtsVoice(value: String?) = put {
        if (value == null) it.remove(Keys.TTS_VOICE) else it[Keys.TTS_VOICE] = value
    }
    suspend fun setTtsSpeed(value: Float) = put { it[Keys.TTS_SPEED] = value.toDouble() }
    suspend fun setSttEngine(value: String) = put { it[Keys.STT_ENGINE] = value }
    suspend fun setSttModel(value: String?) = put {
        if (value == null) it.remove(Keys.STT_MODEL) else it[Keys.STT_MODEL] = value
    }
    suspend fun setHandsFree(value: Boolean) = put { it[Keys.HANDS_FREE] = value }
    suspend fun setCreditAlert(value: Double) = put { it[Keys.CREDIT_ALERT] = value }
    suspend fun setOffPeakHint(value: Boolean) = put { it[Keys.OFF_PEAK_HINT] = value }
    suspend fun setMemoryEnabled(value: Boolean) = put { it[Keys.MEMORY] = value }
    suspend fun setWebSearchEnabled(value: Boolean) = put { it[Keys.WEB_SEARCH] = value }
    suspend fun setFileToolsEnabled(value: Boolean) = put { it[Keys.FILE_TOOLS] = value }
    suspend fun setEulaAccepted(value: Boolean) = put { it[Keys.EULA] = value }
    suspend fun setUpdateCheckEnabled(value: Boolean) = put { it[Keys.UPDATE_CHECK] = value }
    suspend fun setLastSeenVersion(value: String) = put { it[Keys.LAST_SEEN_VERSION] = value }

    private suspend fun put(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }
}
