package com.r17e.guide

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.r17e.guide.a11y.GuideAccessibilityService
import com.r17e.guide.ui.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun firstScreenRenders() {
        rule.waitForIdle()
        rule.onRoot().assertExists()
    }

    /** TalkBack audit: every tappable element must announce something. */
    @Test fun everyClickableHasALabel() {
        rule.waitForIdle()
        val nodes = rule.onAllNodes(hasClickAction(), useUnmergedTree = false).fetchSemanticsNodes()
        assertTrue("expected some clickable nodes", nodes.isNotEmpty())
        for (n in nodes) {
            val c = n.config
            val text = c.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { it.text }.orEmpty()
            val desc = c.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.joinToString().orEmpty()
            val merged = text + desc + collectChildText(n)
            assertTrue("unlabelled clickable at ${n.boundsInRoot}", merged.isNotBlank())
        }
    }

    /** Elder-friendly targets: tappable elements are at least 48dp in both directions. */
    @Test fun touchTargetsAtLeast48dp() {
        rule.waitForIdle()
        val density = rule.activity.resources.displayMetrics.density
        for (n in rule.onAllNodes(hasClickAction()).fetchSemanticsNodes()) {
            // Use the unclipped layout size: boundsInRoot is cut off by the scroll viewport at the screen edge.
            val w = n.size.width / density; val h = n.size.height / density
            assertTrue("small target ${w}x${h}dp at ${n.boundsInRoot}", w >= 47.5f && h >= 47.5f)
        }
    }

    @Test fun activityReportsForegroundForBubble() {
        rule.waitForIdle()
        assertTrue(GuideAccessibilityService.appInFront)
        rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.STARTED)
        assertFalse(GuideAccessibilityService.appInFront)
    }

    private fun collectChildText(n: androidx.compose.ui.semantics.SemanticsNode): String =
        n.children.joinToString("") { ch ->
            (ch.config.getOrElseNullable(SemanticsProperties.Text) { null }?.joinToString { it.text }.orEmpty()) +
                (ch.config.getOrElseNullable(SemanticsProperties.ContentDescription) { null }?.joinToString().orEmpty()) +
                collectChildText(ch)
        }
}
