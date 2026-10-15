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

class StringsQualityTest {
    private fun deva(s: String) = s.any { it in '\u0900'..'\u097F' }

    @Test fun hindiIsDevanagariAndHinglishIsLatinForEveryKey() {
        for (k in S.keys) {
            assertTrue("HI $k should be Devanagari", deva(S.get(Lang.HI, k)) || S.get(Lang.HI, k) == S.get(Lang.EN, k))
            assertFalse("Hinglish $k must not use Devanagari", deva(S.get(Lang.HINGLISH, k)))
        }
    }

    @Test fun hinglishUsesOnePoliteVerbForm() {
        // Consistency: polite "-iye" forms, not a mix with "karein".
        for (k in S.keys) assertFalse("Hinglish $k uses 'karein'", S.get(Lang.HINGLISH, k).contains("karein"))
    }

    @Test fun elderFacingTextStaysShort() {
        for (k in S.keys) for (l in Lang.values()) assertTrue("$k/$l too long", S.get(l, k).length <= 220)
    }

    @Test fun agreeIsGenderNeutralInHindi() {
        assertFalse(S.get(Lang.HI, "agree").contains("मैं समझ गया"))
    }
}
