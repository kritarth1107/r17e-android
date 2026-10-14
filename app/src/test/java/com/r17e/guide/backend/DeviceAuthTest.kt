package com.r17e.guide.backend

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/** Fake backend that really verifies ES256 signatures, like r17e-backend does. */
class DeviceAuthTest {
    private class JvmSigner : DeviceSigner {
        var kp = gen(); var resets = 0
        private fun gen() = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        override fun publicKeyB64() = Base64.getEncoder().encodeToString(kp.public.encoded)
        override fun sign(message: String) = Base64.getEncoder().encodeToString(
            Signature.getInstance("SHA256withECDSA").apply { initSign(kp.private); update(message.toByteArray()) }.sign())
        override fun reset() { kp = gen(); resets++ }
    }
    private class MemStore : AuthStore { override var installId: String? = null; override var deviceId: String? = null; override var refreshToken: String? = null }

    private class Server : BackendClient.Http {
        val keys = mutableMapOf<String, String>() // installId -> pubkey
        val devices = mutableMapOf<String, String>() // deviceId -> pubkey
        var nonce = 0; var issued = 0; val calls = mutableListOf<String>()
        override fun post(url: String, json: String, bearer: String?): Pair<Int, String> {
            val b = JSONObject(json); val path = url.substringAfter("http://x"); calls += path
            return when (path) {
                "/v1/devices/register" -> {
                    val inst = b.getString("installId"); val pk = b.getString("publicKey")
                    if (keys[inst] != null && keys[inst] != pk) 409 to """{"error":"install_id_already_registered"}"""
                    else { keys[inst] = pk; val id = "dev-$inst"; devices[id] = pk; 201 to """{"deviceId":"$id"}""" }
                }
                "/v1/auth/challenge" -> if (b.getString("deviceId") in devices) 200 to """{"challengeId":"c${++nonce}","message":"r17e-auth:v1:${b.getString("deviceId")}:n$nonce"}""" else 404 to """{"error":"unknown_device"}"""
                "/v1/auth/device/token" -> {
                    val id = b.getString("deviceId"); val msg = "r17e-auth:v1:$id:n${b.getString("challengeId").drop(1)}"
                    val pub = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(devices[id])))
                    val ok = Signature.getInstance("SHA256withECDSA").apply { initVerify(pub); update(msg.toByteArray()) }.verify(Base64.getDecoder().decode(b.getString("signature")))
                    if (ok) 200 to tok() else 401 to """{"error":"bad_signature"}"""
                }
                "/v1/auth/refresh" -> if (b.getString("refreshToken").startsWith("r")) 200 to tok() else 401 to """{"error":"invalid"}"""
                else -> 404 to "{}"
            }
        }
        private fun tok() = """{"accessToken":"a${++issued}","expiresIn":900,"refreshToken":"r$issued"}"""
    }

    private var clock = 0L
    private fun auth(s: Server, signer: DeviceSigner, store: AuthStore) =
        DeviceAuth(BackendClient("http://x", s, { clock }), signer, store, "Pixel", "0.1", { "install-1234" }, { clock })

    @Test fun registersSignsChallengeAndCaches() {
        val s = Server(); val store = MemStore(); val a = auth(s, JvmSigner(), store)
        assertEquals("a1", a.accessToken())
        assertEquals("dev-install-1234", store.deviceId); assertEquals("r1", store.refreshToken)
        assertEquals("a1", a.accessToken()); assertEquals(3, s.calls.size)
    }

    @Test fun refreshesAfterExpiryOrInvalidate() {
        val s = Server(); val a = auth(s, JvmSigner(), MemStore())
        a.accessToken(); a.invalidate()
        assertEquals("a2", a.accessToken()); assertEquals("/v1/auth/refresh", s.calls.last())
        clock += 900_000; assertEquals("a3", a.accessToken())
    }

    @Test fun lostKeyStartsNewInstall() {
        val s = Server(); s.keys["install-1234"] = "someone-else"
        var n = 0
        val signer = JvmSigner(); val store = MemStore()
        val a = DeviceAuth(BackendClient("http://x", s), signer, store, null, null, { "install-${n++}aaaaaa" })
        a.accessToken()
        assertTrue(store.deviceId!!.startsWith("dev-install-"))
        val s2 = Server(); s2.keys["install-0aaaaaa"] = "other"; val st2 = MemStore()
        DeviceAuth(BackendClient("http://x", s2), signer, st2, null, null, { "install-${n++}aaaaaa" }).accessToken()
        assertNotNull(st2.deviceId)
    }

    @Test fun reRegistersWhenServerForgotDevice() {
        val s = Server(); val store = MemStore().apply { deviceId = "dev-gone"; installId = "install-1234" }
        assertEquals("a1", auth(s, JvmSigner(), store).accessToken())
        assertEquals("dev-install-1234", store.deviceId)
    }
}
