package com.r17e.guide.session

import org.junit.Assert.*
import org.junit.Test

class BackoffTest {
    @Test fun growsCapsAndGivesUp() {
        val b = Backoff(maxAttempts = 5, capMs = 8000)
        assertEquals(listOf(1000L, 2000L, 4000L, 8000L, 8000L), List(5) { b.next() })
        assertNull(b.next())
        b.reset(); assertEquals(1000L, b.next())
    }
}
