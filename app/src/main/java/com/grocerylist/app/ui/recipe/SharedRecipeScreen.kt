package com.grocerylist.app.ui.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.SharedRecipeException
import com.grocerylist.app.data.SharedRecipeProblem
import com.grocerylist.app.data.fetchSharedRecipe
import com.grocerylist.app.ui.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SharedRecipeState {
    data object Loading : SharedRecipeState
    data class Loaded(val recipe: Recipe) : SharedRecipeState
    data class Failed(val problem: SharedRecipeProblem) : SharedRecipeState
}

/** A recipe someone shared by link ([code]), to look at and save a copy of. iOS: SharedRecipeView. */
class SharedRecipeViewModel(
    private val householdId: String,
    private val uid: String,
    private val code: String,
    private val fetch: suspend (String) -> Recipe = ::fetchSharedRecipe,
) : ViewModel() {
    private val _state = MutableStateFlow<SharedRecipeState>(SharedRecipeState.Loading)
    val state: StateFlow<SharedRecipeState> = _state

    /** The open household's name when the user has several, so Save can say where the recipe goes. */
    val householdName: StateFlow<String?> = AppGraph.households.observeHouseholds(uid)
        .map { all -> if (all.size > 1) all.firstOrNull { it.id == householdId }?.name else null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        load()
    }

    fun load() {
        _state.value = SharedRecipeState.Loading
        viewModelScope.launch {
            _state.value = try {
                SharedRecipeState.Loaded(fetch(code))
            } catch (e: SharedRecipeException) {
                SharedRecipeState.Failed(e.problem)
            }
        }
    }

    /** Saves an exact copy to the open household and returns its ID. */
    fun save(recipe: Recipe): String = AppGraph.recipes(householdId).add(recipe, uid)
}

@Composable
fun SharedRecipeScreen(
    session: Session.Ready,
    code: String,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    onOpenList: (String) -> Unit,
) {
    val viewModel: SharedRecipeViewModel = viewModel(key = "shared-$code") {
        SharedRecipeViewModel(session.householdId, session.user.uid, code)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val householdName by viewModel.householdName.collectAsStateWithLifecycle()
    when (val s = state) {
        is SharedRecipeState.Loaded -> {
            val detail: RecipeViewModel = viewModel(key = "shared-detail-$code") {
                RecipeViewModel(session.householdId, null, session.user.uid)
            }
            val haptics = LocalHapticFeedback.current
            RecipeDetail(
                recipe = s.recipe,
                viewModel = detail,
                onBack = onBack,
                onOpenList = onOpenList,
                actions = {
                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            onSaved(viewModel.save(s.recipe))
                        },
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Text(householdName?.let { "Save to $it" } ?: "Save", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
            )
        }
        SharedRecipeState.Loading -> SharedRecipeMessage(onBack) { CircularProgressIndicator() }
        is SharedRecipeState.Failed -> SharedRecipeMessage(onBack) {
            val (title, text) = when (s.problem) {
                SharedRecipeProblem.EXPIRED ->
                    "This link has expired" to "Recipe links last 30 days. Ask whoever sent it to share it again."
                SharedRecipeProblem.NOT_FOUND ->
                    "Couldn't find that recipe" to "Check the link, or ask whoever sent it to share it again."
                SharedRecipeProblem.OFFLINE ->
                    "Couldn't open that recipe" to "Check your internet connection and try again."
            }
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (s.problem == SharedRecipeProblem.OFFLINE) {
                Button(onClick = viewModel::load) { Text("Try again") }
            } else {
                TextButton(onClick = onBack) { Text("Close") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedRecipeMessage(onBack: () -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) { content() }
    }
}
