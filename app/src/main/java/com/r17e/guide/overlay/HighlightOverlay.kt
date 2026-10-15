package com.r17e.guide.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.WindowManager
import com.r17e.guide.a11y.Snapshot

/** Full-screen, untouchable overlay that draws a pulsing ring + caption around a node. */
class HighlightOverlay(private val ctx: Context) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private var view: RingView? = null
    private var targetId: String? = null

    fun highlight(snap: Snapshot, nodeId: String, caption: String) {
        val n = snap.node(nodeId) ?: return hide()
        targetId = nodeId
        val v = view ?: RingView(ctx).also { attach(it); view = it }
        v.caption = caption
        v.label = com.r17e.guide.i18n.S.get((ctx.applicationContext as com.r17e.guide.R17eApp).prefs.lang, "next_step")
        v.setTarget(RectF(n.left.toFloat(), n.top.toFloat(), n.right.toFloat(), n.bottom.toFloat()))
    }

    /** Re-anchor to the node in the new snapshot, or hide if it's gone. */
    fun onSnapshot(snap: Snapshot) {
        val id = targetId ?: return
        val n = snap.node(id) ?: return hide()
        view?.setTarget(RectF(n.left.toFloat(), n.top.toFloat(), n.right.toFloat(), n.bottom.toFloat()))
    }

    fun hide() {
        targetId = null
        view?.let { it.stop(); runCatching { wm.removeView(it) } }
        view = null
    }

    private fun attach(v: View) {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { x = 0; y = 0 }
        wm.addView(v, lp)
    }

    /**
     * Ring + caption, drawn to match the app: a brand-blue rounded ring with a light halo (readable on any app),
     * a gentle "breathing" outer glow, and a Material-style caption card with a "Next step" label and a pointer.
     */
    private class RingView(ctx: Context) : View(ctx) {
        var caption = ""
        var label = ""
        private var target: RectF? = null
        private var pulse = 0f
        private val loc = IntArray(2)
        private val pal = OverlayPalette(ctx)
        private val d = resources.displayMetrics.density
        private val sp = resources.displayMetrics.scaledDensity
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = pal.ring; strokeWidth = 4 * d }
        private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = pal.ringHalo; strokeWidth = 8 * d }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = pal.ring }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = (pal.ring and 0x00FFFFFF) or 0x1A000000 }
        private val card = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.surface; setShadowLayer(12 * d, 0f, 4 * d, 0x40000000) }
        private val cardStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = pal.outline; strokeWidth = 1 * d }
        private val labelText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.primary; textSize = 14 * sp; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); letterSpacing = 0.04f }
        private val capText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.onSurface; textSize = 20 * sp; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }
        private val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pal.primary }
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1400; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.RESTART
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }

        init { setLayerType(LAYER_TYPE_SOFTWARE, null) } // shadow layer on the caption card

        fun setTarget(r: RectF) { target = r; invalidate() }
        fun stop() = anim.cancel()

        override fun onDraw(c: Canvas) {
            val t = target ?: return
            getLocationOnScreen(loc)
            val r = RectF(t).apply { offset(-loc[0].toFloat(), -loc[1].toFloat()) }
            val pad = 8 * d; val rad = 16 * d
            val box = RectF(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad)
            c.drawRoundRect(box, rad, rad, fill)
            val g = 10 * d * pulse
            glow.strokeWidth = 3 * d; glow.alpha = (140 * (1 - pulse)).toInt()
            c.drawRoundRect(box.left - g, box.top - g, box.right + g, box.bottom + g, rad + g, rad + g, glow)
            c.drawRoundRect(box, rad, rad, halo)
            c.drawRoundRect(box, rad, rad, ring)
            if (caption.isEmpty()) return
            val margin = 16 * d; val padH = 16 * d; val padV = 12 * d
            val maxW = (width - 2 * margin - 2 * padH - 12 * d).coerceAtLeast(1f)
            val lines = wrap(caption, maxW)
            val lineH = capText.textSize * 1.3f
            val textW = maxOf(lines.maxOf { capText.measureText(it) }, labelText.measureText(label))
            val cw = textW + 2 * padH + 12 * d
            val ch = padV * 2 + labelText.textSize * 1.4f + lineH * lines.size
            val cx = (box.centerX() - cw / 2).coerceIn(margin, maxOf(margin, width - cw - margin))
            val below = box.bottom + 14 * d + ch <= height - 24 * d
            val cy = if (below) box.bottom + 14 * d else (box.top - 14 * d - ch).coerceAtLeast(24 * d)
            val cardR = RectF(cx, cy, cx + cw, cy + ch)
            // pointer toward the ring
            val px = box.centerX().coerceIn(cardR.left + 20 * d, cardR.right - 20 * d)
            val path = Path().apply {
                if (below) { moveTo(px - 9 * d, cardR.top + 1); lineTo(px, cardR.top - 9 * d); lineTo(px + 9 * d, cardR.top + 1) }
                else { moveTo(px - 9 * d, cardR.bottom - 1); lineTo(px, cardR.bottom + 9 * d); lineTo(px + 9 * d, cardR.bottom - 1) }
                close()
            }
            c.drawRoundRect(cardR, 16 * d, 16 * d, card)
            c.drawRoundRect(cardR, 16 * d, 16 * d, cardStroke)
            c.drawPath(path, card)
            c.drawRoundRect(cardR.left + 10 * d, cardR.top + padV, cardR.left + 14 * d, cardR.bottom - padV, 2 * d, 2 * d, accent)
            val tx = cardR.left + padH + 12 * d
            c.drawText(label.uppercase(), tx, cardR.top + padV + labelText.textSize, labelText)
            val ty0 = cardR.top + padV + labelText.textSize * 1.4f
            lines.forEachIndexed { i, l -> c.drawText(l, tx, ty0 + lineH * (i + 0.8f), capText) }
        }

        private fun wrap(text: String, maxW: Float): List<String> {
            val out = ArrayList<String>()
            var rest = text.trim()
            while (rest.isNotEmpty() && out.size < 3) {
                var n = capText.breakText(rest, true, maxW, null).coerceAtLeast(1)
                if (n < rest.length) rest.lastIndexOf(' ', n).takeIf { it > 0 }?.let { n = it }
                out += rest.take(n).trim(); rest = rest.drop(n).trim()
            }
            if (rest.isNotEmpty()) out[out.lastIndex] = out.last().dropLast(1) + "…"
            return out
        }
    }
}
