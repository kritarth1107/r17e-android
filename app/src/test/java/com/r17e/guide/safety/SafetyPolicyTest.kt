package com.r17e.guide.safety

import com.r17e.guide.a11y.SnapNode
import com.r17e.guide.a11y.Snapshot
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SafetyPolicyTest {
    private fun n(id: String, txt: String?, vid: String? = null, c: Boolean = true, e: Boolean = false, pw: Boolean = false) =
        SnapNode(id, "View", txt, null, vid, 0, 0, 1, 1, c, e, pw)

    @Test fun redactsOtpFieldsAndLongDigits() {
        val s = SafetyPolicy.filter(Snapshot("com.x", "S", 1, 1, listOf(
            n("a", "482913", vid = "otp_input", e = true),
            n("b", "Your OTP is 482913 do not share", c = false),
            n("c", "Call 98"),
        )))
        assertNull(s.node("a")!!.text); assertTrue(s.node("a")!!.password)
        assertEquals("Your OTP is ••••13 do not share", s.node("b")!!.text)
        assertEquals("Call 98", s.node("c")!!.text)
        assertFalse(s.toJson().contains("482913"))
    }

    @Test fun pinScreenBlocksKeypadAndSecretFields() {
        val s = SafetyPolicy.filter(Snapshot("com.phonepe.app", "Pin", 1, 1, listOf(n("t", "Enter UPI PIN", c = false), n("k", "5"))))
        assertTrue(SafetyPolicy.PIN_SCREEN in s.flags)
        assertTrue(SafetyPolicy.vet(s, s.node("k")!!) is SafetyPolicy.Verdict.Deny)
        assertTrue(SafetyPolicy.vet(s, n("p", null, pw = true)) is SafetyPolicy.Verdict.Deny)
    }

    @Test fun payButtonNeedsConfirmation() {
        val s = SafetyPolicy.filter(Snapshot("com.phonepe.app", "Pay", 1, 1, listOf(n("p", "Pay"))))
        assertTrue(SafetyPolicy.vet(s, s.node("p")!!) is SafetyPolicy.Verdict.AllowWithNote)
        assertTrue(JSONObject(s.toJson()).has("safety"))
    }

    @Test fun flagsScamAndRemoteAccess() {
        assertTrue(SafetyPolicy.SCAM_RISK in SafetyPolicy.filter(Snapshot("com.whatsapp", "C", 1, 1, listOf(n("m", "Your KYC expired, click link", c = false)))).flags)
        assertTrue(SafetyPolicy.REMOTE_ACCESS in SafetyPolicy.filter(Snapshot("com.anydesk.anydeskandroid", "A", 1, 1, emptyList())).flags)
    }

    @Test fun ordinaryButtonsAllowed() {
        val s = SafetyPolicy.filter(Snapshot("com.whatsapp", "Home", 1, 1, listOf(n("c", "Calls"))))
        assertEquals(SafetyPolicy.Verdict.Allow, SafetyPolicy.vet(s, s.node("c")!!))
    }
}
