package com.grocerylist.app.data

import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.grocerylist.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** A non-200 reply from the server. 429 means the user hit a daily limit. */
class ServerException(val status: Int, message: String) : Exception(message) {
    val isRateLimited get() = status == 429
}

/**
 * Calls the Vercel server. Requests carry the signed-in user's Firebase ID token; only the password reset
 * email (sent while signed out) goes without one.
 */
object ServerApi {
    suspend fun post(path: String, body: JSONObject, readTimeoutMs: Int = 30_000, signedIn: Boolean = true): JSONObject {
        val token = if (signedIn) {
            Firebase.auth.currentUser?.getIdToken(false)?.await()?.token ?: error("Not signed in")
        } else {
            null
        }
        return withContext(Dispatchers.IO) {
            val conn = URL("${BuildConfig.RECIPES_API_URL}$path").openConnection() as HttpURLConnection
            try {
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = readTimeoutMs
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                if (token != null) conn.setRequestProperty("Authorization", "Bearer $token")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
                if (conn.responseCode != 200) {
                    throw ServerException(
                        conn.responseCode,
                        "$path returned ${conn.responseCode}: ${conn.errorStream?.bufferedReader()?.readText()}",
                    )
                }
                JSONObject(conn.inputStream.bufferedReader().readText())
            } finally {
                conn.disconnect()
            }
        }
    }
}
