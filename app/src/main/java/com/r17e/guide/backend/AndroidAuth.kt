package com.r17e.guide.backend

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/** EC P-256 key in the Android Keystore (hardware-backed where available, non-exportable). */
class KeystoreSigner(private val alias: String = "r17e_device_key") : DeviceSigner {
    private val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun ensureKey() {
        if (ks.containsAlias(alias)) return
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256).build())
        }.generateKeyPair()
    }

    override fun publicKeyB64(): String { ensureKey(); return Base64.getEncoder().encodeToString(ks.getCertificate(alias).publicKey.encoded) }

    override fun sign(message: String): String {
        ensureKey()
        val s = Signature.getInstance("SHA256withECDSA").apply { initSign(ks.getKey(alias, null) as PrivateKey); update(message.toByteArray()) }
        return Base64.getEncoder().encodeToString(s.sign())
    }

    override fun reset() { if (ks.containsAlias(alias)) ks.deleteEntry(alias) }
}

class PrefsAuthStore(ctx: Context) : AuthStore {
    private val p = ctx.getSharedPreferences("r17e_auth", Context.MODE_PRIVATE)
    override var installId: String? get() = p.getString("install_id", null); set(v) { p.edit().putString("install_id", v).apply() }
    override var deviceId: String? get() = p.getString("device_id", null); set(v) { p.edit().putString("device_id", v).apply() }
    override var refreshToken: String? get() = p.getString("refresh_token", null); set(v) { p.edit().putString("refresh_token", v).apply() }
}
