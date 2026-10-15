package com.r17e.guide

import android.app.Application
import com.r17e.guide.backend.BackendClient
import com.r17e.guide.backend.DeviceAuth
import com.r17e.guide.backend.KeystoreSigner
import com.r17e.guide.backend.PlayIntegrity
import com.r17e.guide.backend.PrefsAuthStore
import com.r17e.guide.backend.LiveTokenProvider
import com.r17e.guide.a11y.GuideAccessibilityService
import com.r17e.guide.overlay.OverlayPointer
import com.r17e.guide.recipes.RecipeBook
import com.r17e.guide.recipes.RecipeRepository
import com.r17e.guide.session.GuideController
import com.r17e.guide.session.Prompts
import com.r17e.guide.session.ScreenTools

/** Holds app-wide singletons. */
class R17eApp : Application() {
    val prefs by lazy { com.r17e.guide.i18n.Prefs(this) }
    val backend by lazy { BackendClient(BuildConfig.BACKEND_URL) }
    val auth by lazy {
        DeviceAuth(backend, KeystoreSigner(), PrefsAuthStore(this), "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}", BuildConfig.VERSION_NAME,
            integrity = playIntegrity)
    }
    val playIntegrity by lazy { PlayIntegrity(this, BuildConfig.GCP_PROJECT_NUMBER) }
    val liveTokens by lazy { LiveTokenProvider(backend, auth::accessToken, auth::invalidate) }

    fun newController(onState: (GuideController.State) -> Unit, onIdle: () -> Unit = {}) =
        GuideController(liveTokens, screenTools(), Prompts.system(prefs.lang), onState,
            beforeConnect = { recipeRepo.refresh(); runCatching { safetyReporter.flush() } }, onIdle = onIdle)

    val recipeRepo by lazy {
        RecipeRepository(
            bundled = RecipeBook.parse(assets.open("recipes.json").bufferedReader().use { it.readText() }),
            cacheFile = java.io.File(filesDir, "saved_recipes.json"),
            fetch = { backend.recipesJson(auth.accessToken()) },
        )
    }
    val safetyReporter by lazy {
        com.r17e.guide.safety.SafetyReporter(java.io.File(filesDir, "safety_queue.json"), { backend.safetyEvent(auth.accessToken(), it) })
    }
    private val bg = java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it, "r17e-bg") }

    /** Safety alerts go to the family only after the elder linked a family (consent); metadata only. */
    fun onSafetySnapshot(snap: com.r17e.guide.a11y.Snapshot) {
        val f = com.r17e.guide.safety.SafetyPolicy.filter(snap)
        if (f.flags.isEmpty()) return
        // While the helper is running, speak up about a scam screen without waiting to be asked (rate-limited).
        val svc = com.r17e.guide.session.GuideSessionService.instance
        if (svc != null && com.r17e.guide.safety.SafetyPolicy.SCAM_RISK in f.flags &&
            scamWarner.shouldWarn(f.pkg, android.os.SystemClock.elapsedRealtime())) svc.warnScam()
        if (prefs.familyGroup != null) bg.execute { runCatching { safetyReporter.onSnapshot(f) } }
    }
    private val scamWarner = com.r17e.guide.safety.ScamWarner()

    fun flushSafety() = bg.execute { runCatching { safetyReporter.flush() } }

    val recipes: RecipeBook get() = recipeRepo.current

    private fun screenTools() = ScreenTools({ GuideAccessibilityService.instance?.current }, OverlayPointer, recipes = { recipeRepo.current })
}
