package com.grocerylist.app.ui.recipe

import androidx.lifecycle.ViewModel
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.RecipeChatSession
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The "Ask for a recipe" screen. The conversation itself is [RecipeChatSession], which outlives the screen (and the
 * app) until someone starts over; recipes someone saves go to the household's recipe box.
 */
class RecipeChatViewModel(val chat: RecipeChatSession) : ViewModel() {
    /** The suggestion open in full, if any. */
    val viewing = MutableStateFlow<Recipe?>(null)
}
