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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.hertzds.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hertzds.data.prefs.ThemeMode

// ─────────────────────────────────────────────────────────────────────────────
// HERTZ-DS · "Deep Current" — dark-blue Material You Expressive, gently squared
// Very dark navy canvas, cool light-blue type, a vivid indigo-blue signal color
// on primary actions (send/call/active states). Squared-rounded: 12/14/16/20.
// ─────────────────────────────────────────────────────────────────────────────

object HertzPalette {
    // Dark — deep navy space
    val Bg = Color(0xFF080B14)
    val Surface = Color(0xFF10152A)
    val SurfaceHigh = Color(0xFF1A2140)
    val SurfaceHigher = Color(0xFF242C52)
    val Hairline = Color(0xFF2C355C)
    val HairlineStrong = Color(0xFF3B4570)
    val Ink = Color(0xFFE7EAFB)           // cool light-blue-white — primary text
    val InkMuted = Color(0xFFA3ABD1)      // muted
    val InkFaint = Color(0xFF6C74A0)
    val Signal = Color(0xFF0D9488)        // deep teal — primary actions (shark/ocean, not candy-blue)
    val SignalMuted = Color(0xFF5EEAD4)
    val OnSignal = Color(0xFFF4FFFC)      // icon/text drawn on the accent surface
    val SignalVeil = Color(0x290D9488)    // accent veil ~16%
    val SignalVeilStrong = Color(0x4D0D9488)
    val Positive = Color(0xFF0D9488)      // ties "success/active" language to the accent
    val PositiveAlt = Color(0xFF9AE6B4)
    val Negative = Color(0xFFFF5A6E)
    val Warning = Color(0xFFFFC24B)

    // Light — same blue accent, light neutral canvas
    val BgL = Color(0xFFF3F5FC)
    val SurfaceL = Color.White
    val SurfaceHighL = Color(0xFFE9EDFB)
    val HairlineL = Color(0xFFDCE1F5)
    val InkL = Color(0xFF10152A)
    val InkMutedL = Color(0xFF565F86)
    val InkFaintL = Color(0xFF8790B8)
    val SignalL = Color(0xFF0F766E)
    val SignalVeilL = Color(0x1F0F766E)
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
    onPrimary = HertzPalette.OnSignal,
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

/** The whole app's typeface — a single variable font file rendered at each weight via fontVariationSettings. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val UrbanistFamily = FontFamily(
    Font(R.font.urbanist, weight = FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.urbanist, weight = FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.urbanist, weight = FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.urbanist, weight = FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private val HertzType = androidx.compose.material3.Typography(
    displaySmall = TextStyle(fontFamily = UrbanistFamily, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = UrbanistFamily, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp, letterSpacing = (-0.2).sp),
    titleLarge = TextStyle(fontFamily = UrbanistFamily, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 23.sp),
    titleMedium = TextStyle(fontFamily = UrbanistFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = UrbanistFamily, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    bodyMedium = TextStyle(fontFamily = UrbanistFamily, fontSize = 14.5.sp, lineHeight = 21.sp, letterSpacing = 0.1.sp),
    bodySmall = TextStyle(fontFamily = UrbanistFamily, fontSize = 13.sp, lineHeight = 18.sp, color = HertzPalette.InkMuted),
    labelLarge = TextStyle(fontFamily = UrbanistFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = UrbanistFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.4.sp, color = HertzPalette.InkMuted),
    labelSmall = TextStyle(fontFamily = UrbanistFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 0.5.sp, color = HertzPalette.InkFaint),
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
