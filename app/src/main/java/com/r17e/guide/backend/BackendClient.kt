package com.r17e.guide.backend

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Thrown for any non-2xx backend reply. [code] is the HTTP status, [error] the backend's error field. */
class BackendException(val code: Int, val error: String?) : IOException("backend $code: ${error ?: "?"}")

data class Challenge(val challengeId: String, val message: String)
data class Tokens(val accessToken: String, val refreshToken: String, val expiresAtMs: Long) {
    override fun toString() = "Tokens(<redacted>, expiresAtMs=$expiresAtMs)"
}

/** Minimal blocking client for r17e-backend. Call from a background thread. */
class BackendClient(
    private val baseUrl: String,
    private val http: Http = UrlConnectionHttp(),
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Transport seam so parsing can be unit tested without a server. */
    fun interface Http {
        /** Returns (status, body). */
        fun post(url: String, json: String, bearer: String?): Pair<Int, String>
        fun get(url: String, bearer: String?): Pair<Int, String> = throw UnsupportedOperationException("GET")
    }

    /** POST /v1/devices/register with this install's public key (base64 SPKI). Returns deviceId. */
    fun register(installId: String, publicKey: String, model: String?, appVersion: String?, integrityToken: String? = null): String {
        val body = JSONObject().apply {
            put("installId", installId); put("platform", "android"); put("publicKey", publicKey)
            model?.let { put("model", it.take(128)) }
            appVersion?.let { put("appVersion", it.take(64)) }
            integrityToken?.let { put("integrityToken", it) }
        }
        return call("/v1/devices/register", body, null).getString("deviceId")
    }

    fun challenge(deviceId: String): Challenge =
        call("/v1/auth/challenge", JSONObject().put("deviceId", deviceId), null)
            .let { Challenge(it.getString("challengeId"), it.getString("message")) }

    fun deviceToken(deviceId: String, challengeId: String, signatureB64: String): Tokens = tokens(
        call("/v1/auth/device/token", JSONObject().put("deviceId", deviceId).put("challengeId", challengeId).put("signature", signatureB64), null))

    fun refresh(refreshToken: String): Tokens = tokens(call("/v1/auth/refresh", JSONObject().put("refreshToken", refreshToken), null))

    /** POST /v1/live/session with the device access token. */
    fun liveSession(accessToken: String, goal: String? = null, appPackage: String? = null): LiveCredentials =
        LiveCredentials.fromJson(call("/v1/live/session", JSONObject().apply {
            goal?.let { put("goal", it.take(500)) }; appPackage?.let { put("appPackage", it.take(255)) }
        }, accessToken))

    fun endSession(accessToken: String, sessionId: String, summary: String? = null) {
        call("/v1/sessions/$sessionId/end", JSONObject().apply { summary?.let { put("summary", it.take(4000)) } }, accessToken)
    }

    /** GET /v1/recipes: this elder's saved recipes (own and caregiver-pushed). Returns raw JSON. */
    fun recipesJson(accessToken: String): String {
        val (code, text) = http.get(baseUrl.trimEnd('/') + "/v1/recipes", accessToken)
        if (code !in 200..299) throw BackendException(code, runCatching { JSONObject(text).optString("error") }.getOrNull())
        return text
    }

    /** POST /v1/family/link with a caregiver's invite code. Returns the family group name. */
    fun linkFamily(accessToken: String, code: String): String =
        call("/v1/family/link", JSONObject().put("code", code), accessToken).getJSONObject("group").optString("name")

    /** POST /v1/safety-events. Body is built by SafetyReporter (metadata only). */
    fun safetyEvent(accessToken: String, event: JSONObject) { call("/v1/safety-events", event, accessToken) }

    private fun tokens(o: JSONObject) =
        Tokens(o.getString("accessToken"), o.getString("refreshToken"), now() + o.optLong("expiresIn", 900) * 1000)

    internal fun call(path: String, body: JSONObject, bearer: String?): JSONObject {
        val (code, text) = http.post(baseUrl.trimEnd('/') + path, body.toString(), bearer)
        val o = runCatching { JSONObject(text) }.getOrNull()
        if (code !in 200..299) throw BackendException(code, o?.optString("error")?.ifEmpty { null })
        return o ?: throw BackendException(code, "bad_json")
    }
}

class UrlConnectionHttp(private val timeoutMs: Int = 10_000) : BackendClient.Http {
    override fun get(url: String, bearer: String?) = exec(url, "GET", null, bearer)
    override fun post(url: String, json: String, bearer: String?) = exec(url, "POST", json, bearer)

    private fun exec(url: String, method: String, json: String?, bearer: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method; c.doOutput = json != null
            c.connectTimeout = timeoutMs; c.readTimeout = timeoutMs
            c.setRequestProperty("Content-Type", "application/json")
            bearer?.let { c.setRequestProperty("Authorization", "Bearer $it") }
            json?.let { j -> c.outputStream.use { it.write(j.toByteArray()) } }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            return code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } finally { c.disconnect() }
    }
}
