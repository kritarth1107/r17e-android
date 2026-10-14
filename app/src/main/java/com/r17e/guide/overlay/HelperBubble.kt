package com.r17e.guide.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import com.r17e.guide.a11y.Snapshot
import kotlin.math.abs

/**
 * Floating helper button shown on every screen while the screen helper is on.
 * Tap: start/stop the voice helper. Drag: move it (it snaps to the nearest edge and remembers its place).
 * Its colour and icon show the phase (grey off, green listening, blue speaking, amber connecting, red offline).
 * Debug builds: long-press opens a node picker for testing the ring. It never acts on the other app.
 */
class HelperBubble(
    private val ctx: Context,
    private val highlight: HighlightOverlay,
    private val snapshot: () -> Snapshot?,
    private val onTap: () -> Unit,
) {
    private val prefs = ctx.getSharedPreferences("r17e_bubble", Context.MODE_PRIVATE)
    private val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke((4 * ctx.resources.displayMetrics.density).toInt(), Color.WHITE) }
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val d = ctx.resources.displayMetrics.density
    private var bubble: TextView? = null
    private var panel: View? = null
    private var listAdapter: ArrayAdapter<String>? = null
    private var ids: List<String> = emptyList()
    private val bubbleLp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.getInt("x", 0); y = prefs.getInt("y", (ctx.resources.displayMetrics.heightPixels * 0.55f).toInt())
    }

    private fun params(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
    )

    fun show() {
        if (bubble != null) return
        val b = TextView(ctx).apply {
            textSize = 30f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            val s = (72 * d).toInt(); width = s; height = s
            background = bg; elevation = 8 * d
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
        }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false; var downAt = 0L
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = bubbleLp.x; oy = bubbleLp.y; moved = false; downAt = e.eventTime }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) + abs(dy) > 16 * d) moved = true
                    if (moved) { bubbleLp.x = ox + dx.toInt(); bubbleLp.y = oy + dy.toInt(); wm.updateViewLayout(v, bubbleLp) }
                }
                MotionEvent.ACTION_UP -> when {
                    moved -> snapToEdge(v)
                    com.r17e.guide.BuildConfig.DEBUG && e.eventTime - downAt > 700 -> togglePanel()
                    else -> { v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY); onTap() }
                }
            }
            true
        }
        b.setOnClickListener { onTap() } // reached by TalkBack double-tap
        wm.addView(b, bubbleLp); bubble = b
        render(com.r17e.guide.session.HelperState.status.value.phase, label)
    }

    private var label = "Helper"

    /** Updates colour, icon and spoken description for the current phase. */
    fun render(phase: com.r17e.guide.session.Phase, description: String) {
        label = description
        val b = bubble ?: return
        val (color, icon) = when (phase) {
            com.r17e.guide.session.Phase.LISTENING -> Color.rgb(27, 127, 59) to "🎤"
            com.r17e.guide.session.Phase.SPEAKING -> Color.rgb(11, 79, 168) to "🔊"
            com.r17e.guide.session.Phase.CONNECTING, com.r17e.guide.session.Phase.RECONNECTING -> Color.rgb(154, 91, 0) to "…"
            com.r17e.guide.session.Phase.OFFLINE -> Color.rgb(179, 38, 30) to "🎤"
            com.r17e.guide.session.Phase.STOPPED -> Color.rgb(11, 79, 168) to "?"
        }
        bg.setColor(color); b.text = icon; b.contentDescription = description
        b.animate().cancel()
        if (phase == com.r17e.guide.session.Phase.LISTENING || phase == com.r17e.guide.session.Phase.SPEAKING) pulse(b) else { b.scaleX = 1f; b.scaleY = 1f }
    }

    private fun pulse(v: View) {
        v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(600).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(600).withEndAction {
                val p = com.r17e.guide.session.HelperState.status.value.phase
                if (bubble === v && (p == com.r17e.guide.session.Phase.LISTENING || p == com.r17e.guide.session.Phase.SPEAKING)) pulse(v)
            }.start()
        }.start()
    }

    private fun snapToEdge(v: View) {
        val w = ctx.resources.displayMetrics.widthPixels; val h = ctx.resources.displayMetrics.heightPixels
        bubbleLp.x = if (bubbleLp.x + v.width / 2 < w / 2) 0 else w - v.width
        bubbleLp.y = bubbleLp.y.coerceIn((80 * d).toInt(), h - v.height - (120 * d).toInt())
        runCatching { wm.updateViewLayout(v, bubbleLp) }
        prefs.edit().putInt("x", bubbleLp.x).putInt("y", bubbleLp.y).apply()
    }

    fun hide() {
        closePanel()
        bubble?.let { runCatching { wm.removeView(it) } }; bubble = null
    }

    fun onSnapshot(snap: Snapshot) { if (panel != null) refresh(snap) }

    private fun togglePanel() { if (panel != null) closePanel() else openPanel() }

    private fun openPanel() {
        val adapter = ArrayAdapter<String>(ctx, android.R.layout.simple_list_item_1, mutableListOf())
        listAdapter = adapter
        val list = ListView(ctx).apply {
            this.adapter = adapter
            setOnItemClickListener { _, _, pos, _ ->
                val snap = snapshot() ?: return@setOnItemClickListener
                val id = ids.getOrNull(pos) ?: return@setOnItemClickListener
                highlight.highlight(snap, id, "Tap here: " + (snap.node(id)?.label ?: ""))
                closePanel()
            }
        }
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(ctx).apply { text = "Clickable nodes"; textSize = 18f; setTextColor(Color.BLACK) },
                LinearLayout.LayoutParams(0, -2, 1f))
            addView(Button(ctx).apply { text = "Clear"; setOnClickListener { highlight.hide(); closePanel() } })
            addView(Button(ctx).apply { text = "Close"; setOnClickListener { closePanel() } })
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt(), (12 * d).toInt())
            background = GradientDrawable().apply { cornerRadius = 16 * d; setColor(Color.WHITE) }
            addView(header); addView(list, LinearLayout.LayoutParams(-1, -1))
        }
        val lp = params(WindowManager.LayoutParams.MATCH_PARENT, (ctx.resources.displayMetrics.heightPixels * 0.5f).toInt())
            .apply { gravity = Gravity.BOTTOM }
        wm.addView(root, lp); panel = root
        snapshot()?.let { refresh(it) }
    }

    private fun refresh(snap: Snapshot) {
        val nodes = snap.clickables
        ids = nodes.map { it.id }
        listAdapter?.apply {
            clear(); addAll(nodes.map { "${it.label}  [${it.role}]  ${it.id}" }); notifyDataSetChanged()
        }
    }

    private fun closePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null; listAdapter = null
    }
}
