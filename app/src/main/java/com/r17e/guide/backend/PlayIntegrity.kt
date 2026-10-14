package com.r17e.guide.backend

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import java.util.concurrent.TimeUnit

/**
 * Classic Play Integrity request. Needs the GCP project number (gradle property r17e.gcpProjectNumber);
 * with 0 it is a no-op and registration goes ahead without a token. Blocking; call off the main thread.
 */
class PlayIntegrity(private val ctx: Context, private val cloudProjectNumber: Long) : IntegrityProvider {
    val configured get() = cloudProjectNumber > 0

    override fun token(nonce: String): String? {
        if (!configured) { Log.i(TAG, "Play Integrity skipped: r17e.gcpProjectNumber not set"); return null }
        return try {
            val req = IntegrityTokenRequest.builder().setNonce(nonce).setCloudProjectNumber(cloudProjectNumber).build()
            Tasks.await(IntegrityManagerFactory.create(ctx).requestIntegrityToken(req), 20, TimeUnit.SECONDS).token()
        } catch (e: Exception) {
            Log.w(TAG, "Play Integrity token failed; registering without it", e); null
        }
    }

    private companion object { const val TAG = "R17E_INTEGRITY" }
}
