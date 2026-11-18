package com.grocerylist.app.ui.recipe

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.Ingredient
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.ServerException
import com.grocerylist.app.data.SharedRecipe
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** An ingredient row being edited. Each has its own key so rows keep their text as others are removed. */
class IngredientField(quantity: String, name: String) {
    val key = nextKey++
    var quantity by mutableStateOf(quantity)
    var name by mutableStateOf(name)

    private companion object {
        var nextKey = 0L
    }
}

class StepField(text: String) {
    val key = nextKey++
    var text by mutableStateOf(text)

    private companion object {
        var nextKey = 0L
    }
}

/**
 * Pasting a new recipe ([recipeId] null: paste, read, then check and save) or editing a saved one.
 * Saving drops blank rows; a recipe needs a title.
 */
class RecipeEditViewModel(householdId: String, private val recipeId: String?, private val uid: String) : ViewModel() {
    enum class Phase { PASTE, READING, EDIT }

    private val recipes = AppGraph.recipes(householdId)
    private var original: Recipe? = null

    var phase by mutableStateOf(if (recipeId == null) Phase.PASTE else Phase.EDIT)
        private set
    var pasted by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
        private set
    /** False until a saved recipe has loaded. */
    var loaded by mutableStateOf(recipeId == null)
        private set

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    var servings by mutableStateOf("")
    var minutes by mutableStateOf("")
    val ingredients = mutableStateListOf<IngredientField>()
    val steps = mutableStateListOf<StepField>()
    var notes by mutableStateOf("")

    init {
        if (recipeId == null) {
            // Text shared from another app, now or while this screen is open, is read right away.
            viewModelScope.launch {
                SharedRecipe.pending.filterNotNull().collect {
                    SharedRecipe.take()
                    if (phase == Phase.PASTE) {
                        pasted = it
                        read()
                    }
                }
            }
        }
        if (recipeId != null) {
            viewModelScope.launch {
                fill(recipes.observe(recipeId).filterNotNull().first())
                loaded = true
            }
        }
    }

    private fun fill(recipe: Recipe) {
        original = recipe
        title = recipe.title
        description = recipe.description
        servings = recipe.servings.takeIf { it > 0 }?.toString().orEmpty()
        minutes = recipe.totalMinutes.takeIf { it > 0 }?.toString().orEmpty()
        ingredients.clear()
        recipe.ingredients.mapTo(ingredients) { IngredientField(it.quantity, it.name) }
        steps.clear()
        recipe.steps.mapTo(steps) { StepField(it) }
        notes = recipe.notes
    }

    fun read() {
        val text = pasted.trim()
        if (text.length < MIN_PASTE || phase == Phase.READING) return
        error = null
        phase = Phase.READING
        viewModelScope.launch {
            try {
                fill(recipes.import(text))
                phase = Phase.EDIT
            } catch (e: Exception) {
                Log.w(TAG, "Recipe import failed", e)
                phase = Phase.PASTE
                error = when ((e as? ServerException)?.status) {
                    422 -> "Couldn't find a recipe in that. Make sure you copied the ingredients and steps, " +
                        "or a link to a cooking video."
                    429 -> "That's all the recipe pasting for today. Check back tomorrow."
                    else -> "Couldn't read that recipe. Check your connection and try again."
                }
            }
        }
    }

    fun addIngredient() = ingredients.add(IngredientField("", ""))
    fun addStep() = steps.add(StepField(""))

    private fun edited(): Recipe {
        val base = original ?: Recipe(title = "")
        return base.copy(
            title = title.trim(),
            description = description.trim(),
            servings = servings.trim().toIntOrNull() ?: 0,
            totalMinutes = minutes.trim().toIntOrNull() ?: 0,
            ingredients = ingredients.filter { it.name.isNotBlank() }
                .map { Ingredient(it.name.trim(), it.quantity.trim()) },
            steps = steps.map { it.text.trim() }.filter { it.isNotEmpty() },
            notes = notes.trim(),
        )
    }

    val canSave get() = phase == Phase.EDIT && loaded && title.isNotBlank()

    /** True once there's something that backing out would lose. */
    val hasChanges get() = when (phase) {
        Phase.PASTE -> false
        Phase.READING -> true
        Phase.EDIT -> loaded && (recipeId == null || edited() != original)
    }

    /** Saves and returns the recipe's ID. */
    fun save(): String? {
        if (!canSave) return null
        val recipe = edited()
        return if (recipeId == null) {
            recipes.add(recipe, uid)
        } else {
            recipes.update(recipe)
            recipeId
        }
    }

    companion object {
        private const val TAG = "RecipeEdit"
        /** The server's minimum. */
        const val MIN_PASTE = 20
    }
}
