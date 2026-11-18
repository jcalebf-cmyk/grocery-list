package com.grocerylist.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.GroceryList
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.Store
import com.grocerylist.app.data.storesInUse
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val householdName: String = "",
    val stores: List<Store> = emptyList(),
    val yourStores: List<Store> = emptyList(),
    val activeLists: List<GroceryList> = emptyList(),
    /** Closed-out lists, newest first. Home shows only how many; Past lists shows them. */
    val pastLists: List<GroceryList> = emptyList(),
    /** The household's saved recipes, newest first. */
    val recipes: List<Recipe> = emptyList(),
    val kitchenCount: Int = 0,
) {
    val pastListCount get() = pastLists.size
}

class HomeViewModel(householdId: String, private val uid: String) : ViewModel() {
    private val repo = AppGraph.groceries(householdId)
    private val messages = Channel<String>(Channel.BUFFERED)
    val userMessages: Flow<String> = messages.receiveAsFlow()

    val state: StateFlow<HomeState> = combine(
        combine(AppGraph.households.observeHousehold(householdId), repo.observeStores(), repo.observeLists(), ::Triple),
        AppGraph.recipes(householdId).observeAll(),
        repo.observeOnHand(),
    ) { (household, stores, lists), recipes, kitchen ->
        HomeState(
            householdName = household?.name.orEmpty(),
            stores = stores,
            yourStores = storesInUse(stores, lists),
            activeLists = lists.filter { it.isActive },
            pastLists = lists.filterNot { it.isActive },
            recipes = recipes,
            kitchenCount = kitchen.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    fun openListFor(store: Store): String = repo.openListFor(store, uid, state.value.activeLists).id

    fun removeStore(store: Store) = repo.removeStore(store)

    fun closeOut(list: GroceryList) = repo.closeOut(list.id)

    fun deleteList(list: GroceryList) {
        viewModelScope.launch {
            runCatching { repo.deleteList(list.id) }.onFailure { messages.send("Couldn't delete that list. Try again.") }
        }
    }

    fun openListWithoutStore(): String = repo.openListFor(null, uid, state.value.activeLists).id

    fun openListForNewStore(name: String): String = openListFor(repo.addStore(name, state.value.stores))

}
