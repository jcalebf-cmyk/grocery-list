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
    private val onHand = household.collection("onHand")

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

    /** Removes the store from the picker. Its past and open lists keep the store's name. */
    fun removeStore(store: Store) {
        stores.document(store.id).delete()
    }

    fun observeOnHand(): Flow<List<OnHandItem>> =
        onHand.orderBy("createdAt").snapshots().map { it.toObjects(OnHandItem::class.java) }

    /** Not counted in item stats: on-hand items feed recipe ideas, not shopping suggestions. */
    fun addOnHand(name: String, uid: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        // Keyed by name so adding "Ground beef" twice doesn't create duplicates.
        onHand.document(itemKey(trimmed)).set(
            mapOf("name" to trimmed, "key" to itemKey(trimmed), "addedBy" to uid, "createdAt" to System.currentTimeMillis()),
        )
    }

    fun removeOnHand(item: OnHandItem) {
        onHand.document(item.id).delete()
    }

    /** Returns the new store. Reuses an existing one with the same name instead of adding a duplicate. */
    fun addStore(name: String, existing: List<Store>): Store {
        val trimmed = name.trim()
        existing.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }?.let { return it }
        val doc = stores.document()
        val createdAt = System.currentTimeMillis()
        doc.set(mapOf("name" to trimmed, "createdAt" to createdAt))
        return Store(id = doc.id, name = trimmed, createdAt = createdAt)
    }

    /** Returns the new list's ID right away; the write syncs in the background. A null store means "no store". */
    fun createList(store: Store?, uid: String): String {
        val doc = lists.document()
        doc.set(
            mapOf(
                "storeId" to store?.id.orEmpty(),
                "storeName" to store?.name.orEmpty(),
                "status" to GroceryList.STATUS_ACTIVE,
                "createdBy" to uid,
                "createdAt" to System.currentTimeMillis(),
            ),
        )
        return doc.id
    }

    /**
     * Reuses the store's open list if there is one, so a household doesn't end up with two Costco lists.
     * A null store opens (or starts) the household's list that isn't tied to a store.
     */
    fun openListFor(store: Store?, uid: String, activeLists: List<GroceryList>): GroceryList {
        val storeId = store?.id.orEmpty()
        return activeLists.firstOrNull { it.storeId == storeId }
            ?: GroceryList(id = createList(store, uid), storeId = storeId, storeName = store?.name.orEmpty())
    }

    /** Moves a closed-out list back to the open lists, checked items and all. */
    fun reopenList(listId: String) {
        lists.document(listId).update(
            mapOf("status" to GroceryList.STATUS_ACTIVE, "completedAt" to FieldValue.delete()),
        )
    }

    /**
     * Closes out a trip. Items in [carryOver] (usually what wasn't checked off) move to a fresh list
     * for the same store, whose ID is returned. They were counted toward suggestions when first added,
     * so they aren't counted again.
     */
    fun completeList(list: GroceryList, uid: String, carryOver: List<ListItem> = emptyList()): String? {
        val now = System.currentTimeMillis()
        val next = if (carryOver.isNotEmpty()) lists.document() else null
        Firebase.firestore.batch().apply {
            update(lists.document(list.id), mapOf("status" to GroceryList.STATUS_DONE, "completedAt" to now))
            if (next != null) {
                set(
                    next,
                    mapOf(
                        "storeId" to list.storeId,
                        "storeName" to list.storeName,
                        "status" to GroceryList.STATUS_ACTIVE,
                        "createdBy" to uid,
                        "createdAt" to now,
                    ),
                )
                carryOver.forEach { item ->
                    set(
                        next.collection("items").document(),
                        mapOf("name" to item.name, "key" to item.key, "checked" to false, "addedBy" to item.addedBy, "createdAt" to item.createdAt),
                    )
                }
            }
        }.commit()
        return next?.id
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
        // Lists without a store still count toward overall habits, just not any store's.
        if (storeId.isNotEmpty()) put("storeCounts", mapOf(storeId to FieldValue.increment(by)))
    }
}
