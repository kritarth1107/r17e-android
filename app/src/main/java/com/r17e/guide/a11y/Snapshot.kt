package com.r17e.guide.a11y

import org.json.JSONArray
import org.json.JSONObject

/** Plain copy of one on-screen node. Never holds an AccessibilityNodeInfo. */
data class SnapNode(
    val id: String,          // stable hash ID
    val role: String,        // r: short class name
    val text: String?,       // txt (null for password fields)
    val desc: String?,       // content description
    val viewId: String?,     // vid: resource id without package
    val left: Int, val top: Int, val right: Int, val bottom: Int, // b
    val clickable: Boolean,  // c
    val editable: Boolean,   // e
    val password: Boolean,   // pw
) {
    val label: String get() = text?.takeIf { it.isNotBlank() } ?: desc?.takeIf { it.isNotBlank() } ?: viewId ?: role

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("r", role)
        text?.let { put("txt", it) }
        desc?.let { put("desc", it) }
        viewId?.let { put("vid", it) }
        put("b", JSONArray(listOf(left, top, right, bottom)))
        if (clickable) put("c", 1)
        if (editable) put("e", 1)
        if (password) put("pw", 1)
    }
}

data class Snapshot(
    val pkg: String,
    val screen: String,
    val width: Int,
    val height: Int,
    val nodes: List<SnapNode>,
    val takenAtMs: Long = System.currentTimeMillis(),
    /** Safety notes for the model (e.g. "pin_entry_screen"); added by the safety layer. */
    val flags: List<String> = emptyList(),
) {
    fun node(id: String): SnapNode? = nodes.firstOrNull { it.id == id }
    val clickables: List<SnapNode> get() = nodes.filter { it.clickable }

    fun toJson(): String = JSONObject().apply {
        put("pkg", pkg); put("screen", screen); put("w", width); put("h", height)
        if (flags.isNotEmpty()) put("safety", JSONArray(flags))
        put("nodes", JSONArray().apply { nodes.forEach { put(it.toJson()) } })
    }.toString()
}
