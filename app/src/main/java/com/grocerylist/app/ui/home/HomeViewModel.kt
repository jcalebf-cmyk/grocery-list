package com.grocerylist.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.GroceryList
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.Store
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val householdName: String = "",
    val stores: List<Store> = emptyList(),
    val activeLists: List<GroceryList> = emptyList(),
    val pastLists: List<GroceryList> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
)

@OptIn(FlowPreview::class)
class HomeViewModel(householdId: String, private val uid: String) : ViewModel() {
    private val repo = AppGraph.groceries(householdId)
    private val recipeRepo = AppGraph.recipes(householdId)

    val state: StateFlow<HomeState> = combine(
        AppGraph.households.observeHousehold(householdId),
        repo.observeStores(),
        repo.observeLists(),
        recipeRepo.observe(),
    ) { household, stores, lists, recipes ->
        HomeState(
            householdName = household?.name.orEmpty(),
            stores = stores,
            activeLists = lists.filter { it.isActive },
            pastLists = lists.filterNot { it.isActive }.take(PAST_TRIPS_SHOWN),
            recipes = recipes?.recipes.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        // The first snapshot can come from an empty local cache, so wait for the stats to settle.
        // The repository skips the network call when today's recipes already exist.
        viewModelScope.launch {
            repo.observeStats().debounce(STATS_SETTLE_MS).collect { recipeRepo.refreshIfStale(it) }
        }
    }

    fun openListFor(store: Store): String = repo.openListFor(store, uid, state.value.activeLists).id

    fun addStore(name: String) {
        if (name.isNotBlank()) repo.addStore(name)
    }

    private companion object {
        const val PAST_TRIPS_SHOWN = 10
        const val STATS_SETTLE_MS = 2_000L
    }
}
