package com.hertzds.deepseek

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

/**
 * DeepSeek bills per token and charges DOUBLE during peak hours.
 *
 * Verified against https://api-docs.deepseek.com/quick_start/pricing on 2026-08-25:
 *   "Peak hours are 01:00 - 04:00 and 06:00 - 10:00 UTC, Monday through Friday
 *    (all other hours are off-peak)."
 * Off-peak rates are exactly half of the peak rates, so we store peak rates and
 * apply [OFF_PEAK_MULTIPLIER] outside the windows.
 */
object DeepSeekPricing {

    const val OFF_PEAK_MULTIPLIER = 0.5
    const val PEAK_MULTIPLIER = 1.0

    /** USD per 1M tokens, at PEAK rates. */
    data class Rates(
        val cacheHitInput: Double,
        val cacheMissInput: Double,
        val output: Double,
    )

    /** Peak windows in UTC, applied Monday through Friday. */
    private val PEAK_WINDOWS = listOf(
        LocalTime.of(1, 0) to LocalTime.of(4, 0),
        LocalTime.of(6, 0) to LocalTime.of(10, 0),
    )

    private val PEAK_DAYS = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    private val PEAK_RATES = mapOf(
        Models.FLASH to Rates(cacheHitInput = 0.014, cacheMissInput = 0.44, output = 1.32),
        Models.VISION to Rates(cacheHitInput = 0.014, cacheMissInput = 0.44, output = 1.32),
        Models.PRO to Rates(cacheHitInput = 0.044, cacheMissInput = 1.32, output = 3.96),
    )

    private val FALLBACK_RATES = PEAK_RATES.getValue(Models.FLASH)

    fun peakRates(model: String): Rates = PEAK_RATES[model] ?: FALLBACK_RATES

    fun ratesAt(model: String, at: Instant): Rates {
        val multiplier = multiplierAt(at)
        val peak = peakRates(model)
        return Rates(
            cacheHitInput = peak.cacheHitInput * multiplier,
            cacheMissInput = peak.cacheMissInput * multiplier,
            output = peak.output * multiplier,
        )
    }

    fun isPeak(at: Instant): Boolean {
        val utc = at.atZone(ZoneOffset.UTC)
        if (utc.dayOfWeek !in PEAK_DAYS) return false
        val time = utc.toLocalTime()
        return PEAK_WINDOWS.any { (start, end) -> !time.isBefore(start) && time.isBefore(end) }
    }

    fun multiplierAt(at: Instant): Double =
        if (isPeak(at)) PEAK_MULTIPLIER else OFF_PEAK_MULTIPLIER

    /**
     * Cost in USD. DeepSeek reports cache hit/miss token counts separately; when a
     * response omits them we treat the whole prompt as a cache miss (the safe,
     * never-underestimating assumption).
     */
    fun cost(
        model: String,
        cacheHitTokens: Int,
        cacheMissTokens: Int,
        outputTokens: Int,
        at: Instant,
    ): Double {
        val rates = ratesAt(model, at)
        return (cacheHitTokens * rates.cacheHitInput +
            cacheMissTokens * rates.cacheMissInput +
            outputTokens * rates.output) / 1_000_000.0
    }

    /** How long until the current pricing tier flips, for the "wait and pay half" hint. */
    fun timeUntilTierChange(from: Instant): Duration {
        val currentlyPeak = isPeak(from)
        var probe = from.plusSeconds(60 - from.atZone(ZoneOffset.UTC).second.toLong())
        // Peak windows are aligned to the hour, so minute granularity over 8 days is exact.
        repeat(8 * 24 * 60) {
            if (isPeak(probe) != currentlyPeak) return Duration.between(from, probe)
            probe = probe.plusSeconds(60)
        }
        return Duration.ZERO
    }

    /** Multiplier applied once the current tier ends (1.0 -> peak, 0.5 -> off-peak). */
    fun nextMultiplier(from: Instant): Double =
        if (isPeak(from)) OFF_PEAK_MULTIPLIER else PEAK_MULTIPLIER
}

object Models {
    const val FLASH = "deepseek-v4-flash"
    const val PRO = "deepseek-v4-pro"
    const val VISION = "deepseek-v4-flash-vision-exp"

    val ALL = listOf(FLASH, PRO, VISION)

    fun label(id: String): String = when (id) {
        FLASH -> "V4 Flash"
        PRO -> "V4 Pro"
        VISION -> "V4 Flash Vision"
        else -> id
    }

    fun supportsVision(id: String): Boolean = id == VISION
}
