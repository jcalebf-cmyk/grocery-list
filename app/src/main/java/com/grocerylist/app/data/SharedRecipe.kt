package com.grocerylist.app.data

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Text shared to Groceries from another app (a recipe, or a YouTube link), waiting for the paste screen.
 * Kept in memory until someone signed in to a household picks it up.
 */
object SharedRecipe {
    val pending = MutableStateFlow<String?>(null)

    /** The waiting text, now handled. */
    fun take(): String? = pending.value.also { pending.value = null }
}
