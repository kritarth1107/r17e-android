package com.r17e.guide.recipes

import com.r17e.guide.a11y.SnapNode
import com.r17e.guide.a11y.Snapshot
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RecipeRepositoryTest {
    private val bundled = RecipeBook.parse(File("src/main/assets/recipes.json").readText())
    private val remote = """{"recipes":[{"id":"11111111-1111-1111-1111-111111111111","title":"Call Beta on WhatsApp","description":null,
        "appPackage":"com.whatsapp","steps":[{"instruction":"Tap Beta's chat","target":"Beta (son)"},{"instruction":"Tap the phone icon","target":"Voice call"},{"instruction":"Wait"}],"source":"manual"}]}"""

    @Test fun remoteLayersOverBundledAndCaches() {
        val f = Files.createTempDirectory("r").resolve("c/recipes.json").toFile()
        var fail = false
        val repo = RecipeRepository(bundled, f) { if (fail) error("offline") else remote }
        assertEquals(bundled.recipes.size, repo.current.recipes.size)
        assertTrue(repo.refresh())
        assertEquals("saved:11111111-1111-1111-1111-111111111111", repo.current.recipes.first().id)
        assertEquals(bundled.recipes.size + 1, repo.current.recipes.size)
        fail = true
        assertFalse(repo.refresh()); assertEquals(bundled.recipes.size + 1, repo.current.recipes.size)
        // New process, offline: cache still used.
        assertEquals(bundled.recipes.size + 1, RecipeRepository(bundled, f) { error("offline") }.current.recipes.size)
    }

    @Test fun badCacheFallsBackToBundled() {
        val f = Files.createTempFile("r", ".json").toFile().apply { writeText("garbage") }
        assertEquals(bundled.recipes.size, RecipeRepository(bundled, f) { error("x") }.current.recipes.size)
    }

    @Test fun savedRecipeMatchesByTitleWordsAndRunsLiteralTargets() {
        val book = RecipeBook.parseRemote(remote) + bundled
        assertEquals("saved:11111111-1111-1111-1111-111111111111", book.match("call beta on whatsapp")?.id)
        assertEquals("saved:11111111-1111-1111-1111-111111111111", book.match("I want to ring Beta using WhatsApp")?.id)
        val r = RecipeRunner(book.recipes.first())
        val snap = Snapshot("com.whatsapp", "S", 1, 1, listOf(SnapNode("n1", "View", "Beta (son)", null, null, 0, 0, 1, 1, true, false, false)))
        assertEquals(0, r.onSnapshot(snap)!!.step) // "(" in target is literal, not regex
    }
}
