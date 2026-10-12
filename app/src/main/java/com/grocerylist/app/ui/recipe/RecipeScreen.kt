package com.grocerylist.app.ui.recipe

import android.content.Intent
import com.grocerylist.app.ui.sharedElement
import com.grocerylist.app.ui.glass
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.AnimatedVisibility
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

    val haze = rememberHazeState()
    val listState = rememberLazyListState()
    // Past the photo, a frosted title bar fades in so you still know which recipe you're on.
    val pastHero by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        if (recipe != null) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().hazeSource(haze),
                contentPadding = PaddingValues(bottom = 112.dp),
            ) {
                item(key = "hero") { RecipeHero(recipe) }
                item(key = "title") { RecipeTitle(recipe) }
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
                itemsIndexed(recipe.steps) { i, step -> StepRow(i + 1, step) }
            }
        }

        AnimatedVisibility(
            visible = pastHero,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .hazeEffect(haze, style = glass())
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(start = 72.dp, end = 16.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    recipe?.title.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Frosted back button, readable over both the photo and the page.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .clip(CircleShape)
                .hazeEffect(haze, style = glass()),
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        if (recipe != null) {
            val count = recipe.ingredients.size - unchecked.size
            Button(
                onClick = { pickingStore = true },
                enabled = count > 0,
                shape = MaterialTheme.shapes.extraLarge,
                contentPadding = PaddingValues(vertical = 16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(16.dp)
                    .fillMaxWidth(),
            ) {
                Text(
                    if (count == 1) "Add 1 item to a list" else "Add $count items to a list",
                    style = MaterialTheme.typography.titleMedium,
                )
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

/** Full-bleed photo that the home screen's card grows into. Tapping it plays a video of a similar dish. */
@Composable
private fun RecipeHero(recipe: Recipe) {
    val context = LocalContext.current
    val openVideo = { context.startActivity(Intent(Intent.ACTION_VIEW, recipe.videoUrl.toUri())) }
    Box(Modifier.fillMaxWidth().height(340.dp).clickable(onClick = openVideo)) {
        AsyncImage(
            model = recipe.thumbnailUrl,
            contentDescription = "Watch a similar recipe on YouTube",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().sharedElement("recipe-image-${recipe.videoId}"),
        )
        // Keeps the status bar readable over bright photos.
        Box(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent))),
        )
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.45f),
            contentColor = Color.White,
            modifier = Modifier.align(Alignment.Center).size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(36.dp))
            }
        }
    }
}

@Composable
private fun RecipeTitle(recipe: Recipe) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(recipe.title, style = MaterialTheme.typography.headlineMedium)
        Text(
            "${recipe.totalMinutes} min · ${recipe.ingredients.size} ingredients",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (recipe.usesHouseholdItems.isNotEmpty()) {
            Text(
                "Uses your usual ${recipe.usesHouseholdItems.joinToString(", ")}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun StepRow(number: Int, step: String) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("$number", style = MaterialTheme.typography.labelLarge)
            }
        }
        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}
