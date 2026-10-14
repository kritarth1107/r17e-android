package com.r17e.guide.session

import com.r17e.guide.a11y.SnapNode
import com.r17e.guide.a11y.Snapshot
import com.r17e.guide.live.FunctionCall
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ScreenToolsTest {
    private fun node(id: String, txt: String) = SnapNode(id, "Button", txt, null, null, 0, 0, 10, 10, true, false, false)
    private val snap = Snapshot("com.whatsapp", "Home", 1080, 2400, listOf(node("n111111", "Calls"), node("n222222", "Chats")))

    private class Rec : Pointer {
        val points = mutableListOf<Pair<String, String>>(); var cleared = 0
        override fun point(snap: Snapshot, nodeId: String, caption: String) { points += nodeId to caption }
        override fun clear() { cleared++ }
    }

    private fun call(name: String, args: String = "{}") = FunctionCall("c", name, JSONObject(args))

    @Test fun declaresOnlyGuideTools() {
        val names = ScreenTools({ snap }, Rec()).declarations.let { a -> (0 until a.length()).map { a.getJSONObject(it).getString("name") } }
        assertEquals(listOf("get_screen", "highlight_node", "clear_highlight"), names)
    }

    @Test fun getScreenThenHighlightValidId() {
        val p = Rec(); val t = ScreenTools({ snap }, p)
        assertEquals("com.whatsapp", t.handle(call("get_screen")).getString("pkg"))
        val r = t.handle(call("highlight_node", """{"node_id":"n111111","caption":"Tap Calls"}"""))
        assertTrue(r.getBoolean("ok")); assertEquals("Calls", r.getString("label"))
        assertEquals(listOf("n111111" to "Tap Calls"), p.points)
    }

    @Test fun rejectsInventedIds() {
        val p = Rec(); val r = ScreenTools({ snap }, p).handle(call("highlight_node", """{"node_id":"nFAKE"}"""))
        assertTrue(r.getString("error").startsWith("unknown_node_id")); assertTrue(p.points.isEmpty())
    }

    @Test fun noScreenWithoutService() {
        assertTrue(ScreenTools({ null }, Rec()).handle(call("get_screen")).has("error"))
    }

    @Test fun clear() {
        val p = Rec(); ScreenTools({ snap }, p).handle(call("clear_highlight")); assertEquals(1, p.cleared)
    }
}

class ScreenToolsSafetyTest {
    @Test fun deniesPinKeypadHighlight() {
        val keys = listOf(SnapNode("t", "TextView", "Enter UPI PIN", null, null, 0, 0, 1, 1, false, false, false),
            SnapNode("k1", "Button", "1", null, null, 0, 0, 1, 1, true, false, false))
        var pointed = false
        val p = object : Pointer { override fun point(snap: Snapshot, nodeId: String, caption: String) { pointed = true }; override fun clear() {} }
        val r = ScreenTools({ Snapshot("in.org.npci.upiapp", "Pin", 1, 1, keys) }, p)
            .handle(FunctionCall("c", "highlight_node", JSONObject("""{"node_id":"k1"}""")))
        assertTrue(r.getString("error").startsWith("pin_keypad")); assertFalse(pointed)
    }
}
