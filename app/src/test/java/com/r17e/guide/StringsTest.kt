package com.r17e.guide

import com.r17e.guide.i18n.Lang
import com.r17e.guide.i18n.S
import org.junit.Assert.*
import org.junit.Test

class StringsTest {
    @Test fun everyKeyTranslatedAndDistinctScript() {
        for (k in S.keys) for (l in Lang.values()) assertTrue("$k/$l", S.get(l, k).isNotBlank())
        assertTrue(S.get(Lang.HI, "start").any { it in '\u0900'..'\u097F' })
        assertFalse(S.get(Lang.HINGLISH, "start").any { it in '\u0900'..'\u097F' })
        assertEquals("missing", S.get(Lang.EN, "missing"))
    }
}
