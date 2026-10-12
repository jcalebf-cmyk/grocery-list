package com.grocerylist.app.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.GroceryList
import com.grocerylist.app.data.Ingredient
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.Store
import com.grocerylist.app.data.isCoveredBy
import com.grocerylist.app.data.storesInUse
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class RecipeState(
    val recipe: Recipe? = null,
    val stores: List<Store> = emptyList(),
    val yourStores: List<Store> = emptyList(),
    val activeLists: List<GroceryList> = emptyList(),
    val onHandNames: List<String> = emptyList(),
) {
    /** Ingredients the household already has; these start unchecked. */
    val onHandIndices: Set<Int>
        get() = recipe?.ingredients.orEmpty().indices.filterTo(HashSet()) {
            isCoveredBy(recipe!!.ingredients[it].name, onHandNames)
        }
}

class RecipeViewModel(householdId: String, videoId: String, private val uid: String) : ViewModel() {
    private val repo = AppGraph.groceries(householdId)

    val state: StateFlow<RecipeState> = combine(
        AppGraph.recipes(householdId).observe(),
        repo.observeStores(),
        repo.observeLists(),
        repo.observeOnHand(),
    ) { recipes, stores, lists, onHand ->
        RecipeState(
            recipe = recipes?.recipes?.firstOrNull { it.videoId == videoId },
            stores = stores,
            yourStores = storesInUse(stores, lists),
            activeLists = lists.filter { it.isActive },
            onHandNames = onHand.map { it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecipeState())

    /** Adds the ingredients to the store's open list (creating one if needed) and returns its ID. */
    fun addToList(store: Store?, ingredients: List<Ingredient>): String {
        val list = repo.openListFor(store, uid, state.value.activeLists)
        ingredients.forEach { repo.addItem(list, it.name.replaceFirstChar(Char::uppercase), uid) }
        return list.id
    }

    fun addToNewStore(name: String, ingredients: List<Ingredient>): String =
        addToList(repo.addStore(name, state.value.stores), ingredients)
}
