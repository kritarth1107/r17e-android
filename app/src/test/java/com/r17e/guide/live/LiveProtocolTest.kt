package com.r17e.guide.live

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class LiveProtocolTest {
    @Test fun setupCarriesModelAudioModalityAndTools() {
        val tools = JSONArray().put(JSONObject().put("name", "highlight_node"))
        val s = JSONObject(LiveProtocol.setup("projects/p/models/m", "be kind", tools)).getJSONObject("setup")
        assertEquals("projects/p/models/m", s.getString("model"))
        assertEquals("AUDIO", s.getJSONObject("generationConfig").getJSONArray("responseModalities").getString(0))
        assertEquals("highlight_node", s.getJSONArray("tools").getJSONObject(0).getJSONArray("functionDeclarations").getJSONObject(0).getString("name"))
    }

    @Test fun audioChunkIsBase64Pcm16k() {
        val c = JSONObject(LiveProtocol.audioChunk(byteArrayOf(1, 2, 3, 4), 2)).getJSONObject("realtimeInput")
            .getJSONArray("mediaChunks").getJSONObject(0)
        assertEquals("audio/pcm;rate=16000", c.getString("mimeType"))
        assertArrayEquals(byteArrayOf(1, 2), Base64.getDecoder().decode(c.getString("data")))
    }

    @Test fun parsesAudioInterruptAndToolCalls() {
        val audio = Base64.getEncoder().encodeToString(byteArrayOf(9, 9))
        val ev = LiveProtocol.parse("""{"serverContent":{"modelTurn":{"parts":[{"inlineData":{"mimeType":"audio/pcm;rate=24000","data":"$audio"}}]},"interrupted":true}}""")
        assertArrayEquals(byteArrayOf(9, 9), (ev[0] as LiveEvent.Audio).pcm24k)
        assertEquals(LiveEvent.Interrupted, ev[1])
        val tc = LiveProtocol.parse("""{"toolCall":{"functionCalls":[{"id":"c1","name":"highlight_node","args":{"node_id":"n1a2b3c"}}]}}""")
        val call = (tc.single() as LiveEvent.ToolCalls).calls.single()
        assertEquals("c1", call.id); assertEquals("n1a2b3c", call.args.getString("node_id"))
        assertEquals(listOf(LiveEvent.SetupComplete), LiveProtocol.parse("""{"setupComplete":{}}"""))
        assertTrue(LiveProtocol.parse("not json").isEmpty())
    }

    @Test fun toolResponseEchoesIdAndName() {
        val r = JSONObject(LiveProtocol.toolResponse(listOf(FunctionCall("c1", "clear_highlight", JSONObject()) to JSONObject().put("ok", true))))
        val fr = r.getJSONObject("toolResponse").getJSONArray("functionResponses").getJSONObject(0)
        assertEquals("c1", fr.getString("id")); assertTrue(fr.getJSONObject("response").getBoolean("ok"))
    }
}
