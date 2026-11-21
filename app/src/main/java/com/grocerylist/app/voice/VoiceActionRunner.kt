package com.grocerylist.app.voice

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.firestore
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.GroceryList
import com.grocerylist.app.data.GroceryRepository
import com.grocerylist.app.data.ListPick
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.Store
import com.grocerylist.app.data.VoiceText
import com.grocerylist.app.data.findItem
import com.grocerylist.app.data.findRecipes
import com.grocerylist.app.data.findStore
import com.grocerylist.app.data.itemKey
import com.grocerylist.app.data.meansPlainList
import com.grocerylist.app.data.openListFor
import com.grocerylist.app.data.pickList
import com.grocerylist.app.data.spokenItems
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Why a voice action couldn't run; [message] is said to the user. */
sealed class VoiceProblem(message: String) : Exception(message) {
    class SignedOut : VoiceProblem(VoiceText.SIGNED_OUT)
    class NoHousehold : VoiceProblem(VoiceText.NO_HOUSEHOLD)
    /** Something is missing or ambiguous; the assistant should ask and try again. */
    class NeedsInput(message: String) : VoiceProblem(message)
    class NotFound(message: String) : VoiceProblem(message)
}

/** A recipe a voice action asked to show, waiting for the signed-in screens to open it. */
object OpenRecipeRequest {
    val pending = MutableStateFlow<String?>(null)
}

/**
 * The voice actions themselves, for Gemini's AppFunctions. They run without a screen, so they read the
 * signed-in user and household directly. Same behavior and wording as the iPhone's App Intents
 * (ios/Groceries/Intents): change both together.
 */
class VoiceActionRunner {
    private class Signed(val uid: String, val householdId: String) {
        val groceries: GroceryRepository get() = AppGraph.groceries(householdId)
    }

    private suspend fun signedIn(): Signed {
        val uid = Firebase.auth.currentUser?.uid ?: throw VoiceProblem.SignedOut()
        val householdId = AppGraph.households.currentHouseholdId(uid) ?: throw VoiceProblem.NoHousehold()
        return Signed(uid, householdId)
    }

    /** Writes apply on this phone at once; this gives them a moment to reach the server before the app goes idle. */
    private suspend fun sync() {
        withTimeoutOrNull(5_000) { runCatching { Firebase.firestore.waitForPendingWrites().await() } }
    }

    suspend fun addItems(items: String, store: String?): String {
        val s = signedIn()
        val names = spokenItems(items)
        if (names.isEmpty()) throw VoiceProblem.NeedsInput(VoiceText.NOTHING_TO_ADD)
        val active = s.groceries.activeLists()
        val list = if (store.isNullOrBlank()) {
            when (val pick = pickList(active)) {
                is ListPick.One -> pick.list
                ListPick.NoneOpen -> s.groceries.openListFor(null, s.uid, active)
                is ListPick.Several -> throw VoiceProblem.NeedsInput(VoiceText.whichList(pick.lists))
            }
        } else {
            s.groceries.openListFor(storeToUse(s, store, active), s.uid, active)
        }
        val onList = s.groceries.items(list.id).filter { !it.checked }.map { itemKey(it.name) }.toSet()
        val (alreadyThere, toAdd) = names.partition { itemKey(it) in onList }
        toAdd.forEach { s.groceries.addItem(list, it, s.uid) }
        sync()
        return VoiceText.added(toAdd, alreadyThere, list.storeName)
    }

    suspend fun readList(store: String?): String {
        val s = signedIn()
        val list = existingList(s, store)
        return VoiceText.readOut(s.groceries.items(list.id), list.storeName)
    }

    suspend fun startList(store: String): String {
        val s = signedIn()
        if (store.isBlank()) throw VoiceProblem.NeedsInput("Which store is the list for?")
        val active = s.groceries.activeLists()
        val target = storeToUse(s, store, active)
        val alreadyGoing = openListFor(active, target?.id.orEmpty()) != null
        val list = s.groceries.openListFor(target, s.uid, active)
        sync()
        return VoiceText.started(list.storeName, alreadyGoing)
    }

    suspend fun checkOff(item: String, store: String?): String {
        val s = signedIn()
        if (item.isBlank()) throw VoiceProblem.NeedsInput("What should I check off?")
        val list = existingList(s, store)
        val match = findItem(s.groceries.items(list.id), item)
            ?: throw VoiceProblem.NotFound(VoiceText.notOnList(item, list.storeName))
        if (match.checked) return VoiceText.alreadyChecked(match.name)
        s.groceries.setChecked(list.id, match, true)
        sync()
        return VoiceText.checkedOff(match.name, list.storeName)
    }

    suspend fun findRecipe(name: String): Recipe {
        val s = signedIn()
        if (name.isBlank()) throw VoiceProblem.NeedsInput("Which recipe?")
        return findRecipes(AppGraph.recipes(s.householdId).all(), name).firstOrNull()
            ?: throw VoiceProblem.NotFound(VoiceText.noRecipe(name))
    }

    /** The store a list is for; null for the list that isn't tied to one. Adds a store that isn't saved yet. */
    private suspend fun storeToUse(s: Signed, name: String, active: List<GroceryList>): Store? {
        if (meansPlainList(name)) return null
        val saved = s.groceries.stores()
        return findStore(saved + storesOf(active), name) ?: s.groceries.addStore(name.trim(), saved)
    }

    /** An open list someone named, or the only one open. Never creates one. */
    private suspend fun existingList(s: Signed, store: String?): GroceryList {
        val active = s.groceries.activeLists()
        if (store.isNullOrBlank()) {
            return when (val pick = pickList(active)) {
                is ListPick.One -> pick.list
                ListPick.NoneOpen -> throw VoiceProblem.NotFound(VoiceText.NO_OPEN_LISTS)
                is ListPick.Several -> throw VoiceProblem.NeedsInput(VoiceText.whichList(pick.lists))
            }
        }
        if (meansPlainList(store)) return openListFor(active, "") ?: throw VoiceProblem.NotFound(VoiceText.noOpenList(""))
        // A list can outlive its store being removed from the picker, so open lists are matched too.
        val match = findStore(storesOf(active) + s.groceries.stores(), store)
        return match?.let { openListFor(active, it.id) }
            ?: throw VoiceProblem.NotFound(VoiceText.noOpenList(match?.name ?: store.trim()))
    }

    private fun storesOf(lists: List<GroceryList>) =
        lists.filter { it.storeId.isNotEmpty() }.map { Store(id = it.storeId, name = it.storeName) }
}
