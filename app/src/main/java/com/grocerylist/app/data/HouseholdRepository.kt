package com.grocerylist.app.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class HouseholdRepository {
    private val db = Firebase.firestore
    private val users = db.collection("users")
    private val households = db.collection("households")
    private val inviteCodes = db.collection("inviteCodes")

    fun observeProfile(uid: String): Flow<UserProfile?> =
        users.document(uid).snapshots().map { it.toObject(UserProfile::class.java) }

    fun observeHousehold(id: String): Flow<Household?> =
        households.document(id).snapshots().map { it.toObject(Household::class.java) }

    // Fetched one by one rather than queried, so security rules can check each profile directly.
    fun observeMembers(memberIds: List<String>): Flow<List<UserProfile>> =
        if (memberIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(memberIds.map(::observeProfile)) { profiles -> profiles.filterNotNull().sortedBy { it.displayName } }
        }

    /** Creates or refreshes the user's profile document after sign-in. Leaves householdId alone. */
    fun ensureProfile(user: FirebaseUser) {
        val profile = mapOf(
            "displayName" to (user.displayName ?: user.email?.substringBefore('@') ?: "Me"),
            "email" to (user.email ?: ""),
        )
        users.document(user.uid).set(profile, SetOptions.merge())
    }

    suspend fun createHousehold(uid: String, name: String) {
        val code = unusedInviteCode()
        val household = households.document()
        val now = System.currentTimeMillis()
        db.batch().apply {
            set(household, mapOf("name" to name.trim(), "inviteCode" to code, "memberIds" to listOf(uid)))
            set(inviteCodes.document(code), mapOf("householdId" to household.id))
            DEFAULT_STORES.forEachIndexed { i, store ->
                set(household.collection("stores").document(), mapOf("name" to store, "createdAt" to now + i))
            }
            set(users.document(uid), mapOf("householdId" to household.id), SetOptions.merge())
        }.commit().await()
    }

    /** Returns false if no household uses this code. */
    suspend fun joinHousehold(uid: String, code: String): Boolean {
        val normalized = code.trim().uppercase()
        val householdId = inviteCodes.document(normalized).get().await().getString("householdId")
            ?: return false
        db.batch().apply {
            // Security rules only let a non-member add themselves when they present the matching code.
            update(
                households.document(householdId),
                mapOf("memberIds" to FieldValue.arrayUnion(uid), "joinCode" to normalized),
            )
            set(users.document(uid), mapOf("householdId" to householdId), SetOptions.merge())
        }.commit().await()
        return true
    }

    private suspend fun unusedInviteCode(): String {
        while (true) {
            val code = (1..6).map { INVITE_ALPHABET.random() }.joinToString("")
            if (!inviteCodes.document(code).get().await().exists()) return code
        }
    }

    private companion object {
        // No 0/O or 1/I/L, so codes are easy to read aloud.
        const val INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    }
}
