package com.r17e.guide

import android.app.Application
import com.r17e.guide.backend.BackendClient
import com.r17e.guide.backend.DeviceRegistry

/** Holds app-wide singletons. */
class R17eApp : Application() {
    val backend by lazy { BackendClient(BuildConfig.BACKEND_URL) }
    val devices by lazy { DeviceRegistry(this, backend, BuildConfig.VERSION_NAME) }
}
