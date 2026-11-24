package com.grocerylist.app.data

import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.grocerylist.app.AppGraph
import kotlinx.coroutines.tasks.await

/** A household account deletion affects. [others] are the members who could take it over. */
data class AffectedHousehold(val household: Household, val isOwner: Boolean, val others: List<UserProfile>) {
    /** The user is its only member, so it goes with them. */
    val solo get() = household.memberIds.size <= 1
    /** The user picks who takes over. (If no profile could be read, the next member does.) */
    val needsNewOwner get() = !solo && isOwner && others.isNotEmpty()
}

/**
 * Deletes the signed-in user's data and login. The caller must have just re-authenticated. For each household
 * they're in:
 *
 * - Only member: the whole household goes too, since nobody else could reach it.
 * - Shared household: the user leaves it (handing ownership to the chosen member first if they're the
 *   owner); its lists stay with the other members.
 */
object AccountDeletion {
    suspend fun affectedHouseholds(uid: String): List<AffectedHousehold> {
        val repo = AppGraph.households
        return repo.households(uid).map { household ->
            val isOwner = household.ownerId == uid
            // Only an owner has anyone to choose.
            val others = if (isOwner) repo.members(household.memberIds - uid) else emptyList()
            AffectedHousehold(household, isOwner, others)
        }
    }

    /** [newOwners] maps a household's ID to the member who takes it over. */
    suspend fun deleteAccount(uid: String, currentHouseholdId: String, newOwners: Map<String, String>) {
        val repo = AppGraph.households
        // The open household goes last: once it's gone, the session looks for another to open.
        for (household in repo.households(uid).sortedBy { it.id == currentHouseholdId }) {
            if (household.memberIds.size <= 1) {
                repo.deleteHousehold(household.id, uid)
            } else {
                if (household.ownerId == uid) {
                    repo.transferOwnership(household, newOwners[household.id] ?: household.memberIds.first { it != uid })
                }
                repo.removeMember(household.id, uid)
            }
        }
        Firebase.firestore.collection("users").document(uid).delete().await()
        AppGraph.auth.deleteUser()
    }
}

/**
 * What the delete-account confirmation says, given the names of the households that go with the account and
 * the ones that stay with their other members. iOS: accountDeletionText in Logic.swift.
 */
fun accountDeletionText(deleted: List<String>, left: List<String>): String {
    val goes = "${joinNames(deleted)}, including all of ${if (deleted.size > 1) "their" else "its"} lists, stores, and recipes."
    val stays = if (left.size > 1) "Their lists stay" else "Its lists stay"
    return when {
        deleted.isEmpty() && left.isEmpty() -> "This permanently deletes your account. This can't be undone."
        deleted.isEmpty() ->
            "This permanently deletes your account and removes you from ${joinNames(left)}. $stays with the other members."
        left.isEmpty() -> "This permanently deletes your account and $goes This can't be undone."
        else ->
            "This permanently deletes your account and $goes This can't be undone. " +
                "You'll also be removed from ${joinNames(left)}. $stays with the other members."
    }
}

private fun joinNames(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> names[0]
    2 -> "${names[0]} and ${names[1]}"
    else -> names.dropLast(1).joinToString(", ") + ", and " + names.last()
}
