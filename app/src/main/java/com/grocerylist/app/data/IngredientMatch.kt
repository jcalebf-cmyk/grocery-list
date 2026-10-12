package com.grocerylist.app.data

/**
 * True if an on-hand item covers a recipe ingredient: every word of the on-hand item appears in the
 * ingredient ("ground beef" covers "lean ground beef", "chicken" covers "chicken breast").
 * Whole words only, so "salt" doesn't cover "unsalted butter". An extra word that names a different
 * product breaks the match, so "rice" doesn't cover "rice vinegar" and "chicken" doesn't cover "chicken broth".
 */
fun isCoveredBy(ingredient: String, onHand: Collection<String>): Boolean {
    val ingredientWords = words(ingredient)
    return onHand.any { item ->
        val itemWords = words(item)
        itemWords.isNotEmpty() && ingredientWords.containsAll(itemWords) &&
            (ingredientWords - itemWords).none { it in productWords }
    }
}

// Words that make "<item> <word>" a different thing to buy than the item itself. Singular, since words()
// drops a trailing "s". Keep in step with Logic.swift.
private val productWords = setOf(
    "vinegar", "flour", "noodle", "wine", "paper", "cake", "cracker", "chip", "milk", "cream", "butter",
    "cheese", "oil", "sauce", "paste", "powder", "flake", "broth", "stock", "bouillon", "soup", "starch",
    "meal", "syrup", "extract", "jam", "jelly", "spread", "dressing", "seasoning", "bread",
)

// Drops a trailing "s" so "eggs" matches "egg" (and "tomatoes" matches "tomatoe", harmlessly).
private fun words(text: String): Set<String> =
    itemKey(text).split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }.mapTo(HashSet()) { it.removeSuffix("s") }
