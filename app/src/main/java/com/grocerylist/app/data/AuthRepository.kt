package com.grocerylist.app.data

import android.app.Activity
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthCredential
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.auth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/** How an account signs in, which is also how it confirms identity before being deleted. */
enum class SignInProvider { PASSWORD, GOOGLE, APPLE }

/** The user closed Apple's sign-in page. */
val Throwable.isAppleSignInCancelled: Boolean
    get() = this is FirebaseAuthException && errorCode == "ERROR_WEB_CONTEXT_CANCELED"

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

    /**
     * Opens Apple's sign-in page in a browser tab (Android has no Apple sheet). Someone who signed up on an
     * iPhone gets the same account: Apple's ID for a person is the same across one developer team's apps.
     * The result outlives the activity, which Android may close while the browser is in front.
     */
    fun startAppleSignIn(activity: Activity): Task<AuthResult> =
        auth.startActivityForSignInWithProvider(activity, appleProvider())

    /** An Apple sign-in still finishing after Android closed the app behind the browser, if there is one. */
    val pendingAppleSignIn: Task<AuthResult>? get() = auth.pendingAuthResult

    private fun appleProvider() = OAuthProvider.newBuilder("apple.com").setScopes(listOf("email", "name")).build()

    suspend fun updateDisplayName(name: String) {
        auth.currentUser?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())?.await()
    }

    /** Same order as iOS's AuthService.provider. */
    val provider: SignInProvider
        get() {
            val ids = auth.currentUser?.providerData?.map { it.providerId }.orEmpty()
            return when {
                "apple.com" in ids -> SignInProvider.APPLE
                EmailAuthProvider.PROVIDER_ID in ids -> SignInProvider.PASSWORD
                else -> SignInProvider.GOOGLE
            }
        }

    /** True if the account signs in with a password (otherwise it's Google or Apple only). */
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

    /**
     * Also ends the app's access to their Apple account, as Apple asks of apps that delete accounts. Unlike on
     * iPhone, failing at that doesn't stop the deletion: Apple accounts can drop the app in their own settings.
     */
    suspend fun reauthenticateWithApple(activity: Activity) {
        val user = auth.currentUser ?: error("Not signed in")
        val result = user.startActivityForReauthenticateWithProvider(activity, appleProvider()).await()
        val token = (result.credential as? OAuthCredential)?.accessToken ?: return
        runCatching { auth.revokeAccessToken(token).await() }
            .onFailure { Log.w("AuthRepository", "Couldn't revoke Apple access", it) }
    }

    /** Permanently deletes the login. Call last, after the user's data is gone. */
    suspend fun deleteUser() {
        auth.currentUser?.delete()?.await()
    }

    fun signOut() = auth.signOut()
}
