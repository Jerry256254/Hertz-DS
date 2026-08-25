package com.hertzds.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hertzds.data.prefs.ThemeMode

// ─────────────────────────────────────────────────────────────────────────────
// HERTZ-DS · "Pure Space Black" — Material You Expressive, gently squared
// Pure black canvas, light-gray type, pure-white signal. No blue.
// Squared-rounded: 12 / 14 / 16 / 20 — never pill, never circular excess.
// ─────────────────────────────────────────────────────────────────────────────

object HertzPalette {
    // Dark — pure black space
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF111214)
    val SurfaceHigh = Color(0xFF1C1E22)
    val SurfaceHigher = Color(0xFF25282E)
    val Hairline = Color(0xFF2A2E36)
    val HairlineStrong = Color(0xFF3A3F4B)
    val Ink = Color(0xFFECEFF3)          // light gray — primary text
    val InkMuted = Color(0xFF9AA0AE)      // muted
    val InkFaint = Color(0xFF6B7280)
    val Signal = Color(0xFFFFFFFF)       // pure white — replaces blue
    val SignalMuted = Color(0xFFD1D5DB)
    val SignalVeil = Color(0x18FFFFFF)   // white veil 10%
    val SignalVeilStrong = Color(0x30FFFFFF)
    val Positive = Color(0xFFFFFFFF)     // also white in this theme (user wants white)
    val PositiveAlt = Color(0xFF9AE6B4)  // keep for credits when needed, but use white by default
    val Negative = Color(0xFFFF6B6B)
    val Warning = Color(0xFFFFC24B)

    // Light — not pure black but keep white-accent language
    val BgL = Color(0xFFF6F8FB)
    val SurfaceL = Color.White
    val SurfaceHighL = Color(0xFFEFF2F7)
    val HairlineL = Color(0xFFE2E7EE)
    val InkL = Color(0xFF0F1319)
    val InkMutedL = Color(0xFF59636F)
    val InkFaintL = Color(0xFF98A1AC)
    val SignalL = Color(0xFF111214)
    val SignalVeilL = Color(0x14000000)
}

class HertzTokens(
    val xs: Int = 4,
    val s: Int = 8,
    val m: Int = 14,
    val l: Int = 18,
    val xl: Int = 26,
    val xxl: Int = 40,
)

val LocalHertz = staticCompositionLocalOf { HertzTokens() }

private fun darkScheme() = darkColorScheme(
    primary = HertzPalette.Signal,
    onPrimary = Color(0xFF000000),
    primaryContainer = HertzPalette.SurfaceHigh,
    onPrimaryContainer = HertzPalette.Ink,
    secondary = HertzPalette.SurfaceHigher,
    onSecondary = HertzPalette.Ink,
    tertiary = HertzPalette.Ink,
    onTertiary = HertzPalette.Bg,
    error = HertzPalette.Negative,
    background = HertzPalette.Bg,
    onBackground = HertzPalette.Ink,
    surface = HertzPalette.Surface,
    onSurface = HertzPalette.Ink,
    surfaceVariant = HertzPalette.SurfaceHigh,
    onSurfaceVariant = HertzPalette.InkMuted,
    outline = HertzPalette.Hairline,
    outlineVariant = HertzPalette.Hairline,
    scrim = Color(0x99000000),
)

private fun lightScheme() = lightColorScheme(
    primary = HertzPalette.SignalL,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE8EAED),
    onPrimaryContainer = HertzPalette.InkL,
    secondary = HertzPalette.SurfaceHighL,
    onSecondary = HertzPalette.InkL,
    tertiary = HertzPalette.InkL,
    error = Color(0xFFC0392B),
    background = HertzPalette.BgL,
    onBackground = HertzPalette.InkL,
    surface = HertzPalette.SurfaceL,
    onSurface = HertzPalette.InkL,
    surfaceVariant = HertzPalette.SurfaceHighL,
    onSurfaceVariant = HertzPalette.InkMutedL,
    outline = HertzPalette.HairlineL,
    outlineVariant = HertzPalette.HairlineL,
)

private val HertzType = androidx.compose.material3.Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontSize = 14.5.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, color = HertzPalette.InkMuted),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 0.4.sp, color = HertzPalette.InkMuted),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 0.5.sp, color = HertzPalette.InkFaint),
)

// Gently squared — Material You Expressive, never pill
private val HertzShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

data class HertzSemantic(
    val hairline: Color,
    val hairlineStrong: Color,
    val faintText: Color,
    val positive: Color,
    val warning: Color,
    val signalVeil: Color,
)

@Composable
fun hertzSemantic(): HertzSemantic =
    if (MaterialTheme.colorScheme.background == HertzPalette.Bg) {
        HertzSemantic(HertzPalette.Hairline, HertzPalette.HairlineStrong, HertzPalette.InkFaint, HertzPalette.Signal, HertzPalette.Warning, HertzPalette.SignalVeil)
    } else {
        HertzSemantic(HertzPalette.HairlineL, HertzPalette.HairlineL, HertzPalette.InkFaintL, HertzPalette.SignalL, Color(0xFF9A6B00), HertzPalette.SignalVeilL)
    }

@Composable
fun HertzTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val scheme: ColorScheme = if (dark) darkScheme() else lightScheme()
    androidx.compose.runtime.CompositionLocalProvider(LocalHertz provides HertzTokens()) {
        MaterialTheme(
            colorScheme = scheme,
            typography = HertzType,
            shapes = HertzShapes,
            content = content,
        )
    }
}
