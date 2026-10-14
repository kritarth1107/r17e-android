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
        recipes.map { r -> r to r.triggers.filter { u.contains(it.lowercase()) }.maxOfOrNull { it.length } }
            .filter { it.second != null }.maxByOrNull { it.second!! }?.first?.let { return it }
        // Fallback for saved recipes without curated triggers: word overlap with the title.
        val words = words(u)
        if (words.isEmpty()) return null
        return recipes.map { r -> r to (r.title.values.flatMap { words(it.lowercase()) }.toSet() intersect words).size }
            .filter { it.second >= 2 }.maxByOrNull { it.second }?.first
    }

    private fun words(s: String) = s.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length >= 3 && it !in STOP }.toSet()

    /** Recipes from this book first, then [fallback]'s recipes whose ids aren't already present. */
    operator fun plus(fallback: RecipeBook) = RecipeBook(recipes + fallback.recipes.filter { f -> recipes.none { it.id == f.id } })

    fun byId(id: String) = recipes.firstOrNull { it.id == id }

    companion object {
        private val STOP = setOf("the", "and", "how", "for", "kaise", "karna", "karun", "hai", "mujhe", "want", "with", "use", "करना", "कैसे")

        /**
         * Parses GET /v1/recipes ({recipes:[{id,title,description,appPackage,steps:[{instruction,target?}]}]}).
         * A step's target is matched literally against node text/description.
         */
        fun parseRemote(json: String): RecipeBook {
            val arr = org.json.JSONObject(json).optJSONArray("recipes") ?: JSONArray()
            return RecipeBook((0 until arr.length()).mapNotNull { i ->
                val o = arr.getJSONObject(i)
                val steps = o.optJSONArray("steps") ?: return@mapNotNull null
                val title = o.optString("title").ifEmpty { return@mapNotNull null }
                Recipe(
                    id = "saved:" + o.getString("id"),
                    title = mapOf("en" to title, "hi" to title),
                    triggers = listOf(title.lowercase()),
                    pkg = o.optString("appPackage").takeIf { it.isNotEmpty() && it != "null" },
                    steps = (0 until steps.length()).map { j ->
                        val st = steps.getJSONObject(j)
                        val instr = st.optString("instruction")
                        val target = st.optString("target").takeIf { it.isNotBlank() && it != "null" }
                        Step(listOfNotNull(target?.let { Regex(Regex.escape(it.trim()), RegexOption.IGNORE_CASE) }), mapOf("en" to instr, "hi" to instr))
                    },
                )
            })
        }

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
