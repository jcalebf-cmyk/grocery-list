package com.grocerylist.app.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.GroceryList
import com.grocerylist.app.data.Ingredient
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.Store
import com.grocerylist.app.data.storesInUse
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/** What a recipe screen needs besides the recipe: stores and lists to add ingredients to. */
data class ShoppingState(
    val stores: List<Store> = emptyList(),
    val yourStores: List<Store> = emptyList(),
    val activeLists: List<GroceryList> = emptyList(),
)

/** A saved recipe ([recipeId]), or none for a recipe the chat passes in directly. */
class RecipeViewModel(householdId: String, recipeId: String?, private val uid: String) : ViewModel() {
    private val repo = AppGraph.groceries(householdId)
    private val recipes = AppGraph.recipes(householdId)

    val recipe: StateFlow<Recipe?> = (if (recipeId != null) recipes.observe(recipeId) else flowOf(null))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val shopping: StateFlow<ShoppingState> = combine(repo.observeStores(), repo.observeLists()) { stores, lists ->
        ShoppingState(
            stores = stores,
            yourStores = storesInUse(stores, lists),
            activeLists = lists.filter { it.isActive },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShoppingState())

    /** Adds the ingredients to the store's open list (creating one if needed) and returns its ID. */
    fun addToList(store: Store?, ingredients: List<Ingredient>): String {
        val list = repo.openListFor(store, uid, shopping.value.activeLists)
        ingredients.forEach { repo.addItem(list, it.name.replaceFirstChar(Char::uppercase), uid) }
        return list.id
    }

    fun addToNewStore(name: String, ingredients: List<Ingredient>): String =
        addToList(repo.addStore(name, shopping.value.stores), ingredients)

    fun delete(id: String) = recipes.delete(id)
}
