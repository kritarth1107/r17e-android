package com.r17e.guide.safety

import com.r17e.guide.a11y.SnapNode
import com.r17e.guide.a11y.Snapshot

/**
 * Safety layer between the screen and the model.
 * - Redacts secrets before anything leaves the phone (password fields, OTP/PIN/CVV fields, long digit runs).
 * - Flags PIN-entry, payment and scam-risk screens so the model changes behaviour.
 * - Vets highlight requests: never points at secret-entry keys, and asks for spoken confirmation before money moves.
 */
object SafetyPolicy {
    const val PIN_SCREEN = "pin_entry_screen: do not guide key by key; tell the user to type their PIN privately"
    const val PAYMENT_SCREEN = "payment_screen: read the amount and receiver aloud and get a clear yes before pointing at Pay"
    const val SCAM_RISK = "scam_risk: warn the user, tell them not to share OTP/PIN or install apps a caller asked for"
    const val REMOTE_ACCESS = "remote_access_app: these apps let strangers control the phone; strongly warn the user"

    private val SECRET_FIELD = Regex("""(?i)(?<![a-z])(otp|one[- _]?time|pin|mpin|upi[ _]?pin|cvv|cvc|password|passcode|card[ _]?(number|no)|aadhaar|पिन|ओटीपी)(?![a-z])""")
    private val DIGITS = Regex("""\d[\d \-]{4,}\d""")
    private val PIN_TEXT = Regex("""(?i)(enter (upi |m)?pin|upi pin|enter otp|enter passcode|पिन डालें|ओटीपी डालें)""")
    private val PAY_TEXT = Regex("""(?i)\b(pay|send money|confirm payment|proceed to pay|transfer|भुगतान|पैसे भेजें)\b""")
    private val SCAM_TEXT = Regex("""(?i)(kyc (is )?(expired|pending|update)|account (will be )?(blocked|suspended)|lottery|you have won|refund (process|form)|electricity (bill|connection).*(disconnect|cut)|digital arrest|customs parcel)""")
    private val PAYMENT_PKGS = setOf(
        "net.one97.paytm", "com.phonepe.app", "com.google.android.apps.nbu.paisa.user", "in.org.npci.upiapp",
        "com.sbi.lotusintouch", "com.csam.icici.bank.imobile", "com.snapwork.hdfc", "in.amazon.mShop.android.shopping",
    )
    private val REMOTE_PKGS = setOf(
        "com.anydesk.anydeskandroid", "com.teamviewer.quicksupport.market", "com.teamviewer.teamviewer.market.mobile",
        "com.rustdesk.rustdesk", "com.airdroid.ad",
    )
    private val KEYPAD_KEY = Regex("""^\s*\d\s*$""")

    fun filter(s: Snapshot): Snapshot {
        val all = s.nodes.joinToString(" ") { listOfNotNull(it.text, it.desc).joinToString(" ") }
        val flags = buildList {
            if (PIN_TEXT.containsMatchIn(all)) add(PIN_SCREEN)
            if (s.pkg in PAYMENT_PKGS && PAY_TEXT.containsMatchIn(all)) add(PAYMENT_SCREEN)
            if (SCAM_TEXT.containsMatchIn(all)) add(SCAM_RISK)
            if (s.pkg in REMOTE_PKGS) add(REMOTE_ACCESS)
        }
        return s.copy(nodes = s.nodes.map(::redact), flags = s.flags + flags)
    }

    private fun redact(n: SnapNode): SnapNode {
        val hint = listOfNotNull(n.viewId, n.desc, n.text).joinToString(" ")
        val secretField = n.password || (n.editable && SECRET_FIELD.containsMatchIn(hint))
        return n.copy(
            text = if (secretField) null else n.text?.let { mask(it) },
            desc = n.desc?.let { mask(it) },
            password = n.password || secretField,
        )
    }

    /** Masks digit runs of 6+ (OTPs, card/account numbers), keeping the last 2 for recognition. */
    fun mask(t: String): String = DIGITS.replace(t) { m ->
        val d = m.value.filter(Char::isDigit)
        if (d.length < 6) m.value else "•".repeat(d.length - 2) + d.takeLast(2)
    }

    sealed interface Verdict {
        data object Allow : Verdict
        data class AllowWithNote(val note: String) : Verdict
        data class Deny(val reason: String) : Verdict
    }

    fun vet(s: Snapshot, node: SnapNode): Verdict = when {
        node.password -> Verdict.Deny("secret_field: let the user type it themselves; do not point at it")
        PIN_SCREEN in s.flags && KEYPAD_KEY.matches(node.label) ->
            Verdict.Deny("pin_keypad: never guide PIN digits; ask the user to enter their PIN privately")
        node.clickable && PAY_TEXT.containsMatchIn(node.label) && (PAYMENT_SCREEN in s.flags || s.pkg in PAYMENT_PKGS) ->
            Verdict.AllowWithNote("money_action: before the user taps, say the amount and receiver and get a clear yes")
        SCAM_RISK in s.flags || REMOTE_ACCESS in s.flags -> Verdict.AllowWithNote("scam_risk: warn the user first")
        else -> Verdict.Allow
    }

    /** Captions are shown on screen; keep them short and free of digits that could be secrets. */
    fun caption(c: String): String = mask(c).take(60)
}
