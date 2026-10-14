package com.r17e.guide.backend

import org.junit.Assert.*
import org.junit.Test

class LiveTokenProviderTest {
    private val ok = """{"sessionId":"s1","accessToken":"ya29.x","tokenType":"Bearer","expiresAt":"2030-01-01T00:00:00.000Z",
        "projectId":"p","location":"us-central1","model":"m","modelResource":"projects/p/locations/us-central1/publishers/google/models/m",
        "websocketUrl":"wss://us-central1-aiplatform.googleapis.com/ws/x"}"""

    @Test fun parsesSessionAndCaches() {
        var calls = 0
        val c = BackendClient("http://x", http = { url, _, bearer ->
            calls++; assertTrue(url.endsWith("/v1/live/session")); assertEquals("tok", bearer); 200 to ok
        })
        val p = LiveTokenProvider(c, { "tok" }, {}, { 0L })
        val creds = p.get()
        assertEquals("s1", creds.sessionId)
        assertEquals(1893456000000L, creds.expiresAtMs)
        assertFalse(creds.toString().contains("ya29"))
        p.get(); assertEquals(1, calls)
        p.get(forceNew = true); assertEquals(2, calls)
    }

    @Test fun reRegistersOnceOn401() {
        var tok = "old"; var reg = 0
        val c = BackendClient("http://x", http = { _, _, bearer -> if (bearer == "old") 401 to """{"error":"invalid_device_token"}""" else 200 to ok })
        val creds = LiveTokenProvider(c, { tok }, { reg++; tok = "new" }).get()
        assertEquals(1, reg); assertEquals("s1", creds.sessionId)
    }

    @Test fun surfaces503() {
        val c = BackendClient("http://x", http = { _, _, _ -> 503 to """{"error":"vertex_credentials_unavailable"}""" })
        val e = assertThrows(BackendException::class.java) { LiveTokenProvider(c, { "t" }, {}).get() }
        assertEquals("vertex_credentials_unavailable", e.error)
    }
}
