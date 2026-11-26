package com.grocerylist.app.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.grocerylist.app.AppGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The "Ask for a recipe" conversation, kept on this phone (one per account and household) until someone taps Start
 * over, so leaving the chat and coming back, or closing the app, picks it up where it was. It isn't shared with the
 * household: only recipes someone saves are. A reply still lands if they leave while it's on its way.
 * iOS: RecipeChat in RecipeChatView.swift.
 */
class RecipeChatSession internal constructor(
    private val file: AtomicFile,
    private val recipes: RecipeRepository,
    private val uid: String,
) {
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending
    /** What's in the text field; a message that fails to send comes back here. */
    val draft = MutableStateFlow("")
    /** Suggested recipes someone has saved, to the saved copy's ID. */
    private val _saved = MutableStateFlow<Map<Recipe, String>>(emptyMap())
    val saved: StateFlow<Map<Recipe, String>> = _saved
    /** Bumped by Start over, so a reply to the old conversation doesn't land in the new one. */
    private var generation = 0

    private val errors = Channel<String>(Channel.BUFFERED)
    val userMessages: Flow<String> = errors.receiveAsFlow()

    init {
        runCatching { decode(String(file.readFully())) }
            .onSuccess { (messages, saved) ->
                _messages.value = messages
                _saved.value = saved
            }
            .onFailure { if (file.baseFile.exists()) Log.w(TAG, "Couldn't read the saved recipe chat", it) }
    }

    fun send(text: String = draft.value) {
        val message = text.trim()
        if (message.isEmpty() || _sending.value) return
        val conversation = _messages.value + ChatMessage(fromUser = true, text = message)
        _messages.value = conversation
        draft.value = ""
        _sending.value = true
        val started = generation
        AppGraph.appScope.launch {
            try {
                val reply = recipes.chat(conversation)
                if (started != generation) return@launch
                _messages.update { it + reply }
                persist()
            } catch (e: Exception) {
                Log.w(TAG, "Recipe chat failed", e)
                if (started != generation) return@launch
                // Take the message back off so the conversation still alternates, and put it back in the field.
                _messages.value = conversation.dropLast(1)
                draft.value = message
                errors.send(
                    if ((e as? ServerException)?.isRateLimited == true) {
                        "That's all the recipe chat for today. Check back tomorrow."
                    } else {
                        "Couldn't get an answer. Check your connection and try again."
                    },
                )
            } finally {
                if (started == generation) _sending.value = false
            }
        }
    }

    fun save(recipe: Recipe) {
        if (recipe in _saved.value) return
        val id = recipes.add(recipe, uid)
        _saved.update { it + (recipe to id) }
        AppGraph.appScope.launch { persist() }
    }

    /** Clears the conversation for a fresh one. Recipes already saved stay in the recipe box. */
    fun startOver() {
        generation++
        _messages.value = emptyList()
        _saved.value = emptyMap()
        _sending.value = false
        draft.value = ""
        AppGraph.appScope.launch { persist() }
    }

    private suspend fun persist() {
        // A reply that lands after signing out isn't kept for the next person.
        if (Firebase.auth.currentUser?.uid != uid) return
        val messages = _messages.value
        val saved = _saved.value
        withContext(Dispatchers.IO) {
            synchronized(file) {
                if (messages.isEmpty()) {
                    file.delete()
                    return@synchronized
                }
                val stream = file.startWrite()
                try {
                    stream.write(encode(messages, saved).toByteArray())
                    file.finishWrite(stream)
                } catch (e: Exception) {
                    Log.w(TAG, "Couldn't save the recipe chat", e)
                    file.failWrite(stream)
                }
            }
        }
    }

    internal companion object {
        const val TAG = "RecipeChat"

        fun encode(messages: List<ChatMessage>, saved: Map<Recipe, String>): String = JSONObject()
            .put("version", 1)
            .put(
                "messages",
                JSONArray(
                    messages.map { m ->
                        JSONObject()
                            .put("fromUser", m.fromUser)
                            .put("text", m.text)
                            .put(
                                "recipes",
                                JSONArray(
                                    m.recipes.map { r ->
                                        recipeCardJson(r).apply { saved[r]?.let { put("savedId", it) } }
                                    },
                                ),
                            )
                    },
                ),
            )
            .toString()

        fun decode(text: String): Pair<List<ChatMessage>, Map<Recipe, String>> {
            val saved = HashMap<Recipe, String>()
            val array = JSONObject(text).getJSONArray("messages")
            val messages = (0 until array.length()).map { i ->
                val m = array.getJSONObject(i)
                val cards = m.optJSONArray("recipes") ?: JSONArray()
                ChatMessage(
                    fromUser = m.getBoolean("fromUser"),
                    text = m.optString("text"),
                    recipes = (0 until cards.length()).map { j ->
                        val o = cards.getJSONObject(j)
                        recipeFromCardJson(o, RecipeSource.AI).also { r ->
                            o.optString("savedId").takeIf { it.isNotEmpty() }?.let { saved[r] = it }
                        }
                    },
                )
            }
            return messages to saved
        }
    }
}

/** One recipe chat per account and household, for as long as the app runs; [RecipeChatSession] keeps them on disk. */
object RecipeChats {
    private val sessions = HashMap<String, RecipeChatSession>()

    fun session(context: Context, householdId: String, uid: String): RecipeChatSession = synchronized(sessions) {
        sessions.getOrPut("$uid/$householdId") {
            val file = File(dir(context), "${uid.safe()}-${householdId.safe()}.json")
            RecipeChatSession(AtomicFile(file), AppGraph.recipes(householdId), uid)
        }
    }

    /** On sign-out (or account deletion), so the next person on this phone doesn't see them. */
    fun clearAll(context: Context) {
        synchronized(sessions) { sessions.clear() }
        dir(context).deleteRecursively()
    }

    private fun dir(context: Context) = File(context.filesDir, "recipe-chats").apply { mkdirs() }

    private fun String.safe() = replace(Regex("[^A-Za-z0-9_-]"), "_")
}
