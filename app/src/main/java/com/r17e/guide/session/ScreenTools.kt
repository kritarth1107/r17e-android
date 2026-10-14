package com.r17e.guide.session

import com.r17e.guide.a11y.Snapshot
import com.r17e.guide.live.FunctionCall
import com.r17e.guide.safety.SafetyPolicy
import org.json.JSONArray
import org.json.JSONObject

/** Draws on screen. Implementations must be thread safe (calls come from the websocket thread). */
interface Pointer {
    fun point(snap: Snapshot, nodeId: String, caption: String)
    fun clear()
}

/**
 * Model-facing screen tools. GUIDE-ONLY: the model can read a snapshot and ask for a ring around a node;
 * there is deliberately no tool that taps, types or scrolls.
 */
class ScreenTools(
    private val snapshot: () -> Snapshot?,
    private val pointer: Pointer,
    private val filter: (Snapshot) -> Snapshot = SafetyPolicy::filter,
) : ToolHandler {
    /** Last snapshot handed to the model; highlight ids are resolved against it, then against the live screen. */
    @Volatile private var lastSent: Snapshot? = null

    override val declarations: JSONArray = JSONArray()
        .put(fn("get_screen", "Get a compact snapshot of what is on the phone screen now: app package, screen name and visible nodes with ids, text, role (r), bounds (b) and clickable (c).", JSONObject()))
        .put(fn("highlight_node", "Draw a pulsing ring around one node from the latest get_screen snapshot to show the user where to tap. Does not tap.",
            JSONObject()
                .put("node_id", JSONObject().put("type", "STRING").put("description", "id of a node from the latest snapshot"))
                .put("caption", JSONObject().put("type", "STRING").put("description", "very short instruction shown next to the ring, in the user's language")),
            required = listOf("node_id")))
        .put(fn("clear_highlight", "Remove the ring from the screen.", JSONObject()))

    override fun handle(call: FunctionCall): JSONObject = when (call.name) {
        "get_screen" -> {
            val s = snapshot()
            if (s == null) err("screen_unavailable: ask the user to turn on the R17E accessibility helper")
            else filter(s).let { f -> lastSent = f; JSONObject(f.toJson()) }
        }
        "highlight_node" -> highlight(call.args.optString("node_id"), call.args.optString("caption").take(60))
        "clear_highlight" -> { pointer.clear(); JSONObject().put("ok", true) }
        else -> err("unknown_tool ${call.name}")
    }

    private fun highlight(id: String, caption: String): JSONObject {
        if (id.isEmpty()) return err("node_id required")
        val live = snapshot()?.let(filter)
        val snap = listOfNotNull(live, lastSent).firstOrNull { it.node(id) != null }
            ?: return err("unknown_node_id: call get_screen again and use an id from it")
        val n = snap.node(id)!!
        val v = SafetyPolicy.vet(snap, n)
        if (v is SafetyPolicy.Verdict.Deny) { pointer.clear(); return err(v.reason) }
        pointer.point(snap, id, SafetyPolicy.caption(caption))
        return JSONObject().put("ok", true).put("label", n.label).put("stale", snap !== live)
            .apply { if (v is SafetyPolicy.Verdict.AllowWithNote) put("note", v.note) }
    }

    private fun err(msg: String) = JSONObject().put("error", msg)

    private fun fn(name: String, desc: String, props: JSONObject, required: List<String> = emptyList()) =
        JSONObject().put("name", name).put("description", desc).put("parameters", JSONObject()
            .put("type", "OBJECT").put("properties", props)
            .apply { if (required.isNotEmpty()) put("required", JSONArray(required)) })
}
