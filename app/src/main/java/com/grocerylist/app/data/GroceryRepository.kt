package com.grocerylist.app.data

import com.google.firebase.Firebase
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Writes don't await the server: Firestore applies them to the local cache immediately and
 * syncs when it can, which keeps the app responsive in a store with bad reception.
 */
class GroceryRepository(householdId: String) {
    private val household = Firebase.firestore.collection("households").document(householdId)
    private val stores = household.collection("stores")
    private val lists = household.collection("lists")
    private val stats = household.collection("itemStats")

    fun observeStores(): Flow<List<Store>> =
        stores.orderBy("createdAt").snapshots().map { it.toObjects(Store::class.java) }

    fun observeLists(): Flow<List<GroceryList>> =
        lists.orderBy("createdAt", Query.Direction.DESCENDING).snapshots()
            .map { it.toObjects(GroceryList::class.java) }

    fun observeList(listId: String): Flow<GroceryList?> =
        lists.document(listId).snapshots().map { it.toObject(GroceryList::class.java) }

    fun observeItems(listId: String): Flow<List<ListItem>> =
        lists.document(listId).collection("items").snapshots().map { snap ->
            snap.toObjects(ListItem::class.java)
                .sortedWith(compareBy<ListItem> { it.checked }.thenByDescending { it.createdAt })
        }

    fun observeStats(): Flow<List<ItemStat>> =
        stats.snapshots().map { it.toObjects(ItemStat::class.java) }

    fun addStore(name: String) {
        stores.add(mapOf("name" to name.trim(), "createdAt" to System.currentTimeMillis()))
    }

    /** Returns the new list's ID right away; the write syncs in the background. */
    fun createList(store: Store, uid: String): String {
        val doc = lists.document()
        doc.set(
            mapOf(
                "storeId" to store.id,
                "storeName" to store.name,
                "status" to GroceryList.STATUS_ACTIVE,
                "createdBy" to uid,
                "createdAt" to System.currentTimeMillis(),
            ),
        )
        return doc.id
    }

    /** Reuses the store's open list if there is one, so a household doesn't end up with two Costco lists. */
    fun openListFor(store: Store, uid: String, activeLists: List<GroceryList>): GroceryList =
        activeLists.firstOrNull { it.storeId == store.id }
            ?: GroceryList(id = createList(store, uid), storeId = store.id, storeName = store.name)

    fun completeList(listId: String) {
        lists.document(listId).update(
            mapOf("status" to GroceryList.STATUS_DONE, "completedAt" to System.currentTimeMillis()),
        )
    }

    fun addItem(list: GroceryList, name: String, uid: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val key = itemKey(trimmed)
        val now = System.currentTimeMillis()
        Firebase.firestore.batch().apply {
            set(
                lists.document(list.id).collection("items").document(),
                mapOf("name" to trimmed, "key" to key, "checked" to false, "addedBy" to uid, "createdAt" to now),
            )
            set(stats.document(key), statDelta(trimmed, list.storeId, +1, now), SetOptions.merge())
        }.commit()
    }

    fun setChecked(listId: String, item: ListItem, checked: Boolean) {
        lists.document(listId).collection("items").document(item.id).update("checked", checked)
    }

    /** Deleting an unchecked item usually means it was added by mistake, so it stops counting toward suggestions. */
    fun deleteItem(list: GroceryList, item: ListItem) {
        Firebase.firestore.batch().apply {
            delete(lists.document(list.id).collection("items").document(item.id))
            if (!item.checked) set(stats.document(item.key), statDelta(null, list.storeId, -1, null), SetOptions.merge())
        }.commit()
    }

    fun clearChecked(listId: String, items: List<ListItem>) {
        Firebase.firestore.batch().apply {
            items.filter { it.checked }.forEach { delete(lists.document(listId).collection("items").document(it.id)) }
        }.commit()
    }

    private fun statDelta(displayName: String?, storeId: String, by: Long, now: Long?) = buildMap {
        displayName?.let { put("displayName", it) }
        now?.let { put("lastAdded", it) }
        put("count", FieldValue.increment(by))
        put("storeCounts", mapOf(storeId to FieldValue.increment(by)))
    }
}
