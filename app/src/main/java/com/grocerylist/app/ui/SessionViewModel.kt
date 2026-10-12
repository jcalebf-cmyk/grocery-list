package com.grocerylist.app.ui

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
                AppGraph.households.observeProfile(user.uid)
                    .onStart { AppGraph.households.ensureProfile(user) }
                    .flatMapLatest { profile ->
                        val householdId = profile?.householdId
                        when {
                            profile == null -> flowOf(Session.Loading)
                            householdId == null -> flowOf(Session.NeedsHousehold(profile))
                            else -> membership(profile, householdId)
                        }
                    }
                    .catch { emit(Session.Failed(it.localizedMessage ?: "Couldn't load your account")) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Session.Loading)

    fun signOut() = AppGraph.auth.signOut()

    /**
     * Ready while the user is still in the household. Once they leave or are removed, security rules
     * stop them reading it, so they're sent to household setup and the stale link is cleared.
     */
    private fun membership(profile: UserProfile, householdId: String): Flow<Session> =
        AppGraph.households.observeHousehold(householdId)
            .map { household -> household?.takeIf { profile.uid in it.memberIds } }
            .catch { e ->
                // Only "permission denied" means you're out; any other error surfaces as a failure instead.
                if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) emit(null)
                else throw e
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
