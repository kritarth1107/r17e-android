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
    private val onIdle: () -> Unit = {},
    private val idleStopMs: Long = 10 * 60_000L,
) : LiveSession.Listener {
    enum class State { CONNECTING, LISTENING, OFFLINE, STOPPED }

    private val io = Executors.newSingleThreadScheduledExecutor { Thread(it, "r17e-ctl") }
    @Volatile private var session: LiveSession? = null
    private var sessionId: String? = null
    @Volatile private var resumeHandle: String? = null
    private val backoff = Backoff()
    val latency = LatencyMeter { SystemClock.elapsedRealtime() }
    private val audio = AudioIO { buf, n ->
        if (LatencyMeter.peak(buf, n) >= LatencyMeter.SPEECH_PEAK) onLocalSpeech()
        session?.sendAudio(buf, n)
    }
    @Volatile private var stopped = false
    @Volatile private var lastModelAudioAt = 0L
    @Volatile var lastError: String? = null; private set

    fun start() = io.execute {
        stopped = false; backoff.reset(); resumeHandle = null
        lastActivityAt = SystemClock.elapsedRealtime(); scheduleIdleCheck()
        onState(State.CONNECTING); HelperState.set(Phase.CONNECTING, null)
        runCatching(beforeConnect)
        connect()
    }

    private fun connect() {
        if (stopped) return
        try {
            latency.connectStarted()
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
        val delay = if (probing) null else backoff.next()
        probing = false
        if (delay == null) {
            Log.w(TAG, "giving up after ${backoff.attempt} attempts: $lastError")
            onState(State.OFFLINE); HelperState.set(Phase.OFFLINE, null, lastError)
            offline = true
            io.schedule({ retryFromOffline("timer") }, OFFLINE_RETRY_MS, TimeUnit.MILLISECONDS)
            return
        }
        Log.i(TAG, "reconnect in ${delay}ms (attempt ${backoff.attempt}, resumable=${resumeHandle != null})")
        HelperState.set(Phase.RECONNECTING)
        io.schedule({ connect() }, delay, TimeUnit.MILLISECONDS)
    }

    @Volatile private var offline = false
    @Volatile private var probing = false

    /** While offline, try the online helper again (every [OFFLINE_RETRY_MS], or at once when the network comes back). */
    fun retryFromOffline(why: String) = io.execute {
        if (stopped || !offline || session != null) return@execute
        Log.i(TAG, "retrying online helper ($why)")
        offline = false; probing = true // one quiet attempt; on failure go straight back to offline help
        connect()
    }

    /** Called by the service when Android reports a working network. */
    fun networkAvailable() { if (offline) retryFromOffline("network") }

    private fun scheduleIdleCheck() {
        io.schedule({
            if (stopped) return@schedule
            val quietFor = SystemClock.elapsedRealtime() - maxOf(lastActivityAt, latency.lastSpeechAt)
            if (quietFor >= idleStopMs && HelperState.status.value.phase != Phase.SPEAKING) { Log.i(TAG, "idle ${quietFor / 1000}s: stopping"); onIdle() }
            else scheduleIdleCheck()
        }, 30, TimeUnit.SECONDS)
    }
    @Volatile private var lastActivityAt = SystemClock.elapsedRealtime()

    /** Debug: stream a raw PCM16 16 kHz mono file (WAV header skipped) as if it came from the mic, at real-time pace. */
    fun injectAudio(file: java.io.File) = Thread {
        val bytes = file.readBytes().let { if (it.size > 44 && String(it, 0, 4) == "RIFF") it.copyOfRange(44, it.size) else it }
        Log.i(TAG, "injecting ${bytes.size} bytes from ${file.name}")
        audio.micMuted = true
        try {
            val silence = ByteArray(3200)
            var i = 0
            while (i < bytes.size) {
                val n = minOf(3200, bytes.size - i); val chunk = bytes.copyOfRange(i, i + n)
                if (LatencyMeter.peak(chunk, n) >= LatencyMeter.SPEECH_PEAK) onLocalSpeech()
                session?.sendAudio(chunk, n); i += n; Thread.sleep(100)
            }
            repeat(15) { session?.sendAudio(silence, silence.size); Thread.sleep(100) } // trailing silence ends the turn
        } finally { audio.micMuted = false }
    }.start()

    @Volatile private var duckedAt = 0L

    /** User voice detected locally. While the helper is talking, duck it at once (the server confirms the barge-in). */
    private fun onLocalSpeech() {
        val speaking = HelperState.status.value.phase == Phase.SPEAKING
        latency.userSpoke(speaking)
        if (speaking && duckedAt == 0L) {
            duckedAt = SystemClock.elapsedRealtime(); audio.duck(true)
            io.schedule({ if (duckedAt != 0L && SystemClock.elapsedRealtime() - duckedAt >= 1500) { duckedAt = 0L; audio.duck(false) } }, 1600, TimeUnit.MILLISECONDS)
        }
    }

    /** True once the live session is set up and can take a turn. */
    val isLive: Boolean get() = session?.ready == true

    fun sendText(text: String) { Log.i(TAG, "sendText ready=${session?.ready}"); session?.sendText(text) }

    /** Debug: simulate the server's connection limit to exercise resumption. */
    fun debugDropConnection() = io.execute { Log.i(TAG, "debug: dropping connection"); onEvent(LiveEvent.GoAway("0s")) }

    override fun onEvent(e: LiveEvent) {
        when (e) {
            LiveEvent.SetupComplete -> {
                Log.i(TAG, "setupComplete (resumed=${resumeHandle != null})")
                latency.connected()?.let { Log.i(LAT, "connect_ms=$it resumed=${resumeHandle != null}") }
                backoff.reset(); offline = false; probing = false; audio.start()
                onState(State.LISTENING); HelperState.set(Phase.LISTENING, error = null)
            }
            is LiveEvent.Audio -> {
                lastActivityAt = SystemClock.elapsedRealtime()
                latency.modelAudio()?.let { Log.i(LAT, "reply_ms=$it median=${latency.median()} n=${latency.replies.size}") }
                audio.play(e.pcm24k)
                lastModelAudioAt = SystemClock.elapsedRealtime()
                if (HelperState.status.value.phase != Phase.SPEAKING) HelperState.set(Phase.SPEAKING)
            }
            LiveEvent.Interrupted -> {
                val t0 = SystemClock.elapsedRealtime(); val dropped = audio.flushPlayback(); duckedAt = 0L
                latency.interrupted()?.let { Log.i(LAT, "bargein_ms=$it") }
                Log.i(LAT, "bargein_flush_ms=${SystemClock.elapsedRealtime() - t0} dropped_audio_ms=$dropped")
                HelperState.set(Phase.LISTENING)
            }
            LiveEvent.TurnComplete -> io.schedule({
                latency.turnDone()
                // Let buffered audio drain before showing "listening" again.
                waitDrainThenListen()
            }, 300, TimeUnit.MILLISECONDS)
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

    /** Show "listening" only once queued speech has actually played out. */
    private fun waitDrainThenListen() {
        if (HelperState.status.value.phase != Phase.SPEAKING) return
        if (audio.isPlaying() || SystemClock.elapsedRealtime() - lastModelAudioAt < 300) io.schedule({ waitDrainThenListen() }, 200, TimeUnit.MILLISECONDS)
        else HelperState.set(Phase.LISTENING)
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

    private companion object { const val TAG = "R17E_CTL"; const val LAT = "R17E_LAT"; const val OFFLINE_RETRY_MS = 30_000L }
}
