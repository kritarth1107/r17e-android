package com.r17e.guide.safety

import org.junit.Assert.*
import org.junit.Test

class ScamWarnerTest {
    @Test fun warnsOncePerAppWithinWindow() {
        val w = ScamWarner(perAppMs = 300_000, globalMs = 60_000)
        assertTrue(w.shouldWarn("chrome", 0))
        assertFalse(w.shouldWarn("chrome", 120_000))
        assertTrue(w.shouldWarn("chrome", 300_000))
    }
    @Test fun globalLimitAcrossApps() {
        val w = ScamWarner(perAppMs = 300_000, globalMs = 60_000)
        assertTrue(w.shouldWarn("a", 0))
        assertFalse(w.shouldWarn("b", 30_000))
        assertTrue(w.shouldWarn("b", 60_000))
    }
}
