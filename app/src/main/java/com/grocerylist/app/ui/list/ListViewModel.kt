package com.grocerylist.app.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.GroceryList
import com.grocerylist.app.data.ItemStat
import com.grocerylist.app.data.ListItem
import com.grocerylist.app.data.suggestItems
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ListState(
    val list: GroceryList? = null,
    val items: List<ListItem> = emptyList(),
    val suggestions: List<ItemStat> = emptyList(),
)

class ListViewModel(householdId: String, private val listId: String, private val uid: String) : ViewModel() {
    private val repo = AppGraph.groceries(householdId)

    val state: StateFlow<ListState> = combine(
        repo.observeList(listId),
        repo.observeItems(listId),
        repo.observeStats(),
    ) { list, items, stats ->
        ListState(
            list = list,
            items = items,
            suggestions = if (list?.isActive == true) {
                suggestItems(stats, list.storeId, items.mapTo(HashSet()) { it.key }, System.currentTimeMillis())
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

    fun clearChecked() = repo.clearChecked(listId, state.value.items)

    fun reopen() = repo.reopenList(listId)

    /** Closes out the trip; with [carryOver], unchecked items move to a new list for the same store. */
    fun finishTrip(carryOver: Boolean) {
        val list = state.value.list ?: return
        repo.completeList(list, uid, if (carryOver) state.value.items.filterNot { it.checked } else emptyList())
    }
}
