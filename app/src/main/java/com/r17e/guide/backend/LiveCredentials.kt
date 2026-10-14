package com.r17e.guide.backend

import org.json.JSONObject
import java.time.Instant

/** Short-lived Vertex AI credentials for one Gemini Live session, as returned by the backend. */
data class LiveCredentials(
    val sessionId: String,
    val accessToken: String,
    val expiresAtMs: Long?,
    val modelResource: String,
    val websocketUrl: String,
) {
    fun isFresh(nowMs: Long, marginMs: Long = 60_000): Boolean = expiresAtMs == null || nowMs + marginMs < expiresAtMs

    override fun toString() = "LiveCredentials(sessionId=$sessionId, model=$modelResource, token=<redacted>)"

    companion object {
        fun fromJson(o: JSONObject) = LiveCredentials(
            sessionId = o.getString("sessionId"),
            accessToken = o.getString("accessToken"),
            expiresAtMs = o.optString("expiresAt").takeIf { it.isNotEmpty() && it != "null" }
                ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
            modelResource = o.getString("modelResource"),
            websocketUrl = o.getString("websocketUrl"),
        )
    }
}

/** Gets fresh Live credentials, re-authenticating once if the access token was rejected. */
class LiveTokenProvider(
    private val client: BackendClient,
    private val token: () -> String,
    private val reAuth: () -> Unit,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var cached: LiveCredentials? = null

    /** Best effort: tell the backend the session ended. */
    fun end(sessionId: String) = runCatching { client.endSession(token(), sessionId) }

    @Synchronized
    fun get(forceNew: Boolean = false, goal: String? = null, appPackage: String? = null): LiveCredentials {
        cached?.takeIf { !forceNew && it.isFresh(now()) }?.let { return it }
        val c = try { client.liveSession(token(), goal, appPackage) } catch (e: BackendException) {
            if (e.code != 401) throw e
            reAuth(); client.liveSession(token(), goal, appPackage)
        }
        cached = c
        return c
    }
}
