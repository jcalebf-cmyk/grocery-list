package com.grocerylist.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.grocerylist.app.AppGraph
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SignInState(
    val creatingAccount: Boolean = false,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val busy: Boolean = false,
    val error: String? = null,
) {
    val canSubmit get() =
        !busy && email.isNotBlank() && password.isNotBlank() && (!creatingAccount || name.isNotBlank())
}

class SignInViewModel : ViewModel() {
    private val _state = MutableStateFlow(SignInState())
    val state: StateFlow<SignInState> = _state

    fun setName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun setEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun setPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun toggleMode() = _state.update { it.copy(creatingAccount = !it.creatingAccount, error = null) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        run {
            if (s.creatingAccount) {
                // The profile doc is first written before the name is set, so write it again with the name.
                AppGraph.auth.createAccount(s.name, s.email, s.password)?.let(AppGraph.households::ensureProfile)
            } else {
                AppGraph.auth.signInWithEmail(s.email, s.password)
            }
        }
    }

    fun signInWithGoogle(idToken: String) = run { AppGraph.auth.signInWithGoogle(idToken) }

    fun showError(e: Throwable) = _state.update { it.copy(busy = false, error = friendlyMessage(e)) }

    // On success the session flow switches screens, so there's nothing to reset.
    private fun run(block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                showError(e)
            }
        }
    }

    private fun friendlyMessage(e: Throwable) = when (e) {
        is FirebaseAuthWeakPasswordException -> "Use a password with at least 6 characters."
        is FirebaseAuthUserCollisionException -> "There's already an account with that email. Try signing in."
        is FirebaseAuthInvalidCredentialsException, is FirebaseAuthInvalidUserException ->
            "That email and password don't match an account."
        else -> e.localizedMessage ?: "Something went wrong. Try again."
    }
}
