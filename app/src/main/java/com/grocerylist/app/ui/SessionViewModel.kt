package com.grocerylist.app.ui

import com.grocerylist.app.ui.onboarding.Onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.Household
import com.grocerylist.app.data.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class NeedsHousehold(val user: UserProfile) : Session
    data class Ready(val user: UserProfile, val householdId: String) : Session
    data class Failed(val message: String) : Session
}

@OptIn(ExperimentalCoroutinesApi::class)
class SessionViewModel : ViewModel() {
    val session: StateFlow<Session> = AppGraph.auth.currentUser
        .flatMapLatest { user ->
            if (user == null) {
                flowOf(Session.SignedOut)
            } else {
                AppGraph.households.observeProfileSaving(user.uid)
                    .onStart {
                        AppGraph.households.ensureProfile(user)
                        viewModelScope.launch { runCatching { AppGraph.households.syncHouseholdIds(user.uid) } }
                    }
                    .flatMapLatest { (profile, saving) ->
                        val householdId = profile?.householdId
                        when {
                            profile == null -> flowOf(Session.Loading)
                            // Signing in writes the name and email first, so until the server's copy is in,
                            // having no household only means it isn't known yet.
                            householdId == null && saving -> flowOf(Session.Loading)
                            householdId == null -> flow { openAnother(profile) }
                            else -> membership(profile, householdId, saving)
                        }
                    }
                    .catch { emit(Session.Failed(it.localizedMessage ?: "Couldn't load your account")) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Session.Loading)

    fun signOut() {
        Onboarding.pending.value = null
        AppGraph.auth.signOut()
    }

    /**
     * Ready while the user is still in the household they have open. Once they leave or are removed, security
     * rules stop them reading it, so another of their households opens instead.
     * [saving]: the profile's household link isn't on the server yet (they're joining right now).
     */
    private fun membership(profile: UserProfile, householdId: String, saving: Boolean): Flow<Session> =
        // Waits for the server: Home's listeners start on Ready, and any the server refuses stop for good.
        AppGraph.households.observeConfirmedHousehold(householdId)
            .map { household -> household?.takeIf { profile.uid in it.memberIds } }
            .catch { e ->
                // Listener errors arrive wrapped in a cancellation. Only "permission denied" means you're
                // out; any other error surfaces as a failure instead.
                val denied = (e as? FirebaseFirestoreException ?: e.cause as? FirebaseFirestoreException)
                    ?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                when {
                    // Asked before the join was saved. The profile updates once it is, which starts this again.
                    denied && saving -> {}
                    denied -> emit(null)
                    else -> throw e
                }
            }
            .transform { household ->
                if (household != null) emit(Session.Ready(profile, householdId)) else openAnother(profile)
            }

    /** Tries until the server answers, so a bad connection doesn't look like having no household. */
    private suspend fun households(uid: String): List<Household> {
        while (true) {
            try {
                return AppGraph.households.households(uid)
            } catch (e: FirebaseFirestoreException) {
                if (e.code != FirebaseFirestoreException.Code.UNAVAILABLE) throw e
                delay(3_000)
            }
        }
    }

    /** Opens another household the user is in, or household setup if there isn't one (clearing the stale link). */
    private suspend fun FlowCollector<Session>.openAnother(profile: UserProfile) {
        val next = households(profile.uid).firstOrNull { it.id != profile.householdId }
        if (next != null) {
            // The profile updates, which starts the session again on that household.
            AppGraph.households.switchHousehold(profile.uid, next.id)
        } else {
            if (profile.householdId != null) AppGraph.households.clearHousehold(profile.uid)
            emit(Session.NeedsHousehold(profile.copy(householdId = null)))
        }
    }
}
