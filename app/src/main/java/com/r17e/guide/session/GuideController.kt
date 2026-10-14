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
    private val beforeConnect: () -> Unit = {},
) : LiveSession.Listener {
    enum class State { CONNECTING, LISTENING, OFFLINE, STOPPED }

    private val io = Executors.newSingleThreadExecutor { Thread(it, "r17e-ctl") }
    private var session: LiveSession? = null
    private var sessionId: String? = null
    private val audio = AudioIO { buf, n -> session?.sendAudio(buf, n) }
    @Volatile private var stopped = false
    @Volatile var lastError: String? = null; private set

    fun start() = io.execute {
        stopped = false
        onState(State.CONNECTING)
        runCatching(beforeConnect)
        try {
            val creds = tokens.get(forceNew = true)
            sessionId = creds.sessionId
            session = LiveSession(creds, systemPrompt, tools.declarations, this)
        } catch (e: Exception) {
            Log.w(TAG, "could not start live session", e)
            lastError = e.message; onState(State.OFFLINE)
        }
    }

    /** Debug: stream a raw PCM16 16 kHz mono file (WAV header skipped) as if it came from the mic, at real-time pace. */
    fun injectAudio(file: java.io.File) = Thread {
        val bytes = file.readBytes().let { if (it.size > 44 && String(it, 0, 4) == "RIFF") it.copyOfRange(44, it.size) else it }
        Log.i(TAG, "injecting ${bytes.size} bytes from ${file.name}")
        audio.micMuted = true
        try {
            val silence = ByteArray(3200)
            bytes.toList().chunked(3200).forEach { c -> session?.sendAudio(c.toByteArray(), c.size); Thread.sleep(100) }
            repeat(15) { session?.sendAudio(silence, silence.size); Thread.sleep(100) } // trailing silence ends the turn
        } finally { audio.micMuted = false }
    }.start()

    fun sendText(text: String) { Log.i(TAG, "sendText ready=${session?.ready}"); session?.sendText(text) }

    override fun onEvent(e: LiveEvent) {
        when (e) {
            LiveEvent.SetupComplete -> {
                Log.i(TAG, "setupComplete"); audio.start(); onState(State.LISTENING) }
            is LiveEvent.Audio -> audio.play(e.pcm24k)
            LiveEvent.Interrupted -> audio.flushPlayback()
            is LiveEvent.ToolCalls -> {
                e.calls.forEach { Log.i(TAG, "tool ${it.name} ${it.args}") }
                val replies = e.calls.map { c ->
                    c to runCatching { tools.handle(c) }.getOrElse { org.json.JSONObject().put("error", it.message ?: "failed") }
                }
                if (com.r17e.guide.BuildConfig.DEBUG) replies.forEach { (c, r) ->
                    val j = r.toString(); Log.i(TAG, "tool reply ${c.name}: " + if (j.length > 600) j.take(300) + " ... " + j.takeLast(300) else j)
                }
                session?.send(LiveProtocol.toolResponse(replies))
            }
            is LiveEvent.ToolCancel -> tools.cancel(e.ids)
            is LiveEvent.Transcript -> Log.i(TAG, (if (e.fromModel) "model: " else "user: ") + e.text)
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
        sessionId?.let { tokens.end(it) }; sessionId = null
        start()
    }

    fun stop() {
        stopped = true
        io.execute {
            session?.close(); session = null; audio.stop()
            sessionId?.let { tokens.end(it) }; sessionId = null
            onState(State.STOPPED)
        }
    }

    private companion object { const val TAG = "R17E_CTL" }
}
