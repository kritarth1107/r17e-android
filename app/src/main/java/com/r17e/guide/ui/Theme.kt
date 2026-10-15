package com.r17e.guide.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * R17E design system.
 * - One calm primary (trust blue) on neutral surfaces; status colours (success / warning / error) used sparingly.
 * - All text/background pairs meet WCAG AA for large text and almost all meet AAA (checked in ContrastTest).
 * - 8dp spacing grid, 48dp+ touch targets, type scale tuned for older eyes (body 18-20sp) but not cartoonish.
 */
object Space {
    val xs = 4.dp; val s1 = 8.dp; val s2 = 16.dp; val s3 = 24.dp; val s4 = 32.dp; val s5 = 40.dp; val s6 = 48.dp
    val touch = 56.dp      // minimum height for primary controls (above the 48dp guideline)
    val screen = 24.dp     // screen edge padding
}

object Motion { const val short = 180; const val medium = 300; const val breathe = 1400 }

/** Status colours that Material's scheme does not have. */
@Immutable
data class StatusColors(
    val success: Color, val onSuccess: Color, val successContainer: Color, val onSuccessContainer: Color,
    val warning: Color, val warningContainer: Color, val onWarningContainer: Color,
)

object Palette {
    // Light
    val primary = Color(0xFF1C4ED8); val onPrimary = Color.White
    val primaryContainer = Color(0xFFDCE6FF); val onPrimaryContainer = Color(0xFF0A1F5C)
    val surface = Color(0xFFFBFBFD); val onSurface = Color(0xFF14171F); val onSurfaceVariant = Color(0xFF4A5160)
    val surfaceLow = Color(0xFFF5F6F9); val surfaceContainer = Color(0xFFEFF1F5); val surfaceHigh = Color(0xFFE8EBF1)
    val outline = Color(0xFF737B8C); val outlineVariant = Color(0xFFD5DAE3)
    val error = Color(0xFFB3261E); val errorContainer = Color(0xFFFDE7E5); val onErrorContainer = Color(0xFF5C0B07)
    val success = Color(0xFF146C2E); val successContainer = Color(0xFFDDF3E2); val onSuccessContainer = Color(0xFF0A3A18)
    val warning = Color(0xFF8A5300); val warningContainer = Color(0xFFFFF0D6); val onWarningContainer = Color(0xFF3D2500)
    // Dark
    val dPrimary = Color(0xFFAFC6FF); val dOnPrimary = Color(0xFF0A2A7A)
    val dPrimaryContainer = Color(0xFF1F3A8A); val dOnPrimaryContainer = Color(0xFFDCE6FF)
    val dSurface = Color(0xFF0F1115); val dOnSurface = Color(0xFFE6E8EE); val dOnSurfaceVariant = Color(0xFFB8BECA)
    val dSurfaceLow = Color(0xFF15181D); val dSurfaceContainer = Color(0xFF1B1E24); val dSurfaceHigh = Color(0xFF242830)
    val dOutline = Color(0xFF8A91A0); val dOutlineVariant = Color(0xFF353A45)
    val dError = Color(0xFFFFB4AB); val dErrorContainer = Color(0xFF5C1410); val dOnErrorContainer = Color(0xFFFFDAD6)
    val dSuccess = Color(0xFF86DBA0); val dSuccessContainer = Color(0xFF11361D); val dOnSuccessContainer = Color(0xFFC9F2D4)
    val dWarning = Color(0xFFF6C177); val dWarningContainer = Color(0xFF3D2A00); val dOnWarningContainer = Color(0xFFFFE2B5)
}

private val Light = lightColorScheme(
    primary = Palette.primary, onPrimary = Palette.onPrimary,
    primaryContainer = Palette.primaryContainer, onPrimaryContainer = Palette.onPrimaryContainer,
    secondary = Palette.onSurfaceVariant, onSecondary = Color.White,
    secondaryContainer = Palette.surfaceHigh, onSecondaryContainer = Palette.onSurface,
    background = Palette.surface, onBackground = Palette.onSurface,
    surface = Palette.surface, onSurface = Palette.onSurface,
    surfaceVariant = Palette.surfaceContainer, onSurfaceVariant = Palette.onSurfaceVariant,
    surfaceContainerLowest = Color.White, surfaceContainerLow = Palette.surfaceLow,
    surfaceContainer = Palette.surfaceContainer, surfaceContainerHigh = Palette.surfaceHigh, surfaceContainerHighest = Color(0xFFE1E5EC),
    outline = Palette.outline, outlineVariant = Palette.outlineVariant,
    error = Palette.error, onError = Color.White, errorContainer = Palette.errorContainer, onErrorContainer = Palette.onErrorContainer,
)

private val Dark = darkColorScheme(
    primary = Palette.dPrimary, onPrimary = Palette.dOnPrimary,
    primaryContainer = Palette.dPrimaryContainer, onPrimaryContainer = Palette.dOnPrimaryContainer,
    secondary = Palette.dOnSurfaceVariant, onSecondary = Palette.dSurface,
    secondaryContainer = Palette.dSurfaceHigh, onSecondaryContainer = Palette.dOnSurface,
    background = Palette.dSurface, onBackground = Palette.dOnSurface,
    surface = Palette.dSurface, onSurface = Palette.dOnSurface,
    surfaceVariant = Palette.dSurfaceContainer, onSurfaceVariant = Palette.dOnSurfaceVariant,
    surfaceContainerLowest = Color(0xFF0B0D10), surfaceContainerLow = Palette.dSurfaceLow,
    surfaceContainer = Palette.dSurfaceContainer, surfaceContainerHigh = Palette.dSurfaceHigh, surfaceContainerHighest = Color(0xFF2C313A),
    outline = Palette.dOutline, outlineVariant = Palette.dOutlineVariant,
    error = Palette.dError, onError = Color(0xFF690005), errorContainer = Palette.dErrorContainer, onErrorContainer = Palette.dOnErrorContainer,
)

private val LightStatus = StatusColors(Palette.success, Color.White, Palette.successContainer, Palette.onSuccessContainer,
    Palette.warning, Palette.warningContainer, Palette.onWarningContainer)
private val DarkStatus = StatusColors(Palette.dSuccess, Color(0xFF00391A), Palette.dSuccessContainer, Palette.dOnSuccessContainer,
    Palette.dWarning, Palette.dWarningContainer, Palette.dOnWarningContainer)

val LocalStatus = staticCompositionLocalOf { LightStatus }

/** Large but restrained type scale (system Roboto / Noto Devanagari). */
val R17eType = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    headlineMedium = TextStyle(fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold),
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 20.sp, lineHeight = 30.sp),
    bodyMedium = TextStyle(fontSize = 18.sp, lineHeight = 27.sp),
    bodySmall = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    labelLarge = TextStyle(fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

val R17eShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

@Composable
fun R17eTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalStatus provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = R17eType, shapes = R17eShapes, content = content)
    }
}
