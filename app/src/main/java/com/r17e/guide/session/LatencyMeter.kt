package com.r17e.guide.session

/**
 * Voice latency bookkeeping (no content, timings only).
 * - connect: start of connect() -> setupComplete
 * - reply: end of the user's speech -> first model audio chunk
 */
class LatencyMeter(private val now: () -> Long) {
    private var connectStart: Long? = null
    private var speechEnd = 0L
    /** Last time any user speech was heard (for the idle auto-stop). */
    @Volatile var lastSpeechAt = 0L; private set
    private var awaiting = false
    val replies = mutableListOf<Long>()
    var lastConnectMs: Long? = null; private set

    fun connectStarted() { connectStart = now() }
    fun connected(): Long? = connectStart?.let { st -> (now() - st).also { lastConnectMs = it; connectStart = null } }

    private var bargeStart: Long? = null

    /** Call for every mic/injected chunk that contains speech. [modelSpeaking]: the user is talking over the helper. */
    fun userSpoke(modelSpeaking: Boolean = false) {
        lastSpeechAt = now()
        if (modelSpeaking) { if (bargeStart == null) bargeStart = now(); return }
        speechEnd = now(); awaiting = true
    }

    /** Model stopped because the user talked over it: ms from the user's first speech chunk to playback stop. */
    fun interrupted(): Long? = bargeStart?.let { st -> (now() - st).also { bargeStart = null; speechEnd = now(); awaiting = true } }

    /** Model turn ended normally; forget a barge-in that never happened. */
    fun turnDone() { bargeStart = null }

    /** Call on each model audio chunk; returns the reply latency for the first chunk after the user spoke. */
    fun modelAudio(): Long? {
        if (!awaiting) return null
        awaiting = false
        return (now() - speechEnd).also { replies += it; if (replies.size > 50) replies.removeAt(0) }
    }

    fun median(): Long? = replies.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }

    companion object {
        /** Peak amplitude of PCM16 little-endian samples. */
        fun peak(buf: ByteArray, n: Int): Int {
            var p = 0
            var i = 0
            while (i + 1 < n) { val v = (buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8); p = maxOf(p, kotlin.math.abs(v.toShort().toInt())); i += 2 }
            return p
        }
        const val SPEECH_PEAK = 1500
    }
}
