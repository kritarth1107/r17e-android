package com.r17e.guide.overlay

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color

/** Overlay colours, matching the app's design system (ui/Theme.kt) in light and dark. */
class OverlayPalette(ctx: Context) {
    val dark = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    val primary = if (dark) 0xFFAFC6FF.toInt() else 0xFF1C4ED8.toInt()
    val onPrimary = if (dark) 0xFF0A2A7A.toInt() else Color.WHITE
    val surface = if (dark) 0xFF242830.toInt() else Color.WHITE
    val onSurface = if (dark) 0xFFE6E8EE.toInt() else 0xFF14171F.toInt()
    val onSurfaceVariant = if (dark) 0xFFB8BECA.toInt() else 0xFF4A5160.toInt()
    val outline = if (dark) 0xFF353A45.toInt() else 0xFFD5DAE3.toInt()
    val warningContainer = if (dark) 0xFF3D2A00.toInt() else 0xFFFFF0D6.toInt()
    val onWarningContainer = if (dark) 0xFFFFE2B5.toInt() else 0xFF3D2500.toInt()
    val errorContainer = if (dark) 0xFF5C1410.toInt() else 0xFFFDE7E5.toInt()
    val onErrorContainer = if (dark) 0xFFFFDAD6.toInt() else 0xFF5C0B07.toInt()
    val error = if (dark) 0xFFFFB4AB.toInt() else 0xFFB3261E.toInt()
    /** Ring: the brand blue reads on light and dark app screens when paired with a white/dark halo. */
    val ring = 0xFF2F6BFF.toInt()
    val ringHalo = if (dark) 0xCC0F1115.toInt() else 0xE6FFFFFF.toInt()
}
