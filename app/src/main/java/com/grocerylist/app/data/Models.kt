package com.grocerylist.app.data

import com.google.firebase.firestore.DocumentId

// Firestore needs no-arg constructors, so every field has a default.
// Timestamps are device millis rather than server timestamps so they sort correctly while offline.

data class UserProfile(
    @DocumentId val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    /** The household they have open. Someone can be in several and switch between them. */
    val householdId: String? = null,
    /**
     * Every household they're in, so security rules let people in any of them read this profile. The households'
     * own member lists are what counts; this copy is brought up to date on launch.
     */
    val householdIds: List<String> = emptyList(),
)

data class Household(
    @DocumentId val id: String = "",
    val name: String = "",
    val inviteCode: String = "",
    val memberIds: List<String> = emptyList(),
) {
    /** The household's creator, i.e. its earliest member. Only the owner can remove people. */
    val ownerId: String? get() = memberIds.firstOrNull()
}

data class Store(
    @DocumentId val id: String = "",
    val name: String = "",
    val createdAt: Long = 0,
)

data class GroceryList(
    @DocumentId val id: String = "",
    val storeId: String = "",
    val storeName: String = "",
    val status: String = STATUS_ACTIVE,
    val createdBy: String = "",
    val createdAt: Long = 0,
    val completedAt: Long? = null,
) {
    val isActive get() = status == STATUS_ACTIVE

    /** Lists started with "Skip" have no store. */
    val displayName get() = storeName.ifBlank { "Shopping list" }

    companion object {
        const val STATUS_ACTIVE = "active"
        const val STATUS_DONE = "done"
    }
}

data class ListItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val key: String = "",
    val checked: Boolean = false,
    val addedBy: String = "",
    val createdAt: Long = 0,
    /** Extra context for whoever's shopping ("the low-sodium one"). Most items have none. */
    val note: String? = null,
)

const val MAX_NOTE_LENGTH = 120

/** A note as it's saved: one line, no longer than [MAX_NOTE_LENGTH]. Null if there's nothing in it. Same as Logic.swift. */
fun cleanNote(text: String): String? =
    text.trim().replace(Regex("\\s+"), " ").take(MAX_NOTE_LENGTH).trim().ifEmpty { null }

/** How often a household adds an item; drives suggestions. Document ID is the item key. */
data class ItemStat(
    @DocumentId val key: String = "",
    val displayName: String = "",
    val count: Long = 0,
    val lastAdded: Long = 0,
    val storeCounts: Map<String, Long> = emptyMap(),
    /** When someone chose "Don't suggest"; adding the item again brings it back. 0 if never. */
    val dismissedAt: Long = 0,
)

/** "  Whole  Milk " and "whole milk" are the same item. Slashes aren't allowed in document IDs. */
fun itemKey(name: String): String =
    name.trim().lowercase().replace(Regex("\\s+"), " ").replace('/', '-')

/**
 * The stores to offer under "Your stores": only ones the household has made a list for,
 * most recently used first. Stores that were saved but never shopped stay hidden.
 */
fun storesInUse(stores: List<Store>, lists: List<GroceryList>): List<Store> {
    val lastUsed = HashMap<String, Long>()
    for (list in lists) {
        if (list.storeId.isNotEmpty()) lastUsed.merge(list.storeId, list.createdAt, ::maxOf)
    }
    return stores.filter { it.id in lastUsed }.sortedByDescending { lastUsed.getValue(it.id) }
}
