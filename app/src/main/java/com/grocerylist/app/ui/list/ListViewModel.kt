package com.grocerylist.app.ui.list

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.GroceryList
import com.grocerylist.app.data.ItemStat
import com.grocerylist.app.data.ListItem
import com.grocerylist.app.data.suggestItems
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ListState(
    val list: GroceryList? = null,
    val items: List<ListItem> = emptyList(),
    val suggestions: List<ItemStat> = emptyList(),
    /** Names of other household members by user ID, for marking items they added. Empty if you're the only member. */
    val otherMembers: Map<String, String> = emptyMap(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ListViewModel(householdId: String, private val listId: String, private val uid: String) : ViewModel() {
    private val repo = AppGraph.groceries(householdId)

    val state: StateFlow<ListState> = combine(
        repo.observeList(listId),
        repo.observeItems(listId),
        repo.observeStats(),
        repo.observeOnHand(),
        AppGraph.households.observeHousehold(householdId)
            .flatMapLatest { AppGraph.households.observeMembers(it?.memberIds.orEmpty()) },
    ) { list, items, stats, kitchen, members ->
        ListState(
            otherMembers = if (members.size > 1) {
                members.filter { it.uid != uid }.associate { it.uid to it.displayName }
            } else {
                emptyMap()
            },
            list = list,
            items = items,
            suggestions = if (list?.isActive == true) {
                suggestItems(
                    stats,
                    list.storeId,
                    items.mapTo(HashSet()) { it.key },
                    System.currentTimeMillis(),
                    onHand = kitchen.map { it.name },
                )
            } else {
                emptyList()
            },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListState())

    fun add(name: String) {
        state.value.list?.let { repo.addItem(it, name, uid) }
    }

    fun toggle(item: ListItem) = repo.setChecked(listId, item, !item.checked)

    fun delete(item: ListItem) {
        state.value.list?.let { repo.deleteItem(it, item) }
    }

    fun rename(item: ListItem, name: String) {
        state.value.list?.let { repo.renameItem(it, item, name) }
    }

    fun clearChecked() =repo.clearChecked(listId, state.value.items)

    fun reopen() = repo.reopenList(listId)

    /** Runs app-wide: the screen closes as soon as this is called. */
    fun deleteList() {
        AppGraph.appScope.launch {
            runCatching { repo.deleteList(listId) }.onFailure { Log.w("ListViewModel", "Couldn't delete list", it) }
        }
    }

    fun closeOut() = repo.closeOut(listId)
}
