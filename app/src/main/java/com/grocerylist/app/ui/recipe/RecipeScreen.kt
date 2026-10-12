package com.grocerylist.app.ui.recipe

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.ui.Session
import com.grocerylist.app.ui.StorePicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeScreen(session: Session.Ready, videoId: String, onBack: () -> Unit, onOpenList: (String) -> Unit) {
    val viewModel: RecipeViewModel = viewModel { RecipeViewModel(session.householdId, videoId, session.user.uid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val recipe = state.recipe
    // Everything starts checked except what's on hand; the shopper unticks anything else they have.
    val onHandIndices = state.onHandIndices
    var unchecked by remember(videoId, onHandIndices) { mutableStateOf(onHandIndices) }
    var pickingStore by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipe?.title.orEmpty(), maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (recipe != null) {
                val count = recipe.ingredients.size - unchecked.size
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { pickingStore = true },
                        enabled = count > 0,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    ) { Text(if (count == 1) "Add 1 item to a list" else "Add $count items to a list") }
                }
            }
        },
    ) { padding ->
        if (recipe == null) return@Scaffold
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { RecipeHeader(recipe) }
            item { SectionTitle("Ingredients") }
            itemsIndexed(recipe.ingredients) { i, ingredient ->
                val checked = i !in unchecked
                val toggle = { unchecked = if (checked) unchecked + i else unchecked - i }
                ListItem(
                    modifier = Modifier.clickable(onClick = toggle),
                    leadingContent = { Checkbox(checked = checked, onCheckedChange = { toggle() }) },
                    headlineContent = { Text(ingredient.name.replaceFirstChar(Char::uppercase)) },
                    supportingContent = if (i in onHandIndices) {
                        { Text("In your kitchen", color = MaterialTheme.colorScheme.primary) }
                    } else {
                        null
                    },
                    trailingContent = { if (ingredient.quantity.isNotBlank()) Text(ingredient.quantity) },
                )
            }
            item { SectionTitle("Steps") }
            itemsIndexed(recipe.steps) { i, step ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Text(
                        "${i + 1}.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(28.dp),
                    )
                    Text(step, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    if (pickingStore && recipe != null) {
        StorePicker(
            stores = state.yourStores,
            title = "Add to which list?",
            onPick = { store ->
                pickingStore = false
                val chosen = recipe.ingredients.filterIndexed { i, _ -> i !in unchecked }
                onOpenList(viewModel.addToList(store, chosen))
            },
            onPickNew = { name ->
                pickingStore = false
                val chosen = recipe.ingredients.filterIndexed { i, _ -> i !in unchecked }
                onOpenList(viewModel.addToNewStore(name, chosen))
            },
            onSkip = {
                pickingStore = false
                val chosen = recipe.ingredients.filterIndexed { i, _ -> i !in unchecked }
                onOpenList(viewModel.addToList(null, chosen))
            },
            openStoreIds = state.activeLists.mapTo(HashSet()) { it.storeId },
            onDismiss = { pickingStore = false },
        )
    }
}

@Composable
private fun RecipeHeader(recipe: Recipe) {
    val context = LocalContext.current
    val openVideo = { context.startActivity(Intent(Intent.ACTION_VIEW, recipe.videoUrl.toUri())) }
    Column {
        AsyncImage(
            model = recipe.thumbnailUrl,
            contentDescription = "Watch on YouTube",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clickable(onClick = openVideo),
        )
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(recipe.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "${recipe.totalMinutes} min · from ${recipe.channel}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (recipe.usesHouseholdItems.isNotEmpty()) {
                Text(
                    "Uses your usual ${recipe.usesHouseholdItems.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            OutlinedButton(onClick = openVideo, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Text("Watch on YouTube", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
