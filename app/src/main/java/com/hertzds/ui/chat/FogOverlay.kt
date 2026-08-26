package com.hertzds.ui.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hertzds.ui.theme.HertzPalette

/**
 * Subtle fog that breathes with voice amplitude. Stays low/high, never harsh.
 * Bottom fog for user, top fog for assistant. Alpha and height are driven by RMS 0..1.
 */
@Composable
fun VoiceFog(
    amplitude: Float, // 0..1 from mic/TTS RMS
    fromBottom: Boolean,
    modifier: Modifier = Modifier,
) {
    // Smooth the raw amplitude a bit
    var smooth by remember { mutableStateOf(0f) }
    LaunchedEffect(amplitude) {
        // simple low-pass
        smooth = smooth * 0.7f + amplitude * 0.3f
    }

    val infinite = rememberInfiniteTransition(label = "fogDrift")
    val drift by infinite.animateFloat(
        initialValue = -6f, targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "drift"
    )

    val height = (24 + smooth * 72).dp
    val alpha = (0.08f + smooth * 0.14f).coerceIn(0f, 0.22f)

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .alpha(alpha)
            .background(
                Brush.verticalGradient(
                    colors = if (fromBottom) {
                        listOf(Color.Transparent, HertzPalette.Signal.copy(alpha = 0.30f), HertzPalette.Signal.copy(alpha = 0.10f))
                    } else {
                        listOf(HertzPalette.Signal.copy(alpha = 0.10f), HertzPalette.Signal.copy(alpha = 0.30f), Color.Transparent)
                    }
                )
            )
    ) {
        // subtle horizontal drift via offset — cheap but pleasant
        Box(
            Modifier
                .fillMaxSize()
                .offset(x = drift.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, HertzPalette.Signal.copy(alpha = 0.07f), Color.Transparent)
                    )
                )
        )
    }
}

/**
 * Live bar visualizer for dictation — a genuine rolling history of the real mic
 * amplitude ([rms], 0..1, sourced from the platform STT's RMS callback or a raw
 * PCM RMS computation), not a synthetic animation. New samples enter on the
 * right and scroll left, like a real level meter.
 */
@Composable
fun DictationWaveform(
    rms: Float, // 0..1, real mic amplitude
    modifier: Modifier = Modifier,
    barCount: Int = 40,
) {
    val history = remember { androidx.compose.runtime.mutableStateListOf<Float>().apply { repeat(barCount) { add(0f) } } }
    LaunchedEffect(rms) {
        history.add(rms.coerceIn(0f, 1f))
        while (history.size > barCount) history.removeAt(0)
    }
    Row(
        modifier.height(28.dp).fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        history.forEach { level ->
            val animated by animateFloatAsState(targetValue = level, label = "bar")
            Box(
                Modifier
                    .weight(1f)
                    .height((4 + animated * 24).dp.coerceIn(4.dp, 28.dp))
                    .background(HertzPalette.Signal, androidx.compose.foundation.shape.RoundedCornerShape(50))
            )
        }
    }
}
