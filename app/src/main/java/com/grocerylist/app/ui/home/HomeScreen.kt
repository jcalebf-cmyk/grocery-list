package com.grocerylist.app.ui.home

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import com.grocerylist.app.data.Recipe
import coil3.compose.AsyncImage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material3.Card
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.data.GroceryList
import com.grocerylist.app.data.Store
import com.grocerylist.app.ui.Session
import com.grocerylist.app.ui.StorePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    session: Session.Ready,
    onOpenList: (String) -> Unit,
    onOpenRecipe: (String) -> Unit,
    onOpenHousehold: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel { HomeViewModel(session.householdId, session.user.uid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingStore by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.householdName.ifEmpty { "Groceries" }) },
                actions = {
                    IconButton(onClick = onOpenHousehold) {
                        Icon(Icons.Default.Person, contentDescription = "Household")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickingStore = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New list") },
            )
        },
    ) { padding ->
        if (state.activeLists.isEmpty() && state.pastLists.isEmpty() && state.recipes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Tap New list and pick a store to start",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
                if (state.recipes.isNotEmpty()) {
                    item { SectionHeader("Recipes for you") }
                    item { RecipeRow(state.recipes, onOpenRecipe) }
                }
                if (state.activeLists.isNotEmpty()) {
                    item { SectionHeader("Shopping lists") }
                    items(state.activeLists, key = { it.id }) { list -> ListRow(list) { onOpenList(list.id) } }
                }
                if (state.pastLists.isNotEmpty()) {
                    item { SectionHeader("Past trips") }
                    items(state.pastLists, key = { it.id }) { list -> ListRow(list) { onOpenList(list.id) } }
                }
            }
        }
    }

    if (pickingStore) {
        StorePicker(
            stores = state.stores,
            onPick = { store ->
                pickingStore = false
                onOpenList(viewModel.openListFor(store))
            },
            onAddStore = viewModel::addStore,
            onDismiss = { pickingStore = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun ListRow(list: GroceryList, onClick: () -> Unit) {
    val time = list.completedAt ?: list.createdAt
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(if (list.isActive) Icons.Default.ShoppingCart else Icons.Default.Check, contentDescription = null)
        },
        headlineContent = { Text(list.storeName) },
        supportingContent = {
            Text(
                (if (list.isActive) "Started " else "Shopped ") +
                    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
            )
        },
    )
}

@Composable
private fun RecipeRow(recipes: List<Recipe>, onOpen: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(recipes, key = { it.videoId }) { recipe ->
            Card(onClick = { onOpen(recipe.videoId) }, modifier = Modifier.width(240.dp)) {
                AsyncImage(
                    model = recipe.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                )
                Column(Modifier.padding(12.dp)) {
                    Text(
                        recipe.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${recipe.totalMinutes} min · ${recipe.ingredients.size} ingredients",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
