package com.hertzds.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Direct Vibrator-based haptics. Compose's `LocalHapticFeedback` routes through
 * `View.performHapticFeedback`, which several OEMs silently no-op unless the
 * system "touch feedback" toggle is on — a different switch than "vibration"
 * and one users regularly leave off, which read as "haptics don't work" even
 * though the calls were firing correctly. Vibrating directly only depends on
 * the phone's master vibration switch, which the user does control here.
 */
class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /** A very light tick — used per streamed chunk while the AI is typing. */
    fun tick() = pulse(10L, 36)

    /** A firmer pulse — used when the user sends, and when a turn starts/ends. */
    fun strong() = pulse(28L, 200)

    private fun pulse(durationMs: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        v.vibrate(VibrationEffect.createOneShot(durationMs, amplitude.coerceIn(1, 255)))
    }
}
