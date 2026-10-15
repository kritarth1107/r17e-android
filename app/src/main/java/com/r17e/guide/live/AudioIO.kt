package com.r17e.guide.live

import android.annotation.SuppressLint
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import kotlin.concurrent.thread

/** Mic capture at 16 kHz PCM16 mono and speaker playback at 24 kHz. Caller must hold RECORD_AUDIO. */
class AudioIO(private val onMic: (ByteArray, Int) -> Unit) {
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    @Volatile private var running = false
    @Volatile var micMuted = false

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true
        val inMin = AudioRecord.getMinBufferSize(LiveProtocol.INPUT_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, LiveProtocol.INPUT_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(inMin, 3200) * 2)
        if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId)?.enabled = true
        if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId)?.enabled = true
        record = rec
        val outMin = AudioTrack.getMinBufferSize(LiveProtocol.OUTPUT_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(LiveProtocol.OUTPUT_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(outMin * 4).setTransferMode(AudioTrack.MODE_STREAM).build().also { it.play(); startPlayer(it) }
        rec.startRecording()
        thread(name = "r17e-mic", isDaemon = true) {
            val buf = ByteArray(3200) // 100 ms
            var chunks = 0; var peak = 0
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (com.r17e.guide.BuildConfig.DEBUG && n > 0) {
                    for (i in 0 until n - 1 step 2) peak = maxOf(peak, kotlin.math.abs((buf[i].toInt() and 0xff) or (buf[i + 1].toInt() shl 8)))
                    if (++chunks % 20 == 0) { android.util.Log.d("R17E_MIC", "2s peak=$peak"); peak = 0 }
                }
                if (n > 0 && !micMuted) onMic(buf, n)
            }
        }
    }

    /**
     * Queue model audio. Never blocks: the websocket reader must stay free so that "interrupted" and tool calls
     * arrive immediately (a blocking AudioTrack.write here used to delay barge-in).
     */
    fun play(pcm: ByteArray) { queuedBytes.addAndGet(pcm.size.toLong()); playQueue.offer(pcm) }

    private val playQueue = java.util.concurrent.LinkedBlockingQueue<ByteArray>()
    private val queuedBytes = java.util.concurrent.atomic.AtomicLong()
    @Volatile private var ducked = false

    /** Lower the helper's voice while the user may be talking over it; [duck] false restores it. */
    fun duck(duck: Boolean) { if (ducked != duck) { ducked = duck; runCatching { track?.setVolume(if (duck) 0.25f else 1f) } } }

    private fun startPlayer(t: AudioTrack) = thread(name = "r17e-play", isDaemon = true) {
        while (running) {
            val pcm = playQueue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS) ?: continue
            queuedBytes.addAndGet(-pcm.size.toLong())
            if (track !== t) break
            t.write(pcm, 0, pcm.size); writtenFrames += pcm.size / 2
        }
    }

    /** Barge-in: drop whatever the model was still saying. */
    /** Returns roughly how much queued audio (ms) was dropped. */
    fun isPlaying(): Boolean = queuedBytes.get() > 0 || (track?.let { (writtenFrames - (it.playbackHeadPosition.toLong() and 0xffffffffL)) > 0 } ?: false)

    fun flushPlayback(): Long {
        val t = track ?: return 0
        val queued = queuedBytes.getAndSet(0) / 2; playQueue.clear()
        val written = writtenFrames; val played = t.playbackHeadPosition.toLong() and 0xffffffffL
        t.pause(); t.flush(); t.play(); writtenFrames = 0; duck(false)
        return (((written - played).coerceAtLeast(0) + queued) * 1000) / LiveProtocol.OUTPUT_RATE
    }
    @Volatile private var writtenFrames = 0L

    fun stop() {
        running = false
        playQueue.clear(); queuedBytes.set(0); ducked = false
        record?.run { runCatching { stop() }; release() }; record = null
        track?.run { runCatching { stop() }; release() }; track = null
    }
}
