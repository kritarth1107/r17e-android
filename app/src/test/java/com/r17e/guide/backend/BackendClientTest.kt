package com.r17e.guide.backend

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BackendClientTest {
    private class FakeHttp(val code: Int, val body: String) : BackendClient.Http {
        var url = ""; var sent = ""; var bearer: String? = null
        override fun post(url: String, json: String, bearer: String?): Pair<Int, String> {
            this.url = url; sent = json; this.bearer = bearer; return code to body
        }
    }

    @Test fun registerSendsAndroidPlatformAndKey() {
        val h = FakeHttp(201, """{"deviceId":"d1","keyAlg":"ES256","integrity":"skipped"}""")
        assertEquals("d1", BackendClient("http://10.0.2.2:8080/", h).register("install-123", "PUBKEY", "Pixel", "0.1"))
        assertEquals("http://10.0.2.2:8080/v1/devices/register", h.url)
        val sent = JSONObject(h.sent)
        assertEquals("android", sent.getString("platform")); assertEquals("PUBKEY", sent.getString("publicKey"))
        assertNull(h.bearer)
    }

    @Test fun tokensComputeExpiry() {
        val h = FakeHttp(200, """{"accessToken":"a","tokenType":"Bearer","expiresIn":900,"refreshToken":"r"}""")
        val t = BackendClient("http://x", h, now = { 1000L }).refresh("old")
        assertEquals(Tokens("a", "r", 901_000L), t); assertFalse(t.toString().contains("a,"))
    }

    @Test fun errorStatusSurfacesBackendError() {
        val h = FakeHttp(400, """{"error":"invalid_body"}""")
        val e = assertThrows(BackendException::class.java) { BackendClient("http://x", h).challenge("d") }
        assertEquals(400, e.code); assertEquals("invalid_body", e.error)
    }
}
