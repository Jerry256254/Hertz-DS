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
import kotlin.math.sin

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
 * Compact bar visualizer for dictation — 24 bars, height from amplitude + fake history.
 */
@Composable
fun DictationWaveform(
    rms: Float, // 0..1
    modifier: Modifier = Modifier,
) {
    // generate pseudo-bars that react to rms with some randomness via time
    val time by rememberInfiniteTransition(label = "wave").animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "t"
    )
    Row(
        modifier.height(28.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        repeat(24) { i ->
            val phase = i * 0.6f
            val base = 0.18f + 0.55f * ((sin(time + phase) + 1f) / 2f)
            val h = (6 + base * 18 * (0.6f + rms)).dp
            Box(
                Modifier
                    .width(3.dp)
                    .height(h.coerceIn(3.dp, 22.dp))
                    .background(Color.White, androidx.compose.foundation.shape.RoundedCornerShape(50))
            )
        }
    }
}
