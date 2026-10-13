package com.grocerylist.app.data

import com.google.firebase.Firebase
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

class AuthRepository {
    private val auth = Firebase.auth

    val currentUser: Flow<FirebaseUser?> = callbackFlow {
        val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener { trySend(it.currentUser) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    suspend fun signInWithEmail(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun createAccount(name: String, email: String, password: String): FirebaseUser? {
        val user = auth.createUserWithEmailAndPassword(email.trim(), password).await().user ?: return null
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        // Confirming is optional, so a failed send mustn't fail sign-up; Settings offers to resend.
        runCatching { sendEmailConfirmation() }
        return user
    }

    /**
     * Our server sends the email (Firebase's own can't be redesigned). It answers the same whether or not
     * the address has an account, so the app never reveals who does.
     */
    suspend fun sendPasswordReset(email: String) {
        ServerApi.post("/api/send-password-reset", JSONObject().put("email", email.trim()), signedIn = false)
    }

    /**
     * True if the account signs in with a password and hasn't confirmed its email yet. Refreshes from
     * Firebase first, since the app's copy only updates on sign-in (not when the link is tapped).
     */
    suspend fun needsEmailConfirmation(): Boolean {
        if (!usesPassword) return false
        runCatching { auth.currentUser?.reload()?.await() }
        return auth.currentUser?.isEmailVerified == false
    }

    /** Our server sends the email, so it can carry the app's design (Firebase's own can't be changed). */
    suspend fun sendEmailConfirmation() {
        ServerApi.post("/api/send-verification", JSONObject())
    }

    suspend fun signInWithGoogle(idToken: String) {
        auth.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    suspend fun updateDisplayName(name: String) {
        auth.currentUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())?.await()
    }

    /** True if the account signs in with a password (otherwise it's Google-only). */
    val usesPassword: Boolean
        get() = auth.currentUser?.providerData?.any { it.providerId == EmailAuthProvider.PROVIDER_ID } == true

    /** Firebase only allows deleting an account right after signing in, so deletion confirms identity first. */
    suspend fun reauthenticateWithPassword(password: String) {
        val user = auth.currentUser ?: error("Not signed in")
        user.reauthenticate(EmailAuthProvider.getCredential(user.email.orEmpty(), password)).await()
    }

    suspend fun reauthenticateWithGoogle(idToken: String) {
        val user = auth.currentUser ?: error("Not signed in")
        user.reauthenticate(GoogleAuthProvider.getCredential(idToken, null)).await()
    }

    /** Permanently deletes the login. Call last, after the user's data is gone. */
    suspend fun deleteUser() {
        auth.currentUser?.delete()?.await()
    }

    fun signOut() = auth.signOut()
}
