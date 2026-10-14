package com.r17e.guide.session

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the helper is doing right now; drives the floating bubble and the home screen. */
enum class Phase { STOPPED, CONNECTING, LISTENING, SPEAKING, RECONNECTING, OFFLINE }

data class HelperStatus(val phase: Phase = Phase.STOPPED, val caption: String? = null, val error: String? = null)

object HelperState {
    private val _status = MutableStateFlow(HelperStatus())
    val status: StateFlow<HelperStatus> = _status
    fun set(phase: Phase, caption: String? = _status.value.caption, error: String? = null) {
        _status.value = HelperStatus(phase, caption, error)
    }
}

/** Exponential backoff for reconnects: 1, 2, 4, 8, 8 s ... then give up after [maxAttempts]. */
class Backoff(private val maxAttempts: Int = 5, private val capMs: Long = 8_000) {
    var attempt = 0; private set
    fun next(): Long? = if (attempt >= maxAttempts) null else minOf(capMs, 1000L shl attempt++)
    fun reset() { attempt = 0 }
}
