package com.hertzds.agent.tools

import com.hertzds.agent.AgentTool
import com.hertzds.agent.ToolContext
import com.hertzds.agent.ToolResult
import com.hertzds.agent.int
import com.hertzds.agent.intProp
import com.hertzds.agent.requireString
import com.hertzds.agent.schema
import com.hertzds.agent.string
import com.hertzds.agent.stringProp
import com.hertzds.data.db.ScheduledTaskDao
import com.hertzds.data.db.ScheduledTaskEntity
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Cron-like recurring jobs. The rows here are the source of truth; a periodic
 * WorkManager job wakes up, finds what is due and runs the agent unattended.
 */
class ScheduleTaskTool(private val dao: ScheduledTaskDao) : AgentTool {

    override val name = "schedule_task"
    override val description =
        "Schedule a recurring instruction for yourself, e.g. a morning briefing. " +
            "The task runs in the background and notifies the user when it finishes."
    override val parameters = schema(
        "title" to stringProp("Short name shown in the task list."),
        "prompt" to stringProp("The instruction to execute on every run, written as a standalone request."),
        "interval_minutes" to intProp("Minutes between runs. 1440 = daily, 60 = hourly. Minimum 15."),
        "time_of_day" to stringProp("Optional HH:mm local time for the first daily run, e.g. '07:30'."),
        required = listOf("title", "prompt", "interval_minutes"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val title = args.requireString("title")
        val prompt = args.requireString("prompt")
        val interval = (args.int("interval_minutes") ?: 1440).coerceAtLeast(15)
        val timeOfDay = args.string("time_of_day")?.let { parseTime(it) }

        val nextRun = nextRunAt(interval, timeOfDay)
        val task = ScheduledTaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            prompt = prompt,
            intervalMinutes = interval,
            timeOfDayMinutes = timeOfDay?.let { it.hour * 60 + it.minute },
            chatId = context.chatId,
            nextRunAt = nextRun,
            createdAt = System.currentTimeMillis(),
        )
        dao.upsert(task)
        context.onProgress(title)

        val formatted = Instant.ofEpochMilli(nextRun).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        return ToolResult(
            "Scheduled \"$title\" every $interval minutes. First run: $formatted (id ${task.id.take(8)}).",
        )
    }

    private fun parseTime(raw: String): LocalTime? = runCatching {
        LocalTime.parse(raw.trim(), DateTimeFormatter.ofPattern("H:mm"))
    }.getOrNull()

    private fun nextRunAt(intervalMinutes: Int, timeOfDay: LocalTime?): Long {
        val zone = ZoneId.systemDefault()
        if (timeOfDay == null) {
            return System.currentTimeMillis() + intervalMinutes * 60_000L
        }
        val today = LocalDate.now(zone).atTime(timeOfDay).atZone(zone).toInstant().toEpochMilli()
        return if (today > System.currentTimeMillis()) today else today + 24 * 60 * 60 * 1000L
    }
}

class ListTasksTool(private val dao: ScheduledTaskDao) : AgentTool {
    override val name = "list_tasks"
    override val description = "List the scheduled recurring tasks with their ids and next run time."
    override val parameters = schema()

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val tasks = dao.all()
        if (tasks.isEmpty()) return ToolResult("No scheduled tasks.")
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        val rendered = tasks.joinToString("\n") { task ->
            val next = Instant.ofEpochMilli(task.nextRunAt).atZone(ZoneId.systemDefault()).format(formatter)
            "[${task.id.take(8)}] ${task.title} — every ${task.intervalMinutes} min, next $next" +
                if (!task.enabled) " (disabled)" else ""
        }
        return ToolResult("Scheduled tasks:\n$rendered")
    }
}

class CancelTaskTool(private val dao: ScheduledTaskDao) : AgentTool {
    override val name = "cancel_task"
    override val description = "Cancel a scheduled task by id (or its 8-character prefix)."
    override val parameters = schema(
        "id" to stringProp("Task id from list_tasks."),
        required = listOf("id"),
    )

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val id = args.requireString("id")
        val exact = dao.get(id)
        if (exact != null) {
            dao.delete(exact.id)
            return ToolResult("Cancelled \"${exact.title}\".")
        }
        val match = dao.all().firstOrNull { it.id.startsWith(id) }
            ?: return ToolResult.error("no task with id $id")
        dao.delete(match.id)
        return ToolResult("Cancelled \"${match.title}\".")
    }
}
