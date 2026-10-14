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

    fun newController(onState: (GuideController.State) -> Unit) =
        GuideController(liveTokens, screenTools(), Prompts.system(prefs.lang), onState, beforeConnect = { recipeRepo.refresh() })

    val recipeRepo by lazy {
        RecipeRepository(
            bundled = RecipeBook.parse(assets.open("recipes.json").bufferedReader().use { it.readText() }),
            cacheFile = java.io.File(filesDir, "saved_recipes.json"),
            fetch = { backend.recipesJson(auth.accessToken()) },
        )
    }
    val recipes: RecipeBook get() = recipeRepo.current

    private fun screenTools() = ScreenTools({ GuideAccessibilityService.instance?.current }, OverlayPointer, recipes = { recipeRepo.current })
}
