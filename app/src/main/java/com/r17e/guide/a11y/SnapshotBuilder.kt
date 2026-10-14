package com.r17e.guide.a11y

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo

/** Walks the tree once and copies what matters into [SnapNode]s. */
object SnapshotBuilder {
    const val MAX_NODES = 150
    private const val MAX_TEXT = 80

    fun build(root: AccessibilityNodeInfo, screen: String, width: Int, height: Int): Snapshot {
        val pkg = root.packageName?.toString() ?: "?"
        val out = ArrayList<SnapNode>()
        val screenRect = Rect(0, 0, width, height)
        walk(root, "0", out, screenRect)
        return Snapshot(pkg, screen, width, height, out)
    }

    private fun walk(n: AccessibilityNodeInfo, path: String, out: MutableList<SnapNode>, screenRect: Rect) {
        if (out.size >= MAX_NODES || !n.isVisibleToUser) return
        val r = Rect().also { n.getBoundsInScreen(it) }
        if (!r.isEmpty && Rect.intersects(r, screenRect)) {
            val pw = n.isPassword
            val txt = if (pw) null else n.text?.toString()?.trim()?.take(MAX_TEXT)?.ifEmpty { null }
            val desc = n.contentDescription?.toString()?.trim()?.take(MAX_TEXT)?.ifEmpty { null }
            val vid = n.viewIdResourceName?.substringAfter(":id/")
            val clickable = n.isClickable || n.isLongClickable
            val editable = n.isEditable
            val role = n.className?.toString()?.substringAfterLast('.') ?: "View"
            if (clickable || editable || txt != null || desc != null) {
                // Shared resource ids (e.g. every Settings row is "title") must not collapse to one hash, so the label is always part of the key.
                val key = "${n.packageName}|$role|${vid ?: ""}|${txt ?: desc ?: if (vid == null) path else ""}"
                out += SnapNode(
                    id = stableId(key, out),
                    role = role, text = txt, desc = desc, viewId = vid,
                    left = r.left, top = r.top, right = r.right, bottom = r.bottom,
                    clickable = clickable, editable = editable, password = pw,
                )
            }
        }
        for (i in 0 until n.childCount) {
            val c = n.getChild(i) ?: continue
            walk(c, "$path.$i", out, screenRect)
            if (out.size >= MAX_NODES) return
        }
    }

    /** Short hash of identity-ish fields so the same button keeps its ID across snapshots. */
    private fun stableId(key: String, existing: List<SnapNode>): String {
        var h = 0x811C9DC5.toInt()
        for (ch in key) { h = h xor ch.code; h *= 0x01000193 }
        var id = "n" + Integer.toHexString(h).padStart(8, '0').take(6)
        var k = 1
        while (existing.any { it.id == id }) id = id.take(7) + "_" + k++
        return id
    }
}
