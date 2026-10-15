package com.r17e.guide

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.r17e.guide.i18n.Lang
import com.r17e.guide.i18n.Prefs
import com.r17e.guide.i18n.S
import com.r17e.guide.ui.MainActivity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Walks real screens. Saves and restores the language and onboarding prefs so a dev emulator keeps its state. */
@RunWith(AndroidJUnit4::class)
class UiFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val prefs = Prefs(InstrumentationRegistry.getInstrumentation().targetContext)
    private var savedLang = Lang.EN; private var savedOnboarded = false
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun save() { savedLang = prefs.lang; savedOnboarded = prefs.onboarded }
    @After fun restore() { scenario?.close(); prefs.lang = savedLang; prefs.onboarded = savedOnboarded }

    private fun launch(lang: Lang, onboarded: Boolean) {
        prefs.lang = lang; prefs.onboarded = onboarded
        scenario = ActivityScenario.launch(MainActivity::class.java); rule.waitForIdle()
    }

    @Test fun onboardingSwitchesLanguageLive() {
        launch(Lang.EN, onboarded = false)
        rule.onNodeWithText(S.get(Lang.EN, "choose_lang")).assertExists()
        rule.onNodeWithText("हिन्दी").performClick()
        rule.onNodeWithText(S.get(Lang.HI, "choose_lang")).assertExists()
    }

    @Test fun onboardingContinueAdvancesToHowItWorks() {
        launch(Lang.EN, onboarded = false)
        rule.onNodeWithText(S.get(Lang.EN, "continue")).performClick()
        rule.onNodeWithText(S.get(Lang.EN, "how_title")).assertExists()
    }

    @Test fun settingsOpensFromHomeAndEverythingIsLabelled() {
        launch(Lang.EN, onboarded = true)
        rule.onNodeWithContentDescription(S.get(Lang.EN, "settings")).performClick()
        rule.onNodeWithText(S.get(Lang.EN, "language")).assertExists()
        for (n in rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()) {
            val t = n.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { it.text }.orEmpty() +
                n.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.joinToString().orEmpty() +
                n.children.joinToString { c -> c.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { it.text }.orEmpty() }
            assertTrue("unlabelled clickable in Settings at ${n.boundsInRoot}", t.isNotBlank() || n.children.isNotEmpty())
        }
    }

    @Test fun homeInHindiUsesHindiButton() {
        launch(Lang.HI, onboarded = true)
        val start = S.get(Lang.HI, "start"); val stop = S.get(Lang.HI, "stop")
        val found = runCatching { rule.onNodeWithText(start).assertExists() }.isSuccess ||
            runCatching { rule.onNodeWithText(stop).assertExists() }.isSuccess
        assertTrue("Hindi start/stop button not shown", found)
    }

    @Test fun homeHasNoEmoji() {
        launch(Lang.EN, onboarded = true)
        val emoji = Regex("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]")
        for (n in rule.onAllNodes(androidx.compose.ui.test.hasText("", substring = true)).fetchSemanticsNodes()) {
            val t = n.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { it.text }.orEmpty()
            assertTrue("emoji in \"$t\"", !emoji.containsMatchIn(t))
        }
    }
}
