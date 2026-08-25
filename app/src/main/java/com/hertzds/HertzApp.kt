package com.hertzds

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.hertzds.agent.AgentEngine
import com.hertzds.agent.ToolRegistry
import com.hertzds.agent.tools.CancelTaskTool
import com.hertzds.agent.tools.FetchUrlTool
import com.hertzds.agent.tools.GenerateImageTool
import com.hertzds.agent.tools.ListFilesTool
import com.hertzds.agent.tools.ListTasksTool
import com.hertzds.agent.tools.MakeDirTool
import com.hertzds.agent.tools.OcrEngine
import com.hertzds.agent.tools.OcrTool
import com.hertzds.agent.tools.ReadFileTool
import com.hertzds.agent.tools.DeleteFileTool
import com.hertzds.agent.tools.RememberTool
import com.hertzds.agent.tools.RecallTool
import com.hertzds.agent.tools.ForgetTool
import com.hertzds.agent.tools.ScheduleTaskTool
import com.hertzds.agent.tools.SearchFilesTool
import com.hertzds.agent.tools.TimeTool
import com.hertzds.agent.tools.WebSearchTool
import com.hertzds.agent.tools.WriteFileTool
import com.hertzds.data.db.HertzDatabase
import com.hertzds.data.prefs.SettingsRepository
import com.hertzds.data.repo.ApiKeyRepository
import com.hertzds.data.repo.ChatRepository
import com.hertzds.data.repo.MemoryRepository
import com.hertzds.deepseek.DeepSeekClient
import com.hertzds.voice.ModelDownloader
import com.hertzds.voice.VoiceManager
import com.hertzds.work.ScheduledTaskWorker
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.time.Duration

/** Manual DI: one container, created once, handed to Compose through the Application. */
class HertzApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ScheduledTaskWorker.ensureScheduled(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_AGENT,
                "Agent tasks",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Results of scheduled background agent runs" },
        )
    }

    companion object {
        const val CHANNEL_AGENT = "agent_tasks"
    }
}

class AppContainer(private val app: Application) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(Duration.ofSeconds(30))
        .readTimeout(Duration.ofMinutes(10))
        .writeTimeout(Duration.ofSeconds(60))
        .build()

    val database: HertzDatabase by lazy { HertzDatabase.build(app) }
    val settings: SettingsRepository by lazy { SettingsRepository(app) }
    val deepSeekClient: DeepSeekClient by lazy { DeepSeekClient(http, json) }

    val chats: ChatRepository by lazy {
        ChatRepository(database.chatDao(), database.messageDao(), database.attachmentDao(), database.usageDao())
    }
    val keys: ApiKeyRepository by lazy { ApiKeyRepository(database.apiKeyDao(), database.usageDao(), deepSeekClient) }
    val memories: MemoryRepository by lazy { MemoryRepository(database.memoryDao()) }

    val toolRegistry: ToolRegistry by lazy {
        ToolRegistry(
            listOf(
                WebSearchTool(http),
                FetchUrlTool(http),
                ListFilesTool(),
                ReadFileTool(),
                WriteFileTool(),
                DeleteFileTool(),
                MakeDirTool(),
                SearchFilesTool(),
                OcrTool(OcrEngine(http, json)),
                GenerateImageTool(http),
                RememberTool(memories),
                RecallTool(memories),
                ForgetTool(memories),
                ScheduleTaskTool(database.scheduledTaskDao()),
                ListTasksTool(database.scheduledTaskDao()),
                CancelTaskTool(database.scheduledTaskDao()),
                TimeTool(),
            ),
        )
    }

    val agentEngine: AgentEngine by lazy {
        AgentEngine(app, deepSeekClient, chats, keys, memories, toolRegistry, json)
    }

    val voiceManager: VoiceManager by lazy { VoiceManager(app, http, modelDownloader) }
    val modelDownloader: ModelDownloader by lazy { ModelDownloader(app, http) }
}
