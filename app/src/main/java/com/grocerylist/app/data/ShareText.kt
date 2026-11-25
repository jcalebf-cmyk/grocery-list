package com.grocerylist.app.data

/**
 * Plain text for sending a recipe or a list by text or email through the share sheet.
 * Same wording as `recipeShareText` / `listShareText` in Logic.swift.
 */

/** The recipe as written (its own servings), with the video link if it came from one. */
fun recipeShareText(recipe: Recipe): String = buildString {
    append(recipe.title.trim())
    if (recipe.description.isNotBlank()) append("\n").append(recipe.description.trim())
    val summary = listOfNotNull(
        recipe.totalMinutes.takeIf { it > 0 }?.let { "$it min" },
        recipe.servings.takeIf { it > 0 }?.let { "Serves $it" },
    ).joinToString(" · ")
    if (summary.isNotEmpty()) append("\n").append(summary)
    if (recipe.ingredients.isNotEmpty()) {
        append("\n\nIngredients")
        for (ingredient in recipe.ingredients) append("\n• ").append(ingredientLine(ingredient))
    }
    if (recipe.steps.isNotEmpty()) {
        append("\n\nSteps")
        recipe.steps.forEachIndexed { i, step -> append("\n${i + 1}. ").append(step.trim()) }
    }
    if (recipe.notes.isNotBlank()) append("\n\nNotes\n").append(recipe.notes.trim())
    if (recipe.sourceUrl.isNotBlank()) append("\n\nVideo: ").append(recipe.sourceUrl.trim())
}

/** "2 cloves garlic", or "Salt" when there's no amount. */
private fun ingredientLine(ingredient: Ingredient): String {
    val name = ingredient.name.trim()
    val quantity = ingredient.quantity.trim()
    return if (quantity.isEmpty()) name.replaceFirstChar(Char::uppercase) else "$quantity $name"
}

/** What's still to get on a list, in aisle order. Null when everything's checked off. */
fun listShareText(list: GroceryList, items: List<ListItem>): String? {
    val toGet = groupByAisle(items).filter { it.aisle != null }.flatMap { it.items }
    if (toGet.isEmpty()) return null
    return listShareSubject(list) + toGet.joinToString("") { "\n• " + itemLine(it) }
}

/** "Soup (the low-sodium one)", or just the name when there's no note. */
private fun itemLine(item: ListItem): String {
    val note = cleanNote(item.note.orEmpty())
    return item.name.trim() + if (note == null) "" else " ($note)"
}

/** The email subject for a shared list. */
fun listShareSubject(list: GroceryList): String =
    if (list.storeName.isBlank()) "Shopping list" else "${list.storeName.trim()} list"
