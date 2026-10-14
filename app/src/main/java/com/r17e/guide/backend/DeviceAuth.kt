package com.r17e.guide.backend

/** Device keypair: the private key never leaves the signer (Android Keystore on device). */
interface DeviceSigner {
    /** Base64 X.509 SPKI DER public key (EC P-256). */
    fun publicKeyB64(): String
    /** Base64 DER ECDSA-SHA256 signature over the UTF-8 message. */
    fun sign(message: String): String
    /** Drop the key so a fresh one is made (used when the backend refuses this install). */
    fun reset()
}

/** Small persisted state. */
interface AuthStore {
    var installId: String?
    var deviceId: String?
    var refreshToken: String?
}

/**
 * Device auth against r17e-backend: register public key once, then get short-lived access tokens by signing
 * a server challenge (`r17e-auth:v1:<deviceId>:<nonce>`); refresh tokens rotate in between.
 */
class DeviceAuth(
    private val client: BackendClient,
    private val signer: DeviceSigner,
    private val store: AuthStore,
    private val deviceModel: String?,
    private val appVersion: String?,
    private val newInstallId: () -> String = { java.util.UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var access: Tokens? = null

    val deviceId: String? get() = store.deviceId

    /** A valid access token, doing whatever registration/sign-in/refresh is needed. Blocking. */
    @Synchronized
    fun accessToken(): String {
        access?.takeIf { now() + 30_000 < it.expiresAtMs }?.let { return it.accessToken }
        val t = store.refreshToken?.let { rt -> runCatching { client.refresh(rt) }.getOrNull() } ?: signIn()
        access = t; store.refreshToken = t.refreshToken
        return t.accessToken
    }

    /** Forget the cached access token (e.g. after a 401); next call refreshes or signs in again. */
    @Synchronized
    fun invalidate() { access = null }

    @Synchronized
    fun ensureRegistered(): String = store.deviceId ?: register()

    private fun signIn(): Tokens {
        val id = ensureRegistered()
        return try { challengeSign(id) } catch (e: BackendException) {
            if (e.code != 404 && e.code != 401) throw e
            store.deviceId = null; challengeSign(register()) // device unknown/revoked server-side: re-register
        }
    }

    private fun challengeSign(id: String): Tokens {
        val ch = client.challenge(id)
        return client.deviceToken(id, ch.challengeId, signer.sign(ch.message))
    }

    private fun register(): String {
        val install = store.installId ?: newInstallId().also { store.installId = it }
        val id = try { client.register(install, signer.publicKeyB64(), deviceModel, appVersion) } catch (e: BackendException) {
            if (e.code != 409) throw e
            // installId is bound to a key we no longer hold: start over as a new install.
            signer.reset(); store.installId = newInstallId()
            client.register(store.installId!!, signer.publicKeyB64(), deviceModel, appVersion)
        }
        store.deviceId = id; store.refreshToken = null
        return id
    }
}
