package com.r17e.guide

import android.app.Application
import com.r17e.guide.backend.BackendClient
import com.r17e.guide.backend.DeviceRegistry
import com.r17e.guide.backend.LiveTokenProvider
import com.r17e.guide.a11y.GuideAccessibilityService
import com.r17e.guide.overlay.OverlayPointer
import com.r17e.guide.session.GuideController
import com.r17e.guide.session.Prompts
import com.r17e.guide.session.ScreenTools

/** Holds app-wide singletons. */
class R17eApp : Application() {
    val backend by lazy { BackendClient(BuildConfig.BACKEND_URL) }
    val devices by lazy { DeviceRegistry(this, backend, BuildConfig.VERSION_NAME) }
    val liveTokens by lazy { LiveTokenProvider(backend, devices::token, { devices.invalidate(); devices.register() }) }

    fun newController(onState: (GuideController.State) -> Unit) =
        GuideController(liveTokens, screenTools(), Prompts.SYSTEM, onState)

    private fun screenTools() = ScreenTools({ GuideAccessibilityService.instance?.current }, OverlayPointer)
}
