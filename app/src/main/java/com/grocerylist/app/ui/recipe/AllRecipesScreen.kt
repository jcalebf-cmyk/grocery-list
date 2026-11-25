package com.grocerylist.app.ui.recipe

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.searchRecipes
import com.grocerylist.app.ui.AppIcons
import com.grocerylist.app.ui.ReadableWidth
import com.grocerylist.app.ui.Session
import com.grocerylist.app.ui.readable
import com.grocerylist.app.ui.windowWidth
import kotlinx.coroutines.flow.drop

/** Every saved recipe as cards, newest first, with search. Opens ready to type. iOS: AllRecipesView. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllRecipesScreen(
    session: Session.Ready,
    onBack: () -> Unit,
    onOpenRecipe: (String) -> Unit,
    onAsk: () -> Unit,
    onPaste: () -> Unit,
) {
    val recipes by remember { AppGraph.recipes(session.householdId).observeAll() }.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(recipes, query) { searchRecipes(recipes, query) }
    val columns = recipeColumns(minOf(windowWidth(), ReadableWidth))

    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    // Only on opening, not on coming back from a recipe.
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!opened) focus.requestFocus()
        opened = true
    }
    val scroll = rememberLazyListState()
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged) { if (dragged) focusManager.clearFocus() }
    // The best matches are at the top.
    LaunchedEffect(scroll) { snapshotFlow { query }.drop(1).collect { scroll.scrollToItem(0) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recipes") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = onAsk) { Icon(AppIcons.Sparkle, contentDescription = "Ask for a recipe") }
                    IconButton(onClick = onPaste) { Icon(AppIcons.Paste, contentDescription = "Paste a recipe") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search by name or ingredient") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = ""; focus.requestFocus() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .widthIn(max = ReadableWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .focusRequester(focus),
            )
            LazyColumn(
                Modifier.fillMaxSize().imePadding(),
                state = scroll,
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp).readable(),
            ) {
                recipeRows(shown, columns, onOpenRecipe)
                if (shown.isEmpty() && query.isNotBlank()) {
                    item {
                        Text(
                            "No recipes match \"${query.trim()}\"",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}
