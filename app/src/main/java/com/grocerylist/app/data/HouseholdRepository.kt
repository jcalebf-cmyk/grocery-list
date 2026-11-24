package com.grocerylist.app.data

import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
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

    /** The user's profile, and whether it has changes from this phone the server hasn't saved yet. */
    fun observeProfileSaving(uid: String): Flow<Pair<UserProfile?, Boolean>> =
        users.document(uid).snapshots(MetadataChanges.INCLUDE)
            .map { it.toObject(UserProfile::class.java) to it.metadata.hasPendingWrites() }
            .distinctUntilChanged()

    fun observeHousehold(id: String): Flow<Household?> =
        households.document(id).snapshots().map { it.toObject(Household::class.java) }

    /**
     * Only the server's copy. A household created on this phone is in its cache straight away, but
     * security rules deny reading its lists and kitchen until the server has saved it.
     */
    fun observeConfirmedHousehold(id: String): Flow<Household?> =
        households.document(id).snapshots(MetadataChanges.INCLUDE)
            .filter { !it.metadata.hasPendingWrites() }
            .map { it.toObject(Household::class.java) }
            .distinctUntilChanged()

    /** Every household [uid] is in, by name. */
    fun observeHouseholds(uid: String): Flow<List<Household>> =
        mine(uid).snapshots().map { it.toObjects(Household::class.java).byName() }

    /** Every household [uid] is in, by name, read once. From the server: this phone's copy may be missing some. */
    suspend fun households(uid: String): List<Household> =
        mine(uid).get(Source.SERVER).await().toObjects(Household::class.java).byName()

    // Asked of the households themselves, so it's right however someone was added or removed.
    private fun mine(uid: String) = households.whereArrayContains("memberIds", uid)

    private fun List<Household>.byName() = sortedBy { it.name.lowercase() }

    // Fetched one by one rather than queried, so security rules can check each profile directly. Someone
    // whose profile can't be read is left out rather than losing everyone.
    fun observeMembers(memberIds: List<String>): Flow<List<UserProfile>> =
        if (memberIds.isEmpty()) {
            flowOf(emptyList())
        } else {
            combine(memberIds.map { observeProfile(it).catch { emit(null) } }) { profiles -> profiles.filterNotNull().sortedBy { it.displayName } }
        }

    suspend fun members(memberIds: List<String>): List<UserProfile> =
        memberIds.mapNotNull { id ->
            runCatching { users.document(id).get().await().toObject(UserProfile::class.java) }.getOrNull()
        }.sortedBy { it.displayName }

    /**
     * The household [uid] has open, read once (for voice actions). Null without one, or after being
     * removed from it (security rules then refuse reading it).
     */
    suspend fun currentHouseholdId(uid: String): String? {
        val householdId = users.document(uid).get().await().toObject(UserProfile::class.java)?.householdId ?: return null
        val household = try {
            households.document(householdId).get().await().toObject(Household::class.java)
        } catch (e: FirebaseFirestoreException) {
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) return null else throw e
        }
        return householdId.takeIf { household != null && uid in household.memberIds }
    }

    /** Creates or refreshes the user's profile document after sign-in. Leaves householdId alone. */
    fun ensureProfile(user: FirebaseUser) {
        val profile = mapOf(
            "displayName" to (user.displayName ?: user.email?.substringBefore('@') ?: "Me"),
            "email" to (user.email ?: ""),
        )
        users.document(user.uid).set(profile, SetOptions.merge())
    }

    /** Brings the profile's list of households up to date with the households themselves. */
    suspend fun syncHouseholdIds(uid: String) {
        val actual = mine(uid).get(Source.SERVER).await().documents.map { it.id }
        val listed = users.document(uid).get(Source.SERVER).await().toObject(UserProfile::class.java)?.householdIds ?: return
        // Adds and removes rather than replacing, so a household joined meanwhile isn't dropped.
        val missing = actual - listed.toSet()
        val stale = listed - actual.toSet()
        if (missing.isNotEmpty()) users.document(uid).update("householdIds", FieldValue.arrayUnion(*missing.toTypedArray())).await()
        if (stale.isNotEmpty()) users.document(uid).update("householdIds", FieldValue.arrayRemove(*stale.toTypedArray())).await()
    }

    /** Opens another household the user is in, on all their devices. Works offline. */
    fun switchHousehold(uid: String, householdId: String) {
        users.document(uid).update("householdId", householdId)
    }

    /** The profile change that goes with joining or starting a household: it opens, alongside any others. */
    private fun opening(user: UserProfile, householdId: String) = mapOf(
        "householdId" to householdId,
        "householdIds" to FieldValue.arrayUnion(*listOfNotNull(user.householdId, householdId).toTypedArray()),
    )

    suspend fun createHousehold(user: UserProfile, name: String) {
        val code = unusedInviteCode()
        val household = households.document()
        // No starter stores: the store picker suggests ones near the user instead.
        db.batch().apply {
            set(household, mapOf("name" to name.trim(), "inviteCode" to code, "memberIds" to listOf(user.uid)))
            set(inviteCodes.document(code), mapOf("householdId" to household.id))
            set(users.document(user.uid), opening(user, household.id), SetOptions.merge())
        }.commit().await()
    }

    /** Returns false if no household uses this code. */
    suspend fun joinHousehold(user: UserProfile, code: String): Boolean {
        val normalized = code.trim().uppercase()
        val householdId = inviteCodes.document(normalized).get().await().getString("householdId")
            ?: return false
        // Already in it: the code just opens it.
        if (households(user.uid).any { it.id == householdId }) {
            switchHousehold(user.uid, householdId)
            return true
        }
        db.batch().apply {
            // Security rules only let a non-member add themselves when they present the matching code.
            update(
                households.document(householdId),
                mapOf("memberIds" to FieldValue.arrayUnion(user.uid), "joinCode" to normalized),
            )
            set(users.document(user.uid), opening(user, householdId), SetOptions.merge())
        }.commit().await()
        return true
    }

    suspend fun renameHousehold(householdId: String, name: String) {
        households.document(householdId).update("name", name.trim()).await()
    }

    suspend fun updateDisplayName(uid: String, name: String) {
        users.document(uid).update("displayName", name.trim()).await()
    }

    /**
     * Hands ownership to another member by moving them to the front of the member list.
     * Owner only (enforced by security rules).
     */
    suspend fun transferOwnership(household: Household, newOwnerUid: String) {
        val reordered = listOf(newOwnerUid) + household.memberIds.filter { it != newOwnerUid }
        households.document(household.id).update("memberIds", reordered).await()
    }

    /**
     * Takes you out of the household you have open, and opens [next]; without another, you'll be asked to
     * create or join one. Not allowed for the owner.
     *
     * [next] opens, and is saved, before leaving: by then nothing on screen is still following this household.
     */
    suspend fun leaveHousehold(householdId: String, uid: String, next: String?) {
        if (next != null) users.document(uid).update("householdId", next).await()
        db.batch().apply {
            update(households.document(householdId), "memberIds", FieldValue.arrayRemove(uid))
            update(users.document(uid), "householdIds", FieldValue.arrayRemove(householdId))
            if (next == null) update(users.document(uid), "householdId", FieldValue.delete())
        }.commit().await()
    }

    /**
     * Owner only (enforced by security rules). The removed person's own profile still points here
     * until their app notices they're no longer a member and opens another of theirs (or household setup).
     */
    suspend fun removeMember(householdId: String, memberUid: String) {
        households.document(householdId).update("memberIds", FieldValue.arrayRemove(memberUid)).await()
    }

    /** Clears a stale household link, e.g. after being removed by the owner. */
    fun clearHousehold(uid: String) {
        users.document(uid).update("householdId", FieldValue.delete())
    }

    /**
     * Deletes a household [uid] is the only member of, with everything in it: lists and their items, stores,
     * stats, the old kitchen, saved recipes (plus the old suggestions in `recipes`), and the invite code.
     * Opens [next] first, if given.
     */
    suspend fun deleteHousehold(householdId: String, uid: String, next: String? = null) {
        val ref = households.document(householdId)
        // Asks the server, in case someone has just joined.
        val household = ref.get(Source.SERVER).await().toObject(Household::class.java) ?: return
        check(household.memberIds == listOf(uid)) { "Other people are in this household" }
        if (next != null) switchHousehold(uid, next)
        // Subcollections first: the rules check membership on the household doc, so it must go last. Read from
        // the server, since anything missed (say, with a bad connection) could never be reached again.
        val docs = mutableListOf<DocumentReference>()
        for (name in listOf("stores", "itemStats", "onHand", "savedRecipes", "recipes")) {
            ref.collection(name).get(Source.SERVER).await().documents.mapTo(docs) { it.reference }
        }
        for (list in ref.collection("lists").get(Source.SERVER).await().documents) {
            list.reference.collection("items").get(Source.SERVER).await().documents.mapTo(docs) { it.reference }
            docs += list.reference
        }
        // Batches are capped at 500 writes.
        docs.chunked(450).forEach { chunk ->
            db.batch().apply { chunk.forEach(::delete) }.commit().await()
        }
        if (household.inviteCode.isNotEmpty()) inviteCodes.document(household.inviteCode).delete().await()
        ref.delete().await()
        if (next != null) users.document(uid).update("householdIds", FieldValue.arrayRemove(householdId))
    }

    private suspend fun unusedInviteCode(): String {
        while (true) {
            val code = (1..6).map { INVITE_ALPHABET.random() }.joinToString("")
            if (!inviteCodes.document(code).get().await().exists()) return code
        }
    }

    companion object {
        /** As many as the security rules check when someone reads a profile. */
        const val MAX_HOUSEHOLDS = 8

        // No 0/O or 1/I/L, so codes are easy to read aloud.
        private const val INVITE_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    }
}
