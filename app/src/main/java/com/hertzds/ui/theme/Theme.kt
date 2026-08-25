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
// HERTZ-DS · "Signal on Black"
// Precizní tmavý instrument: hluboký grafát, hairline linky, jeden elektrický
// signál. Žádná karta v kartě, žádné stínové polštáře, monospace jen pro data.
// ─────────────────────────────────────────────────────────────────────────────

object HertzPalette {
    // Dark
    val Bg = Color(0xFF07080B)
    val Surface = Color(0xFF0D0F14)
    val SurfaceHigh = Color(0xFF151922)
    val Hairline = Color(0xFF20242F)
    val Ink = Color(0xFFF0F3F8)
    val InkMuted = Color(0xFF99A3B2)
    val InkFaint = Color(0xFF5D6675)
    val Signal = Color(0xFF45D6FF)
    val SignalDeep = Color(0xFF149EC4)
    val SignalVeil = Color(0x1A45D6FF)
    val Positive = Color(0xFF3FE08D)
    val Negative = Color(0xFFFF6B6B)
    val Warning = Color(0xFFFFC24B)

    // Light
    val BgL = Color(0xFFF6F8FB)
    val SurfaceL = Color.White
    val SurfaceHighL = Color(0xFFEFF2F7)
    val HairlineL = Color(0xFFE2E7EE)
    val InkL = Color(0xFF0F1319)
    val InkMutedL = Color(0xFF59636F)
    val InkFaintL = Color(0xFF98A1AC)
    val SignalL = Color(0xFF006E93)
    val SignalVeilL = Color(0x14006E93)
}

/** Spacing & radius tokens — the only numbers screens may reach for. */
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
    onPrimary = Color(0xFF00202B),
    primaryContainer = HertzPalette.SignalVeil,
    onPrimaryContainer = HertzPalette.Signal,
    secondary = HertzPalette.SurfaceHigh,
    onSecondary = HertzPalette.Ink,
    tertiary = HertzPalette.Positive,
    onTertiary = Color(0xFF002715),
    error = HertzPalette.Negative,
    background = HertzPalette.Bg,
    onBackground = HertzPalette.Ink,
    surface = HertzPalette.Surface,
    onSurface = HertzPalette.Ink,
    surfaceVariant = HertzPalette.SurfaceHigh,
    onSurfaceVariant = HertzPalette.InkMuted,
    outline = HertzPalette.Hairline,
    outlineVariant = HertzPalette.Hairline,
)

private fun lightScheme() = lightColorScheme(
    primary = HertzPalette.SignalL,
    onPrimary = Color.White,
    primaryContainer = HertzPalette.SignalVeilL,
    onPrimaryContainer = HertzPalette.SignalL,
    secondary = HertzPalette.SurfaceHighL,
    onSecondary = HertzPalette.InkL,
    tertiary = Color(0xFF0B7A48),
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

private val HertzShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Semantic aliases so screens never hardcode raw palette values. */
data class HertzSemantic(
    val hairline: Color,
    val faintText: Color,
    val positive: Color,
    val warning: Color,
    val signalVeil: Color,
)

@Composable
fun hertzSemantic(): HertzSemantic =
    if (MaterialTheme.colorScheme.background == HertzPalette.Bg) {
        HertzSemantic(HertzPalette.Hairline, HertzPalette.InkFaint, HertzPalette.Positive, HertzPalette.Warning, HertzPalette.SignalVeil)
    } else {
        HertzSemantic(HertzPalette.HairlineL, HertzPalette.InkFaintL, Color(0xFF0B7A48), Color(0xFF9A6B00), HertzPalette.SignalVeilL)
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
