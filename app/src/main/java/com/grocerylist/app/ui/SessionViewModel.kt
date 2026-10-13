package com.grocerylist.app.ui

import com.grocerylist.app.ui.onboarding.Onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestoreException
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

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
                    .onStart { AppGraph.households.ensureProfile(user) }
                    .flatMapLatest { (profile, saving) ->
                        val householdId = profile?.householdId
                        when {
                            profile == null -> flowOf(Session.Loading)
                            householdId == null -> flowOf(Session.NeedsHousehold(profile))
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
     * Ready while the user is still in the household. Once they leave or are removed, security rules
     * stop them reading it, so they're sent to household setup and the stale link is cleared.
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
            .map { household ->
                if (household != null) {
                    Session.Ready(profile, householdId)
                } else {
                    AppGraph.households.clearHousehold(profile.uid)
                    Session.NeedsHousehold(profile.copy(householdId = null))
                }
            }
}
