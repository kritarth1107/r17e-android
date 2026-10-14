package com.r17e.guide.session

import android.util.Log
import com.r17e.guide.backend.LiveTokenProvider
import com.r17e.guide.live.AudioIO
import com.r17e.guide.live.LiveEvent
import com.r17e.guide.live.LiveProtocol
import com.r17e.guide.live.LiveSession
import java.util.concurrent.Executors

/** Wires backend credentials, the Live websocket, audio and tool handling for one helper session. */
class GuideController(
    private val tokens: LiveTokenProvider,
    private val tools: ToolHandler,
    private val systemPrompt: String,
    private val onState: (State) -> Unit,
) : LiveSession.Listener {
    enum class State { CONNECTING, LISTENING, OFFLINE, STOPPED }

    private val io = Executors.newSingleThreadExecutor { Thread(it, "r17e-ctl") }
    private var session: LiveSession? = null
    private val audio = AudioIO { buf, n -> session?.sendAudio(buf, n) }
    @Volatile private var stopped = false
    @Volatile var lastError: String? = null; private set

    fun start() = io.execute {
        stopped = false
        onState(State.CONNECTING)
        try {
            val creds = tokens.get()
            session = LiveSession(creds, systemPrompt, tools.declarations, this)
        } catch (e: Exception) {
            Log.w(TAG, "could not start live session", e)
            lastError = e.message; onState(State.OFFLINE)
        }
    }

    fun sendText(text: String) { session?.sendText(text) }

    override fun onEvent(e: LiveEvent) {
        when (e) {
            LiveEvent.SetupComplete -> { audio.start(); onState(State.LISTENING) }
            is LiveEvent.Audio -> audio.play(e.pcm24k)
            LiveEvent.Interrupted -> audio.flushPlayback()
            is LiveEvent.ToolCalls -> {
                val replies = e.calls.map { c ->
                    c to runCatching { tools.handle(c) }.getOrElse { org.json.JSONObject().put("error", it.message ?: "failed") }
                }
                session?.send(LiveProtocol.toolResponse(replies))
            }
            is LiveEvent.ToolCancel -> tools.cancel(e.ids)
            is LiveEvent.Transcript -> Log.d(TAG, (if (e.fromModel) "model: " else "user: ") + e.text)
            is LiveEvent.GoAway -> io.execute { reconnect() }
            LiveEvent.TurnComplete -> {}
        }
    }

    override fun onClosed(reason: String, error: Throwable?) {
        audio.stop()
        if (stopped) return
        lastError = error?.message ?: reason
        onState(State.OFFLINE)
    }

    private fun reconnect() {
        session?.close(); session = null; audio.stop()
        start()
    }

    fun stop() {
        stopped = true
        io.execute { session?.close(); session = null; audio.stop(); onState(State.STOPPED) }
    }

    private companion object { const val TAG = "R17E_CTL" }
}
