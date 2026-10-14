package com.r17e.guide.backend

import java.security.MessageDigest
import java.util.Base64

/** Supplies a Play Integrity token for registration, or null when unavailable/unconfigured. */
fun interface IntegrityProvider {
    fun token(nonce: String): String?

    companion object {
        val NONE = IntegrityProvider { null }

        /**
         * Nonce binding the token to this registration: base64url(SHA-256(installId:publicKey)), no padding.
         * The backend can recompute it from the register body when it starts verifying tokens.
         */
        fun nonceFor(installId: String, publicKey: String): String =
            Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest("$installId:$publicKey".toByteArray()))
    }
}
