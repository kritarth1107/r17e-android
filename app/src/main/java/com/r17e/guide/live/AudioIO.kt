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
            .setBufferSizeInBytes(outMin * 4).setTransferMode(AudioTrack.MODE_STREAM).build().also { it.play() }
        rec.startRecording()
        thread(name = "r17e-mic", isDaemon = true) {
            val buf = ByteArray(3200) // 100 ms
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0 && !micMuted) onMic(buf, n)
            }
        }
    }

    fun play(pcm: ByteArray) { track?.write(pcm, 0, pcm.size) }

    /** Barge-in: drop whatever the model was still saying. */
    fun flushPlayback() { track?.run { pause(); flush(); play() } }

    fun stop() {
        running = false
        record?.run { runCatching { stop() }; release() }; record = null
        track?.run { runCatching { stop() }; release() }; track = null
    }
}
