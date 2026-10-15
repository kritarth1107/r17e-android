package com.r17e.guide.ui

import androidx.compose.ui.graphics.Color
import com.r17e.guide.i18n.Lang
import com.r17e.guide.i18n.S
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class DesignSystemTest {
    private fun lum(c: Color): Double {
        fun ch(v: Float) = if (v <= 0.03928f) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        return 0.2126 * ch(c.red) + 0.7152 * ch(c.green) + 0.0722 * ch(c.blue)
    }
    private fun ratio(a: Color, b: Color): Double { val x = lum(a); val y = lum(b); return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05) }

    @Test fun textPairsMeetWcagAA() {
        val P = Palette
        val pairs = mapOf(
            "onSurface/surface" to (P.onSurface to P.surface), "onSurfaceVariant/surface" to (P.onSurfaceVariant to P.surface),
            "onSurfaceVariant/surfaceLow" to (P.onSurfaceVariant to P.surfaceLow), "onPrimary/primary" to (P.onPrimary to P.primary),
            "primary/surface" to (P.primary to P.surface), "onPrimaryContainer/primaryContainer" to (P.onPrimaryContainer to P.primaryContainer),
            "onErrorContainer/errorContainer" to (P.onErrorContainer to P.errorContainer), "onSuccess/successContainer" to (P.onSuccessContainer to P.successContainer),
            "onWarning/warningContainer" to (P.onWarningContainer to P.warningContainer), "success/surface" to (P.success to P.surface),
            "D onSurface/surface" to (P.dOnSurface to P.dSurface), "D onSurfaceVariant/surface" to (P.dOnSurfaceVariant to P.dSurface),
            "D onSurfaceVariant/container" to (P.dOnSurfaceVariant to P.dSurfaceContainer), "D onPrimary/primary" to (P.dOnPrimary to P.dPrimary),
            "D primary/surface" to (P.dPrimary to P.dSurface), "D onPrimaryContainer/primaryContainer" to (P.dOnPrimaryContainer to P.dPrimaryContainer),
            "D onErrorContainer/errorContainer" to (P.dOnErrorContainer to P.dErrorContainer), "D onSuccess/successContainer" to (P.dOnSuccessContainer to P.dSuccessContainer),
            "D onWarning/warningContainer" to (P.dOnWarningContainer to P.dWarningContainer),
        )
        for ((name, p) in pairs) { val r = ratio(p.first, p.second); assertTrue("$name contrast ${"%.2f".format(r)} < 4.5", r >= 4.5) }
    }

    @Test fun noEmojiOrDecorativeSymbolsInUiStrings() {
        for (k in S.keys) for (l in Lang.values()) {
            val s = S.get(l, k)
            val bad = s.codePoints().filter { cp -> cp in 0x1F000..0x1FAFF || cp in 0x2600..0x27BF || cp in 0x2190..0x21FF || cp in 0x2B00..0x2BFF || cp == 0x2713 || cp == 0x23FB }.count()
            assertTrue("$k/$l has emoji/symbol: $s", bad == 0L)
        }
    }
}
