package com.r17e.guide.safety

import com.r17e.guide.a11y.SnapNode
import com.r17e.guide.a11y.Snapshot
import com.r17e.guide.backend.BackendException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class SafetyReporterTest {
    private fun n(t: String) = SnapNode("x", "View", t, null, null, 0, 0, 1, 1, false, false, false)
    private val scamPin = SafetyPolicy.filter(Snapshot("com.android.chrome", "ChromeTabbedActivity", 1, 1,
        listOf(n("Your KYC expired. Share OTP 482913"), n("Enter UPI PIN"))))

    @Test fun sendsMetadataOnlyAndDedups() {
        val sent = mutableListOf<JSONObject>(); var t = 0L
        val r = SafetyReporter(Files.createTempFile("q", ".json").toFile(), { sent += it }, now = { t })
        assertEquals(2, r.onSnapshot(scamPin))
        assertEquals(setOf("other", "otp_request"), sent.map { it.getString("kind") }.toSet())
        val all = sent.joinToString { it.toString() }
        assertFalse(all.contains("482913")); assertFalse(all.contains("KYC expired. Share")); assertFalse(all.contains("Enter UPI PIN"))
        assertEquals("com.android.chrome", sent[0].getString("appPackage"))
        assertEquals(0, r.onSnapshot(scamPin)) // dedup
        t += 11 * 60_000; assertEquals(2, r.onSnapshot(scamPin))
    }

    @Test fun queuesOfflineAndFlushesLater() {
        val f = Files.createTempFile("q", ".json").toFile()
        var online = false; val sent = mutableListOf<JSONObject>()
        val send: (JSONObject) -> Unit = { if (!online) throw java.io.IOException("offline") else sent += it }
        SafetyReporter(f, send).onSnapshot(scamPin)
        val r2 = SafetyReporter(f, send) // survives restart
        assertEquals(2, r2.pending())
        online = true; assertEquals(2, r2.flush()); assertEquals(0, r2.pending())
    }

    @Test fun dropsPermanentRejectionsKeepsTransient() {
        val f = Files.createTempFile("q", ".json").toFile()
        var code = 400
        val r = SafetyReporter(f, { throw BackendException(code, "x") })
        r.onSnapshot(scamPin); assertEquals(0, r.pending())
        code = 429
        SafetyReporter(f, { throw BackendException(code, "x") }, now = { 99_999_999 }).onSnapshot(scamPin)
        assertEquals(2, SafetyReporter(f, {}).pending())
    }

    @Test fun mapsEveryFlagToBackendKind() {
        val kinds = setOf("scam_call", "suspicious_payment", "otp_request", "unknown_payee", "phishing_link", "remote_access_app", "other")
        listOf(SafetyPolicy.SCAM_RISK, SafetyPolicy.PIN_SCREEN, SafetyPolicy.PAYMENT_SCREEN, SafetyPolicy.REMOTE_ACCESS)
            .forEach { assertTrue(SafetyReporter.map(it)!!.kind in kinds) }
    }
}
