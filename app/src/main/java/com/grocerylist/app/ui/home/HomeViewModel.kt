package com.grocerylist.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.GroceryList
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.RefreshResult
import com.grocerylist.app.data.Store
import com.grocerylist.app.data.storesInUse
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeState(
    val householdName: String = "",
    val stores: List<Store> = emptyList(),
    val yourStores: List<Store> = emptyList(),
    val activeLists: List<GroceryList> = emptyList(),
    val pastLists: List<GroceryList> = emptyList(),
    val recipes: List<Recipe> = emptyList(),
    val recipesUpdatedAt: Long? = null,
    val refreshingRecipes: Boolean = false,
    val kitchenCount: Int = 0,
    /** True until the household picks (or skips) favorite cuisines. */
    val needsCuisines: Boolean = false,
    val mood: List<String> = emptyList(),
)

@OptIn(FlowPreview::class)
class HomeViewModel(householdId: String, private val uid: String) : ViewModel() {
    private val repo = AppGraph.groceries(householdId)
    private val recipeRepo = AppGraph.recipes(householdId)

    private val stats = repo.observeStats().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val onHand = repo.observeOnHand().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val prefs = recipeRepo.observePreferences().stateIn(viewModelScope, SharingStarted.Eagerly, null) // null while loading
    private var inputsLoaded = false

    private val messages = Channel<String>(Channel.BUFFERED)
    val userMessages: Flow<String> = messages.receiveAsFlow()

    val state: StateFlow<HomeState> = combine(
        combine(AppGraph.households.observeHousehold(householdId), repo.observeStores(), repo.observeLists(), ::Triple),
        recipeRepo.observe(),
        recipeRepo.refreshing,
        onHand,
        prefs,
    ) { (household, stores, lists), recipes, refreshing, kitchen, cuisines ->
        HomeState(
            householdName = household?.name.orEmpty(),
            stores = stores,
            yourStores = storesInUse(stores, lists),
            activeLists = lists.filter { it.isActive },
            pastLists = lists.filterNot { it.isActive }.take(PAST_LISTS_SHOWN),
            recipes = recipes?.recipes.orEmpty(),
            recipesUpdatedAt = recipes?.generatedAt,
            refreshingRecipes = refreshing,
            kitchenCount = kitchen.size,
            needsCuisines = cuisines?.chosen == false,
            mood = recipes?.mood.orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init {
        // The first snapshots can come from an empty local cache, so wait for the data to settle
        // before deciding whether recipes need refreshing. A new household picks cuisines first.
        viewModelScope.launch {
            combine(stats, onHand, ::Pair).debounce(SETTLE_MS).first()
            prefs.first { it?.chosen == true }
            inputsLoaded = true
            refresh(mood = null, force = false)
        }
    }

    /** Called whenever the home screen comes back into view, e.g. after editing on-hand items or cuisines. */
    fun onShown() {
        if (inputsLoaded) viewModelScope.launch { refresh(mood = null, force = false) }
    }

    fun saveCuisines(cuisines: List<String>) = recipeRepo.savePreferences(cuisines)

    fun refreshRecipes() = refreshWithMessages(mood = null)

    /** "Tonight I'm feeling…": new recipes from just these cuisines. Empty goes back to the usual mix. */
    fun setMood(cuisines: List<String>) = refreshWithMessages(mood = cuisines)

    private suspend fun refresh(mood: List<String>?, force: Boolean) =
        recipeRepo.refresh(stats.value, onHand.value, prefs.value?.cuisines.orEmpty(), mood, force)

    private fun refreshWithMessages(mood: List<String>?) {
        viewModelScope.launch {
            when (refresh(mood, force = true)) {
                RefreshResult.NOT_ENOUGH_HISTORY -> messages.send("Add a few ingredients to My kitchen first")
                RefreshResult.FAILED -> messages.send("Couldn't get new recipes. Try again in a bit.")
                RefreshResult.RATE_LIMITED -> messages.send("That's all the new recipe ideas for today. Check back tomorrow.")
                else -> Unit
            }
        }
    }

    fun openListFor(store: Store): String = repo.openListFor(store, uid, state.value.activeLists).id

    fun removeStore(store: Store) = repo.removeStore(store)

    fun deleteList(list: GroceryList) {
        viewModelScope.launch {
            runCatching { repo.deleteList(list.id) }.onFailure { messages.send("Couldn't delete that list. Try again.") }
        }
    }

    fun openListWithoutStore(): String = repo.openListFor(null, uid, state.value.activeLists).id

    fun openListForNewStore(name: String): String = openListFor(repo.addStore(name, state.value.stores))

    private companion object {
        const val PAST_LISTS_SHOWN = 10
        const val SETTLE_MS = 2_000L
    }
}
