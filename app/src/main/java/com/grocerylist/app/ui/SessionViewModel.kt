package com.grocerylist.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
                    .map { profile ->
                        val householdId = profile?.householdId
                        when {
                            profile == null -> Session.Loading
                            householdId == null -> Session.NeedsHousehold(profile)
                            else -> Session.Ready(profile, householdId)
                        }
                    }
                    .catch { emit(Session.Failed(it.localizedMessage ?: "Couldn't load your account")) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Session.Loading)

    fun signOut() = AppGraph.auth.signOut()
}
