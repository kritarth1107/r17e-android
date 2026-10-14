package com.r17e.guide

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** CI guard: the app must never act on the user's behalf. */
class GuideOnlyTest {
    @Test fun noActionOrGestureApisInSources() {
        val forbidden = Regex("""\.(performAction|dispatchGesture|performGlobalAction)\s*\(""")
        val hits = File("src/main").walk().filter { it.extension == "kt" || it.extension == "java" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (forbidden.containsMatchIn(l)) "${f.path}:${i + 1}" else null } }
            .toList()
        assertTrue("Guide-only violation: $hits", hits.isEmpty())
    }
}
