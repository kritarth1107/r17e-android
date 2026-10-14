package com.r17e.guide.recipes

import com.r17e.guide.a11y.SnapNode
import com.r17e.guide.a11y.Snapshot
import org.json.JSONArray
import org.json.JSONObject

data class Step(val match: List<Regex>, val say: Map<String, String>) {
    fun target(s: Snapshot): SnapNode? = s.nodes.firstOrNull { n ->
        val labels = listOfNotNull(n.text, n.desc)
        labels.any { l -> match.any { it.containsMatchIn(l.trim()) } }
    }
}

data class Recipe(val id: String, val title: Map<String, String>, val triggers: List<String>, val pkg: String?, val steps: List<Step>)

/** Hand-written guides for common tasks. Used offline, and offered to the model as a hint when online. */
class RecipeBook(val recipes: List<Recipe>) {

    /** Best recipe for a spoken request, by trigger phrase overlap; null if nothing fits. */
    fun match(utterance: String): Recipe? {
        val u = utterance.lowercase()
        return recipes.map { r -> r to r.triggers.filter { u.contains(it.lowercase()) }.maxOfOrNull { it.length } }
            .filter { it.second != null }.maxByOrNull { it.second!! }?.first
    }

    fun byId(id: String) = recipes.firstOrNull { it.id == id }

    companion object {
        fun parse(json: String) = RecipeBook(JSONArray(json).let { a -> (0 until a.length()).map { recipe(a.getJSONObject(it)) } })

        private fun recipe(o: JSONObject) = Recipe(
            id = o.getString("id"),
            title = map(o.getJSONObject("title")),
            triggers = strings(o.getJSONArray("triggers")),
            pkg = o.optString("pkg").takeIf { it.isNotEmpty() && it != "null" },
            steps = o.getJSONArray("steps").let { s -> (0 until s.length()).map { i ->
                val st = s.getJSONObject(i)
                Step(strings(st.getJSONArray("match")).map { Regex(it, RegexOption.IGNORE_CASE) }, map(st.getJSONObject("say")))
            } },
        )
        private fun strings(a: JSONArray) = (0 until a.length()).map { a.getString(it) }
        private fun map(o: JSONObject) = o.keys().asSequence().associateWith { o.getString(it) }
    }
}

/** Tracks progress through one recipe from successive snapshots. Pure logic, no Android. */
class RecipeRunner(val recipe: Recipe) {
    var stepIndex = -1; private set

    data class Guidance(val step: Int, val node: SnapNode, val say: Map<String, String>)

    /**
     * Picks the furthest step (not behind the current one) whose target is on screen.
     * Returns guidance when the step changes; null if nothing new to say.
     */
    fun onSnapshot(s: Snapshot): Guidance? {
        if (recipe.pkg != null && s.pkg != recipe.pkg) return null
        val from = maxOf(stepIndex, 0)
        for (i in recipe.steps.indices.reversed()) {
            if (i < from) break
            val n = recipe.steps[i].target(s) ?: continue
            if (i == stepIndex) return null
            stepIndex = i
            return Guidance(i, n, recipe.steps[i].say)
        }
        return null
    }

    val isLastStep get() = stepIndex == recipe.steps.lastIndex
}
