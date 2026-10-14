package com.r17e.guide.safety

import com.r17e.guide.a11y.Snapshot
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Turns safety flags into caregiver alerts (POST /v1/safety-events). Sends METADATA ONLY: a fixed kind, severity
 * and template message plus the app package and screen class. Never screen text, numbers or OTPs.
 * Events are de-duplicated per (flag, app) for [dedupMs], persisted in [queueFile] and retried until sent,
 * so they survive offline periods and restarts. Permanent rejections (4xx other than 401/408/429) are dropped.
 */
class SafetyReporter(
    private val queueFile: File,
    private val send: (JSONObject) -> Unit,
    private val sessionId: () -> String? = { null },
    private val now: () -> Long = System::currentTimeMillis,
    private val dedupMs: Long = 10 * 60_000,
    private val maxQueue: Int = 200,
) {
    data class Mapping(val kind: String, val severity: String, val message: String)

    private val lastSent = HashMap<String, Long>()

    @Synchronized
    fun onSnapshot(s: Snapshot): Int {
        var added = 0
        for (flag in s.flags) {
            val m = map(flag) ?: continue
            val key = "${m.kind}|${s.pkg}"
            if (lastSent[key]?.let { now() - it < dedupMs } == true) continue
            lastSent[key] = now()
            enqueue(JSONObject().apply {
                put("kind", m.kind); put("severity", m.severity); put("message", m.message)
                put("appPackage", s.pkg.take(255))
                sessionId()?.let { put("sessionId", it) }
                put("details", JSONObject().put("flag", flag.substringBefore(':')).put("screen", s.screen.take(80))
                    .put("detectedAt", java.time.Instant.ofEpochMilli(now()).toString()).put("source", "android"))
            })
            added++
        }
        if (added > 0) flush()
        return added
    }

    @Synchronized
    fun pending(): Int = load().length()

    /** Tries to send everything queued; stops at the first transient failure. Returns how many were sent. */
    @Synchronized
    fun flush(): Int {
        val q = load(); var sent = 0
        val keep = JSONArray()
        var i = 0
        while (i < q.length()) {
            val e = q.getJSONObject(i)
            try { send(e); sent++ } catch (ex: Exception) {
                val code = (ex as? com.r17e.guide.backend.BackendException)?.code
                val permanent = code != null && code in 400..499 && code !in setOf(401, 408, 429)
                if (!permanent) { for (j in i until q.length()) keep.put(q.get(j)); break }
            }
            i++
        }
        save(keep)
        return sent
    }

    private fun enqueue(e: JSONObject) {
        val q = load(); q.put(e)
        while (q.length() > maxQueue) q.remove(0)
        save(q)
    }

    private fun load(): JSONArray = runCatching { JSONArray(queueFile.readText()) }.getOrDefault(JSONArray())
    private fun save(a: JSONArray) { queueFile.parentFile?.mkdirs(); queueFile.writeText(a.toString()) }

    companion object {
        fun map(flag: String): Mapping? = when (flag) {
            SafetyPolicy.SCAM_RISK -> Mapping("other", "high", "Scam-like message seen on screen (e.g. KYC expiry, lottery, account block). The helper warned them.")
            SafetyPolicy.PIN_SCREEN -> Mapping("otp_request", "medium", "A PIN or OTP entry screen was opened.")
            SafetyPolicy.PAYMENT_SCREEN -> Mapping("suspicious_payment", "low", "A payment screen was opened in a payments app.")
            SafetyPolicy.REMOTE_ACCESS -> Mapping("remote_access_app", "high", "A remote-control app (like AnyDesk/TeamViewer) was opened.")
            else -> null
        }
    }
}
