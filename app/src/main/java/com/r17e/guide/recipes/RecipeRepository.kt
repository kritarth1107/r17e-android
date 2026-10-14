package com.r17e.guide.recipes

import java.io.File

/**
 * Bundled recipes are always available; saved recipes from the backend are layered on top and cached
 * on disk so they also work offline. Any fetch failure just keeps the last good copy.
 */
class RecipeRepository(
    private val bundled: RecipeBook,
    private val cacheFile: File,
    private val fetch: () -> String,
) {
    @Volatile var current: RecipeBook = load(); private set

    private fun load(): RecipeBook = runCatching { RecipeBook.parseRemote(cacheFile.readText()) + bundled }.getOrDefault(bundled)

    /** Blocking. Returns true if fresh saved recipes were applied. */
    fun refresh(): Boolean = runCatching {
        val json = fetch()
        val remote = RecipeBook.parseRemote(json)
        cacheFile.parentFile?.mkdirs(); cacheFile.writeText(json)
        current = remote + bundled
        true
    }.getOrDefault(false)
}
