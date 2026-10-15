package com.r17e.guide.safety

/**
 * Decides when the running helper should speak a scam warning by itself: at most once per app every
 * [perAppMs], and at most once every [globalMs] overall, so it never nags. Guide-only: it only speaks.
 */
class ScamWarner(private val perAppMs: Long = 5 * 60_000L, private val globalMs: Long = 60_000L) {
    private val lastByApp = HashMap<String, Long>()
    private var lastAny = Long.MIN_VALUE / 2

    @Synchronized
    fun shouldWarn(pkg: String, nowMs: Long): Boolean {
        if (nowMs - lastAny < globalMs) return false
        val prev = lastByApp[pkg]
        if (prev != null && nowMs - prev < perAppMs) return false
        lastByApp[pkg] = nowMs; lastAny = nowMs
        return true
    }
}
