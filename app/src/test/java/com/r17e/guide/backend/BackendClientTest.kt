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

    @Test fun registerParsesAndSendsAndroidPlatform() {
        val h = FakeHttp(201, """{"deviceId":"d1","deviceToken":"r17e_dev_x"}""")
        val r = BackendClient("http://10.0.2.2:8080/", h).register("install-123", "Pixel", "0.1")
        assertEquals(Registration("d1", "r17e_dev_x"), r)
        assertEquals("http://10.0.2.2:8080/v1/devices/register", h.url)
        val sent = JSONObject(h.sent)
        assertEquals("android", sent.getString("platform"))
        assertEquals("install-123", sent.getString("installId"))
        assertNull(h.bearer)
    }

    @Test fun errorStatusSurfacesBackendError() {
        val h = FakeHttp(400, """{"error":"invalid_body"}""")
        val e = assertThrows(BackendException::class.java) { BackendClient("http://x", h).register("i", null, null) }
        assertEquals(400, e.code); assertEquals("invalid_body", e.error)
    }
}
