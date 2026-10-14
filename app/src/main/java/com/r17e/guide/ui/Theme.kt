package com.r17e.guide.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

/** High-contrast palette (WCAG AA+ on white) and large type for older eyes. */
object R17eColors {
    val Ink = Color(0xFF111111)
    val Primary = Color(0xFF0B4FA8)      // deep blue, 8.6:1 on white
    val Listening = Color(0xFF1B7F3B)    // green
    val Speaking = Color(0xFF0B4FA8)
    val Warn = Color(0xFF9A5B00)         // amber text
    val Danger = Color(0xFFB3261E)
    val Ring = Color(0xFFFFB000)
    val Card = Color(0xFFF1F4F9)
}

@Composable
fun R17eTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = R17eColors.Primary, onPrimary = Color.White, background = Color.White, onBackground = R17eColors.Ink,
            surface = Color.White, onSurface = R17eColors.Ink, surfaceVariant = R17eColors.Card, error = R17eColors.Danger,
        ),
        typography = base.copy(
            headlineLarge = TextStyle(fontSize = 34.sp, lineHeight = 42.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 34.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 22.sp, lineHeight = 31.sp),
            labelLarge = TextStyle(fontSize = 22.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
        ),
        content = content,
    )
}
