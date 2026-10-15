package com.r17e.guide.session

import org.junit.Assert.*
import org.junit.Test

class LatencyMeterTest {
    private var t = 0L
    private val m = LatencyMeter { t }

    @Test fun replyMeasuredFromLastSpeechChunkToFirstAudio() {
        t = 100; m.userSpoke(); t = 900; m.userSpoke(); t = 2500
        assertEquals(1600L, m.modelAudio())
        t = 2600; assertNull(m.modelAudio()) // only the first chunk counts
    }

    @Test fun bargeInMeasuredFromFirstOverlapChunk() {
        t = 0; m.userSpoke(modelSpeaking = true); t = 200; m.userSpoke(modelSpeaking = true); t = 550
        assertEquals(550L, m.interrupted())
        assertNull(m.interrupted())
    }

    @Test fun turnDoneForgetsUnconfirmedBargeIn() {
        t = 0; m.userSpoke(modelSpeaking = true); m.turnDone(); t = 900
        assertNull(m.interrupted())
    }

    @Test fun connectAndMedian() {
        t = 0; m.connectStarted(); t = 2700; assertEquals(2700L, m.connected())
        listOf(3L, 1L, 2L).forEach { v -> m.userSpoke(); t += v; m.modelAudio() }
        assertEquals(2L, m.median())
    }

    @Test fun peakOfPcm16() {
        val b = byteArrayOf(0x10, 0x00, 0x00, 0x80.toByte(), 0xff.toByte(), 0x7f)
        assertEquals(32768, LatencyMeter.peak(b, b.size))
    }
}
