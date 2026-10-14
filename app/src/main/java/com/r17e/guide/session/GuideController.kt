package com.r17e.guide.session

import com.r17e.guide.BuildConfig

import android.os.SystemClock
import android.util.Log
import com.r17e.guide.backend.BackendException
import com.r17e.guide.backend.LiveTokenProvider
import com.r17e.guide.live.AudioIO
import com.r17e.guide.live.LiveEvent
import com.r17e.guide.live.LiveProtocol
import com.r17e.guide.live.LiveSession
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Wires backend credentials, the Live websocket, audio and tool handling for one helper session.
 * Survives Live connection limits: keeps the latest resumption handle and reconnects with it on goAway or a
 * dropped socket (fresh credentials each time), with backoff. Only after repeated failures does it go OFFLINE.
 */
class GuideController(
    private val tokens: LiveTokenProvider,
    private val tools: ToolHandler,
    private val systemPrompt: String,
    private val onState: (State) -> Unit,
    private val beforeConnect: () -> Unit = {},
) : LiveSession.Listener {
    enum class State { CONNECTING, LISTENING, OFFLINE, STOPPED }

    private val io = Executors.newSingleThreadScheduledExecutor { Thread(it, "r17e-ctl") }
    @Volatile private var session: LiveSession? = null
    private var sessionId: String? = null
    @Volatile private var resumeHandle: String? = null
    private val backoff = Backoff()
    private val audio = AudioIO { buf, n -> session?.sendAudio(buf, n) }
    @Volatile private var stopped = false
    @Volatile private var lastModelAudioAt = 0L
    @Volatile var lastError: String? = null; private set

    fun start() = io.execute {
        stopped = false; backoff.reset(); resumeHandle = null
        onState(State.CONNECTING); HelperState.set(Phase.CONNECTING, null)
        runCatching(beforeConnect)
        connect()
    }

    private fun connect() {
        if (stopped) return
        try {
            val creds = tokens.get(forceNew = true)
            sessionId = creds.sessionId
            session = LiveSession(creds, systemPrompt, tools.declarations, this, resumeHandle)
        } catch (e: Exception) {
            Log.w(TAG, "connect failed", e)
            lastError = (e as? BackendException)?.error ?: e.message
            retryOrGiveUp()
        }
    }

    private fun retryOrGiveUp() {
        if (stopped) return
        val delay = backoff.next()
        if (delay == null) {
            Log.w(TAG, "giving up after ${backoff.attempt} attempts: $lastError")
            onState(State.OFFLINE); HelperState.set(Phase.OFFLINE, null, lastError)
            return
        }
        Log.i(TAG, "reconnect in ${delay}ms (attempt ${backoff.attempt}, resumable=${resumeHandle != null})")
        HelperState.set(Phase.RECONNECTING)
        io.schedule({ connect() }, delay, TimeUnit.MILLISECONDS)
    }

    /** Debug: stream a raw PCM16 16 kHz mono file (WAV header skipped) as if it came from the mic, at real-time pace. */
    fun injectAudio(file: java.io.File) = Thread {
        val bytes = file.readBytes().let { if (it.size > 44 && String(it, 0, 4) == "RIFF") it.copyOfRange(44, it.size) else it }
        Log.i(TAG, "injecting ${bytes.size} bytes from ${file.name}")
        audio.micMuted = true
        try {
            val silence = ByteArray(3200)
            var i = 0
            while (i < bytes.size) { val n = minOf(3200, bytes.size - i); session?.sendAudio(bytes.copyOfRange(i, i + n), n); i += n; Thread.sleep(100) }
            repeat(15) { session?.sendAudio(silence, silence.size); Thread.sleep(100) } // trailing silence ends the turn
        } finally { audio.micMuted = false }
    }.start()

    fun sendText(text: String) { Log.i(TAG, "sendText ready=${session?.ready}"); session?.sendText(text) }

    /** Debug: simulate the server's connection limit to exercise resumption. */
    fun debugDropConnection() = io.execute { Log.i(TAG, "debug: dropping connection"); onEvent(LiveEvent.GoAway("0s")) }

    override fun onEvent(e: LiveEvent) {
        when (e) {
            LiveEvent.SetupComplete -> {
                Log.i(TAG, "setupComplete (resumed=${resumeHandle != null})")
                backoff.reset(); audio.start()
                onState(State.LISTENING); HelperState.set(Phase.LISTENING, error = null)
            }
            is LiveEvent.Audio -> {
                audio.play(e.pcm24k)
                lastModelAudioAt = SystemClock.elapsedRealtime()
                if (HelperState.status.value.phase != Phase.SPEAKING) HelperState.set(Phase.SPEAKING)
            }
            LiveEvent.Interrupted -> { audio.flushPlayback(); HelperState.set(Phase.LISTENING) }
            LiveEvent.TurnComplete -> io.schedule({
                // Let buffered audio drain before showing "listening" again.
                if (SystemClock.elapsedRealtime() - lastModelAudioAt >= 700 && HelperState.status.value.phase == Phase.SPEAKING)
                    HelperState.set(Phase.LISTENING)
            }, 800, TimeUnit.MILLISECONDS)
            is LiveEvent.ToolCalls -> {
                if (BuildConfig.DEBUG) e.calls.forEach { Log.i(TAG, "tool ${it.name} ${it.args}") }
                val replies = e.calls.map { c ->
                    c to runCatching { tools.handle(c) }.getOrElse { org.json.JSONObject().put("error", it.message ?: "failed") }
                }
                replies.forEach { (c, r) -> if (c.name == "highlight_node" && r.optBoolean("ok")) HelperState.set(HelperState.status.value.phase, c.args.optString("caption")) }
                if (com.r17e.guide.BuildConfig.DEBUG) replies.forEach { (c, r) ->
                    if (BuildConfig.DEBUG) { val j = r.toString(); Log.i(TAG, "tool reply ${c.name}: " + if (j.length > 600) j.take(300) + " ... " + j.takeLast(300) else j) }
                }
                session?.send(LiveProtocol.toolResponse(replies))
            }
            is LiveEvent.ToolCancel -> tools.cancel(e.ids)
            is LiveEvent.Transcript -> if (BuildConfig.DEBUG) Log.i(TAG, (if (e.fromModel) "model: " else "user: ") + e.text)
            is LiveEvent.Resumption -> if (e.resumable && e.handle != null) resumeHandle = e.handle
            is LiveEvent.GoAway -> io.execute {
                Log.i(TAG, "goAway timeLeft=${e.timeLeft}; resuming=${resumeHandle != null}")
                dropSession(); HelperState.set(Phase.RECONNECTING); connect()
            }
        }
    }

    override fun onClosed(reason: String, error: Throwable?) {
        io.execute {
            if (stopped) return@execute
            Log.w(TAG, "socket closed: $reason ${error?.message ?: ""}")
            lastError = error?.message ?: reason
            dropSession(); retryOrGiveUp()
        }
    }

    private fun dropSession() {
        session?.close(); session = null; audio.stop()
        sessionId?.let { tokens.end(it) }; sessionId = null
    }

    fun stop() {
        stopped = true
        io.execute {
            dropSession(); resumeHandle = null
            onState(State.STOPPED); HelperState.set(Phase.STOPPED, null)
        }
    }

    private companion object { const val TAG = "R17E_CTL" }
}
