package com.r17e.guide

import android.app.Application
import com.r17e.guide.backend.BackendClient
import com.r17e.guide.backend.DeviceRegistry
import com.r17e.guide.backend.LiveTokenProvider
import com.r17e.guide.live.FunctionCall
import com.r17e.guide.session.GuideController
import com.r17e.guide.session.Prompts
import com.r17e.guide.session.ToolHandler
import org.json.JSONArray
import org.json.JSONObject

/** Holds app-wide singletons. */
class R17eApp : Application() {
    val backend by lazy { BackendClient(BuildConfig.BACKEND_URL) }
    val devices by lazy { DeviceRegistry(this, backend, BuildConfig.VERSION_NAME) }
    val liveTokens by lazy { LiveTokenProvider(backend, devices::token, { devices.invalidate(); devices.register() }) }

    fun newController(onState: (GuideController.State) -> Unit) =
        GuideController(liveTokens, NoTools, Prompts.SYSTEM, onState)

    private object NoTools : ToolHandler {
        override val declarations = JSONArray()
        override fun handle(call: FunctionCall) = JSONObject().put("error", "no tools yet")
    }
}
