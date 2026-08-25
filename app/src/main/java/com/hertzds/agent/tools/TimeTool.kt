package com.hertzds.agent.tools

import com.hertzds.agent.AgentTool
import com.hertzds.agent.ToolContext
import com.hertzds.agent.ToolResult
import com.hertzds.agent.schema
import com.hertzds.deepseek.DeepSeekPricing
import kotlinx.serialization.json.JsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Current time plus the DeepSeek billing tier, so the agent can answer
 * "is it cheaper to run this later?" without guessing.
 */
class TimeTool : AgentTool {

    override val name = "get_time"
    override val description =
        "Get the current local and UTC time, and whether DeepSeek is currently billing at peak " +
            "(double) or off-peak (half) rates, including when the tier next changes."
    override val parameters = schema()

    override suspend fun execute(args: JsonObject, context: ToolContext): ToolResult {
        val now = Instant.now()
        val local = now.atZone(ZoneId.systemDefault())
        val utc = now.atZone(ZoneOffset.UTC)
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        val peak = DeepSeekPricing.isPeak(now)
        val untilChange = DeepSeekPricing.timeUntilTierChange(now)
        val hours = untilChange.toHours()
        val minutes = untilChange.toMinutes() % 60

        return ToolResult(
            buildString {
                appendLine("Local time: ${local.format(formatter)} (${ZoneId.systemDefault()})")
                appendLine("UTC time:   ${utc.format(formatter)}")
                appendLine()
                appendLine("DeepSeek billing tier: ${if (peak) "PEAK (full price)" else "OFF-PEAK (50% off)"}")
                appendLine("Peak window: 01:00-04:00 and 06:00-10:00 UTC, Monday to Friday.")
                append("Switches to ${if (peak) "off-peak" else "peak"} in ${hours}h ${minutes}m.")
            },
        )
    }
}
