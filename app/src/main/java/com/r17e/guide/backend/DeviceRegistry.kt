package com.r17e.guide.backend

import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * Owns this install's identity and device token. Registers once and caches the token;
 * [invalidate] forces re-registration (e.g. after a 401). Token lives in private prefs
 * (placeholder auth, matches the backend's placeholder scheme).
 */
class DeviceRegistry(ctx: Context, private val client: BackendClient, private val appVersion: String?) {
    private val prefs = ctx.getSharedPreferences("r17e_device", Context.MODE_PRIVATE)

    val installId: String
        get() = prefs.getString(K_INSTALL, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(K_INSTALL, it).apply()
        }

    val deviceId: String? get() = prefs.getString(K_DEVICE, null)

    /** Cached token, registering first if needed. Blocking. */
    @Synchronized
    fun token(): String = prefs.getString(K_TOKEN, null) ?: register().deviceToken

    @Synchronized
    fun register(): Registration {
        val r = client.register(installId, "${Build.MANUFACTURER} ${Build.MODEL}", appVersion)
        prefs.edit().putString(K_DEVICE, r.deviceId).putString(K_TOKEN, r.deviceToken).apply()
        return r
    }

    @Synchronized
    fun invalidate() { prefs.edit().remove(K_TOKEN).apply() }

    private companion object {
        const val K_INSTALL = "install_id"; const val K_DEVICE = "device_id"; const val K_TOKEN = "device_token"
    }
}
