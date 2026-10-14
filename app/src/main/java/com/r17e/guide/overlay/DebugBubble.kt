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
 * Small touchable floating bubble (debug only). Tap it to list the current clickable
 * nodes; tap a row to ring that node. Drag to move. It never acts on the other app.
 */
class DebugBubble(
    private val ctx: Context,
    private val highlight: HighlightOverlay,
    private val snapshot: () -> Snapshot?,
) {
    private val wm = ctx.getSystemService(WindowManager::class.java)
    private val d = ctx.resources.displayMetrics.density
    private var bubble: TextView? = null
    private var panel: View? = null
    private var listAdapter: ArrayAdapter<String>? = null
    private var ids: List<String> = emptyList()
    private val bubbleLp = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT).apply {
        gravity = Gravity.TOP or Gravity.START; x = (12 * d).toInt(); y = (300 * d).toInt()
    }

    private fun params(w: Int, h: Int) = WindowManager.LayoutParams(
        w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT,
    )

    fun show() {
        if (bubble != null) return
        val b = TextView(ctx).apply {
            text = "?"; textSize = 26f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            val s = (60 * d).toInt(); width = s; height = s
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(25, 118, 210)) }
            contentDescription = "Help"
        }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = bubbleLp.x; oy = bubbleLp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - sx; val dy = e.rawY - sy
                    if (abs(dx) + abs(dy) > 12 * d) moved = true
                    if (moved) { bubbleLp.x = ox + dx.toInt(); bubbleLp.y = oy + dy.toInt(); wm.updateViewLayout(v, bubbleLp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) togglePanel()
            }
            true
        }
        wm.addView(b, bubbleLp); bubble = b
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
