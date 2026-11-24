package com.grocerylist.app.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.ServerException
import com.grocerylist.app.data.isAppleSignInCancelled
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.IOException

data class SignInState(
    val creatingAccount: Boolean = false,
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    /** Set when "Create account" is tried with mismatched passwords, so the error shows even mid-typing. */
    val submittedMismatch: Boolean = false,
    val busy: Boolean = false,
    val error: String? = null,
    /** The "Forgot password?" dialog: sending, sent (to this address), or failed. */
    val resetSending: Boolean = false,
    val resetSentTo: String? = null,
    val resetError: String? = null,
) {
    /** Firebase rejects shorter passwords, so say so before the user submits. */
    val passwordTooShort get() = creatingAccount && password.isNotEmpty() && password.length < MIN_PASSWORD_LENGTH

    private val passwordsMatch get() = password == confirmPassword

    /**
     * Show "passwords don't match" only once the confirmation can't turn into a match by typing more:
     * it's as long as the password, or already differs from how the password starts.
     */
    val showMismatch get() = creatingAccount && confirmPassword.isNotEmpty() && !passwordsMatch &&
        (submittedMismatch || confirmPassword.length >= password.length || !password.startsWith(confirmPassword))

    val canSubmit get() = !busy && email.isNotBlank() && password.isNotBlank() && (
        !creatingAccount || (
            name.isNotBlank() && password.length >= MIN_PASSWORD_LENGTH &&
                confirmPassword.isNotEmpty() && passwordsMatch
            )
        )

    companion object {
        /** Same as the reset page (account.html) and Firebase's password policy. */
        const val MIN_PASSWORD_LENGTH = 8
    }
}

class SignInViewModel : ViewModel() {
    private val _state = MutableStateFlow(SignInState())
    val state: StateFlow<SignInState> = _state

    fun setName(v: String) = _state.update { it.copy(name = v, error = null) }
    fun setEmail(v: String) = _state.update { it.copy(email = v, error = null) }
    fun setPassword(v: String) = _state.update { it.copy(password = v, submittedMismatch = false, error = null) }
    fun setConfirmPassword(v: String) = _state.update { it.copy(confirmPassword = v, submittedMismatch = false, error = null) }
    fun toggleMode() = _state.update {
        it.copy(creatingAccount = !it.creatingAccount, confirmPassword = "", submittedMismatch = false, error = null)
    }

    fun sendPasswordReset(email: String) {
        _state.update { it.copy(resetSending = true, resetError = null) }
        viewModelScope.launch {
            try {
                AppGraph.auth.sendPasswordReset(email)
            } catch (e: Exception) {
                _state.update { it.copy(resetSending = false, resetError = resetFailureMessage(e)) }
                return@launch
            }
            // Fill in the sign-in email too, ready for when they come back with a new password.
            _state.update { it.copy(resetSending = false, resetSentTo = email.trim(), email = email.trim()) }
        }
    }

    fun closePasswordReset() = _state.update { it.copy(resetSending = false, resetSentTo = null, resetError = null) }

    fun submit() {
        val s = _state.value
        if (s.creatingAccount && s.confirmPassword != s.password) {
            _state.update { it.copy(submittedMismatch = true) }
            return
        }
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

    fun signInWithApple(signIn: Task<AuthResult>) = run(::appleFailureMessage) { signIn.await() }

    /** Picks an Apple sign-in back up if Android closed the app while its page was open. */
    fun resumeAppleSignIn() {
        if (!_state.value.busy) AppGraph.auth.pendingAppleSignIn?.let(::signInWithApple)
    }

    fun showError(e: Throwable) = _state.update { it.copy(busy = false, error = friendlyMessage(e)) }

    private fun run(failureMessage: (Throwable) -> String = ::friendlyMessage, block: suspend () -> Unit) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                block()
                // This view model outlives the sign-in screen, so wipe what was typed (password included).
                // Otherwise signing out later would reopen the screen with the old details filled in.
                _state.value = SignInState()
            } catch (e: Exception) {
                Log.w("SignIn", "Sign-in failed", e)
                val message = if (e.isAppleSignInCancelled) null else failureMessage(e)
                _state.update { it.copy(busy = false, error = message) }
            }
        }
    }

    private fun resetFailureMessage(e: Throwable) = when {
        e is ServerException && e.isRateLimited -> "That's a lot of reset emails for one day. Try again tomorrow."
        e is IOException -> "Couldn't connect. Check your internet and try again."
        else -> "Couldn't send the email. Try again in a few minutes."
    }

    private fun appleFailureMessage(e: Throwable) = when {
        e is FirebaseAuthUserCollisionException ->
            "There's already an account with that email. Sign in the way you did before."
        e is FirebaseNetworkException -> "Couldn't connect. Check your internet and try again."
        else -> "Couldn't sign in with Apple. Try again, or use another way."
    }

    private fun friendlyMessage(e: Throwable) = when {
        e is FirebaseAuthWeakPasswordException || e.breaksPasswordPolicy ->
            "Use a password with at least ${SignInState.MIN_PASSWORD_LENGTH} characters."
        e is FirebaseAuthUserCollisionException -> "There's already an account with that email. Try signing in."
        e is FirebaseAuthInvalidCredentialsException || e is FirebaseAuthInvalidUserException ->
            "That email and password don't match an account."
        e is FirebaseTooManyRequestsException -> "Too many tries. Wait a few minutes and try again."
        else -> e.localizedMessage ?: "Something went wrong. Try again."
    }

    /** Refused by the password policy set in Firebase, which the form should have caught first. */
    private val Throwable.breaksPasswordPolicy
        get() = this is FirebaseAuthException && errorCode == "ERROR_PASSWORD_DOES_NOT_MEET_REQUIREMENTS"
}
