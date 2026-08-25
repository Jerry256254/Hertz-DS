package com.hertzds.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hertzds.AppContainer
import com.hertzds.HertzApp
import com.hertzds.MainActivity
import com.hertzds.R
import com.hertzds.data.repo.MessageRole
import com.hertzds.data.repo.MessageStatus
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Wakes up periodically, finds scheduled tasks whose nextRunAt has passed and
 * runs each one through the agent unattended — the cron half of the app.
 */
class ScheduledTaskWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as HertzApp).container
        val now = System.currentTimeMillis()
        val due = container.database.scheduledTaskDao().due(now)
        for (task in due) {
            val resultText = runTask(container, task.chatId, task.title, task.prompt)
            val nextRun = now + task.intervalMinutes * 60_000L
            container.database.scheduledTaskDao().markRun(task.id, now, nextRun, resultText.take(500))
            if (task.notifyOnComplete) notify(task.title, resultText)
        }
        return Result.success()
    }

    private suspend fun runTask(
        container: AppContainer,
        chatId: String?,
        title: String,
        prompt: String,
    ): String {
        // A dedicated ghost chat keeps background runs out of the user's conversations.
        val chat = chatId?.let { container.chats.getChat(it) }
            ?: container.chats.createChat(
                title = "⏰ $title",
                model = container.settings.current().defaultModel,
                systemPrompt = "You are running an unattended scheduled task. Execute it fully " +
                    "with tools where useful, then summarise the outcome in at most 3 sentences.",
            )

        container.chats.addMessage(
            container.chats.newMessage(chat.id, MessageRole.USER, prompt),
        )

        val settings = container.settings.current()
        var finalText = ""
        try {
            container.agentEngine.runTurn(chat.id, settings).collect { event ->
                if (event is com.hertzds.agent.AgentEvent.Delta) finalText += event.text
            }
        } catch (e: Exception) {
            finalText = "Task failed: ${e.message}"
            container.chats.addMessage(
                container.chats.newMessage(chat.id, MessageRole.SYSTEM, finalText, MessageStatus.ERROR),
            )
        }
        return finalText.ifBlank { "(no output)" }
    }

    private fun notify(title: String, body: String) {
        val context = applicationContext
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val intent = Intent(context, MainActivity::class.java)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, HertzApp.CHANNEL_AGENT)
            .setSmallIcon(R.drawable.ic_agent)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull().orEmpty().take(120))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(2000)))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(title.hashCode(), notification) }
    }

    companion object {
        private const val UNIQUE_NAME = "hertz_scheduled_tasks"

        /** Minimum periodic interval on Android is 15 minutes; tasks fire on the next tick. */
        fun ensureScheduled(context: Context) {
            val request = PeriodicWorkRequestBuilder<ScheduledTaskWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
