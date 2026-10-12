package com.grocerylist.app.data

/**
 * True if an on-hand item covers a recipe ingredient: every word of the on-hand item appears in the
 * ingredient ("ground beef" covers "lean ground beef", "chicken" covers "chicken breast").
 * Whole words only, so "salt" doesn't cover "unsalted butter".
 */
fun isCoveredBy(ingredient: String, onHand: Collection<String>): Boolean {
    val ingredientWords = words(ingredient)
    return onHand.any { item -> words(item).let { it.isNotEmpty() && ingredientWords.containsAll(it) } }
}

// Drops a trailing "s" so "eggs" matches "egg" (and "tomatoes" matches "tomatoe", harmlessly).
private fun words(text: String): Set<String> =
    itemKey(text).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.mapTo(HashSet()) { it.removeSuffix("s") }
