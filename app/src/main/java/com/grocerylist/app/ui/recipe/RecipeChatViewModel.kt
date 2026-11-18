package com.grocerylist.app.ui.recipe

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.ChatMessage
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.ServerException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The "Ask for a recipe" chat. The conversation lives only here, for as long as the screen is open; recipes
 * someone saves go to the household's recipe box.
 */
class RecipeChatViewModel(householdId: String, private val uid: String) : ViewModel() {
    private val recipes = AppGraph.recipes(householdId)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending
    /** What's in the text field; a message that fails to send comes back here. */
    val draft = MutableStateFlow("")
    /** Suggested recipes someone has saved, to the saved copy's ID. */
    private val _saved = MutableStateFlow<Map<Recipe, String>>(emptyMap())
    val saved: StateFlow<Map<Recipe, String>> = _saved
    /** The suggestion open in full, if any. */
    val viewing = MutableStateFlow<Recipe?>(null)

    private val errors = Channel<String>(Channel.BUFFERED)
    val userMessages: Flow<String> = errors.receiveAsFlow()

    fun send(text: String = draft.value) {
        val message = text.trim()
        if (message.isEmpty() || _sending.value) return
        val conversation = _messages.value + ChatMessage(fromUser = true, text = message)
        _messages.value = conversation
        draft.value = ""
        _sending.value = true
        viewModelScope.launch {
            try {
                val reply = recipes.chat(conversation)
                _messages.update { it + reply }
            } catch (e: Exception) {
                Log.w(TAG, "Recipe chat failed", e)
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
                _sending.value = false
            }
        }
    }

    fun save(recipe: Recipe) {
        if (recipe in _saved.value) return
        val id = recipes.add(recipe, uid)
        _saved.update { it + (recipe to id) }
    }

    private companion object {
        const val TAG = "RecipeChat"
    }
}
