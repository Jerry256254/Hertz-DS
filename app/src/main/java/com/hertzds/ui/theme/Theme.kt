package com.hertzds.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.hertzds.data.prefs.ThemeMode

// Hertz palette: near-black graphite, electric cyan signal colour.
private val Graphite = Color(0xFF0E1116)
private val SurfaceDark = Color(0xFF161B22)
private val SurfaceHigh = Color(0xFF1F2630)
private val Signal = Color(0xFF33CFFF)
private val SignalDim = Color(0xFF1993BD)
private val Ink = Color(0xFFE7EDF3)
private val InkMuted = Color(0xFF93A1AE)
private val Danger = Color(0xFFFF5D5D)
private val Money = Color(0xFF57D9A3)

private val DarkScheme = darkColorScheme(
    primary = Signal,
    onPrimary = Color(0xFF00232E),
    primaryContainer = Color(0xFF004860),
    onPrimaryContainer = Color(0xFFB8ECFF),
    secondary = SignalDim,
    onSecondary = Color(0xFF04202B),
    background = Graphite,
    onBackground = Ink,
    surface = SurfaceDark,
    onSurface = Ink,
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFF3A4552),
    error = Danger,
    tertiary = Money,
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00719A),
    onPrimary = Color.White,
    background = Color(0xFFF6F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EEF2),
    onBackground = Color(0xFF14181D),
)

private val HertzTypography = Typography(
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
)

@Composable
fun HertzTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkScheme else LightScheme,
        typography = HertzTypography,
        content = content,
    )
}
