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

    private class RingView(ctx: Context) : View(ctx) {
        var caption = ""
        private var target: RectF? = null
        private var pulse = 0f
        private val loc = IntArray(2)
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.rgb(255, 176, 0)
        }
        private val capBg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(230, 20, 20, 20) }
        private val capText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE; textSize = 22f * resources.displayMetrics.scaledDensity
        }
        private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900; repeatCount = ValueAnimator.INFINITE; repeatMode = ValueAnimator.REVERSE
            addUpdateListener { pulse = it.animatedValue as Float; invalidate() }
            start()
        }

        fun setTarget(r: RectF) { target = r; invalidate() }
        fun stop() = anim.cancel()

        override fun onDraw(c: Canvas) {
            val t = target ?: return
            getLocationOnScreen(loc) // screen coords -> view coords
            val r = RectF(t).apply { offset(-loc[0].toFloat(), -loc[1].toFloat()) }
            val d = resources.displayMetrics.density
            val pad = (6 + 10 * pulse) * d
            ring.strokeWidth = (4 + 3 * pulse) * d
            ring.alpha = (255 - 120 * pulse).toInt()
            c.drawRoundRect(r.left - pad, r.top - pad, r.right + pad, r.bottom + pad, 16 * d, 16 * d, ring)
            if (caption.isNotEmpty()) {
                // Wrap to the screen width (max 3 lines); long captions used to crash coerceIn with an empty range.
                val maxW = (width - 40 * d).coerceAtLeast(1f)
                val lines = wrap(caption, maxW)
                val lineH = capText.textSize * 1.3f
                val tw = lines.maxOf { capText.measureText(it) }
                val h = lineH * lines.size + capText.textSize * 0.4f
                val x = (r.centerX() - tw / 2).coerceIn(20 * d, maxOf(20 * d, width - tw - 20 * d))
                var y = r.bottom + pad + 12 * d
                if (y + h > height) y = (r.top - pad - 12 * d - h).coerceAtLeast(0f)
                c.drawRoundRect(x - 12 * d, y, x + tw + 12 * d, y + h, 12 * d, 12 * d, capBg)
                lines.forEachIndexed { i, l -> c.drawText(l, x, y + lineH * (i + 0.85f), capText) }
            }
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
