package com.grocerylist.app.data

import java.text.Normalizer

/** A recipe a search found. [ingredient] is the one that matched, when the title alone doesn't show why it's here. */
data class RecipeMatch(val recipe: Recipe, val ingredient: String? = null)

/**
 * Recipes with every word of [query], best first: in the title, then in an ingredient, then in the description,
 * notes or video channel. Within each, they stay in the order given. Accents and plurals don't matter ("jalapeno",
 * "tomatoes"), and part of a word is enough. A blank query gives every recipe. Same as `searchRecipes` in Logic.swift.
 */
fun searchRecipes(recipes: List<Recipe>, query: String): List<RecipeMatch> {
    val words = searchWords(query)
    if (words.isEmpty()) return recipes.map { RecipeMatch(it) }
    return recipes.mapNotNull { recipe ->
        val title = SearchText(recipe.title)
        val notInTitle = words.filterNot(title::has)
        if (notInTitle.isEmpty()) return@mapNotNull 0 to RecipeMatch(recipe)
        val ingredients = recipe.ingredients.map { it.name to SearchText(it.name) }
        val leftOver = notInTitle.filterNot { word -> ingredients.any { it.second.has(word) } }
        if (leftOver.isEmpty()) {
            val best = ingredients.maxByOrNull { (_, text) -> notInTitle.count(text::has) }
            // Just the part that matched: "boneless, skinless chicken thighs" is "skinless chicken thighs".
            val part = best?.first?.split(",")?.map { it.trim() }?.maxByOrNull { SearchText(it).let { text -> notInTitle.count(text::has) } }
            return@mapNotNull 1 to RecipeMatch(recipe, part)
        }
        val rest = SearchText(listOf(recipe.description, recipe.notes, recipe.sourceName).joinToString(" "))
        if (leftOver.all(rest::has)) 2 to RecipeMatch(recipe) else null
    }.sortedBy { it.first }.map { it.second }
}

internal fun searchWords(text: String): List<String> =
    aisleWords(Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), ""))

private class SearchText(text: String) {
    private val words = searchWords(text)
    private val forms = words.flatMapTo(HashSet()) { wordForms(it) }

    /** Part of a word, or the same word in the singular or plural. */
    fun has(word: String) = words.any { word in it } || wordForms(word).any { it in forms }
}
