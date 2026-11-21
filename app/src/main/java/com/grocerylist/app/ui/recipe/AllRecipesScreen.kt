package com.grocerylist.app.ui.recipe

import com.grocerylist.app.ui.readable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.itemKey
import com.grocerylist.app.ui.AppIcons
import com.grocerylist.app.ui.Session

/** True if every word of [query] appears in the recipe's title or an ingredient. Same as `matchesRecipe` in Logic.swift. */
fun matchesRecipe(recipe: Recipe, query: String): Boolean {
    val words = itemKey(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
    val haystack = itemKey((listOf(recipe.title) + recipe.ingredients.map { it.name }).joinToString(" "))
    return words.all { it in haystack }
}

/** Every saved recipe, newest first, with search by title or ingredient. */
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
    val shown = remember(recipes, query) { recipes.filter { matchesRecipe(it, query) } }

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
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.readable()) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by name or ingredient") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(shown, key = { it.id }) { recipe ->
                ListItem(
                    modifier = Modifier.clickable { onOpenRecipe(recipe.id) },
                    headlineContent = { Text(recipe.title) },
                    supportingContent = recipeSummary(recipe).takeIf { it.isNotEmpty() }?.let { { Text(it) } },
                )
            }
            if (shown.isEmpty() && query.isNotBlank()) {
                item {
                    Text(
                        "No recipes match \"$query\"",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
