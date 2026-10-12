package com.grocerylist.app.data

import com.google.firebase.Firebase
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.firestore
import com.grocerylist.app.AppGraph
import kotlinx.coroutines.tasks.await

/**
 * Deletes the signed-in user's data and login. The caller must have just re-authenticated.
 *
 * - Only member: the whole household goes too (lists and their items, stores, stats, kitchen,
 *   recipes, invite code), since nobody else could reach it.
 * - Shared household: the user leaves it (handing ownership to [newOwner] first if they're the
 *   owner); its lists stay with the other members.
 */
object AccountDeletion {
    private val db = Firebase.firestore

    suspend fun deleteAccount(uid: String, householdId: String?, newOwner: UserProfile?) {
        val households = AppGraph.households
        if (householdId != null) {
            val household = db.collection("households").document(householdId).get().await()
                .toObject(Household::class.java)
            when {
                household == null -> Unit
                household.memberIds.size <= 1 -> deleteHousehold(household)
                else -> {
                    if (household.ownerId == uid) {
                        households.transferOwnership(household, newOwner?.uid ?: error("Choose a new owner first"))
                    }
                    households.leaveHousehold(householdId, uid)
                }
            }
        }
        db.collection("users").document(uid).delete().await()
        AppGraph.auth.deleteUser()
    }

    /** Subcollections first: the rules check membership on the household doc, so it must go last. */
    private suspend fun deleteHousehold(household: Household) {
        val ref = db.collection("households").document(household.id)
        val docs = mutableListOf<DocumentReference>()
        for (name in listOf("stores", "itemStats", "onHand", "recipes")) {
            ref.collection(name).get().await().documents.mapTo(docs) { it.reference }
        }
        for (list in ref.collection("lists").get().await().documents) {
            list.reference.collection("items").get().await().documents.mapTo(docs) { it.reference }
            docs += list.reference
        }
        // Batches are capped at 500 writes.
        docs.chunked(450).forEach { chunk ->
            db.batch().apply { chunk.forEach(::delete) }.commit().await()
        }
        if (household.inviteCode.isNotEmpty()) db.collection("inviteCodes").document(household.inviteCode).delete().await()
        ref.delete().await()
    }
}
