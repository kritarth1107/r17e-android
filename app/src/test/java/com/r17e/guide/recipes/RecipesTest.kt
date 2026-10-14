package com.r17e.guide.recipes

import com.r17e.guide.a11y.SnapNode
import com.r17e.guide.a11y.Snapshot
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RecipesTest {
    private val book = RecipeBook.parse(File("src/main/assets/recipes.json").readText())
    private fun snap(pkg: String, vararg labels: String) = Snapshot(pkg, "S", 1, 1,
        labels.mapIndexed { i, l -> SnapNode("n$i", "Button", l, null, null, 0, 0, 1, 1, true, false, false) })

    @Test fun assetParsesAndEveryRecipeHasSteps() {
        assertTrue(book.recipes.size >= 4)
        book.recipes.forEach { r -> assertTrue(r.id, r.steps.isNotEmpty()); r.steps.forEach { assertTrue(it.say.containsKey("en") && it.say.containsKey("hi")) } }
    }

    @Test fun matchesEnglishHindiAndPrefersLongerTrigger() {
        assertEquals("whatsapp_video_call", book.match("mujhe beti ko video call karna hai")?.id)
        assertEquals("whatsapp_video_call", book.match("वीडियो कॉल करनी है")?.id)
        assertEquals("phone_call_contact", book.match("call my son")?.id)
        assertNull(book.match("what is the weather"))
    }

    @Test fun runnerAdvancesAndSkipsAhead() {
        val r = RecipeRunner(book.byId("whatsapp_video_call")!!)
        assertNull(r.onSnapshot(snap("com.android.launcher", "Chats")))
        assertEquals(0, r.onSnapshot(snap("com.whatsapp", "Chats", "Calls"))!!.step)
        assertNull(r.onSnapshot(snap("com.whatsapp", "Chats")))
        val g = r.onSnapshot(snap("com.whatsapp", "Chats", "Video call"))!!
        assertEquals(2, g.step); assertEquals("n1", g.node.id); assertTrue(r.isLastStep)
    }
}
