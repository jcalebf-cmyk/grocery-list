package com.grocerylist.app.ui.home

import com.grocerylist.app.ui.onboarding.Onboarding
import com.grocerylist.app.ui.onboarding.OnboardingScreen

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import com.grocerylist.app.ui.sharedElement
import com.grocerylist.app.ui.photoGlass
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import com.grocerylist.app.ui.AppIcons
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.data.GroceryList
import com.grocerylist.app.data.Store
import com.grocerylist.app.ui.Session
import com.grocerylist.app.data.NearbyStores
import com.grocerylist.app.ui.StoreLogo
import com.grocerylist.app.ui.rememberLocationPermissionState
import com.grocerylist.app.ui.StorePicker
import com.grocerylist.app.ui.CuisineSetupScreen
import com.grocerylist.app.ui.CuisineSheet
import com.grocerylist.app.data.MAX_MOOD_CUISINES
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.grocerylist.app.ui.CloseOutListDialog
import com.grocerylist.app.ui.DeleteListDialog
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material.icons.filled.Delete
import androidx.compose.foundation.background

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    session: Session.Ready,
    onOpenList: (String) -> Unit,
    onOpenRecipe: (String) -> Unit,
    onOpenOnHand: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel { HomeViewModel(session.householdId, session.user.uid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingStore by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.onShown() }

    // The store picker asks for location the first time it opens. Once allowed, nearby stores are
    // fetched here in the background so New list opens straight onto them.
    val location = rememberLocationPermissionState()
    val context = LocalContext.current
    LaunchedEffect(location.granted) {
        if (location.granted) runCatching { NearbyStores.findCached(context) }
    }
    LaunchedEffect(Unit) { viewModel.userMessages.collect { snackbar.showSnackbar(it) } }

    // New members get the walkthrough. Otherwise a household that hasn't picked cuisines yet (say the app
    // closed partway through) gets just that step.
    val onboarding by Onboarding.pending.collectAsStateWithLifecycle()
    onboarding?.let { start ->
        OnboardingScreen(session, start, onDone = { Onboarding.pending.value = null; viewModel.onShown() })
        return
    }
    if (state.needsCuisines) {
        CuisineSetupScreen(onDone = viewModel::saveCuisines)
        return
    }
    var choosingMood by rememberSaveable { mutableStateOf(false) }
    var deletingList by remember { mutableStateOf<GroceryList?>(null) }
    var closingList by remember { mutableStateOf<GroceryList?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(state.householdName.ifEmpty { "Groceries" }, style = MaterialTheme.typography.headlineSmall)
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickingStore = true },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New list") },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                RecipesHeader(
                    updatedAt = state.recipesUpdatedAt.takeIf { state.recipes.isNotEmpty() },
                    refreshing = state.refreshingRecipes,
                    kitchenCount = state.kitchenCount,
                    mood = state.mood,
                    onRefresh = viewModel::refreshRecipes,
                    onOpenKitchen = onOpenOnHand,
                    onChooseMood = { choosingMood = true },
                )
            }
            item {
                when {
                    state.recipes.isNotEmpty() -> RecipeRow(state.recipes, onOpenRecipe)
                    !state.refreshingRecipes -> KitchenPrompt(onOpenKitchen = onOpenOnHand)
                }
            }
            if (state.activeLists.isNotEmpty()) {
                item { SectionHeader("Shopping lists") }
                items(state.activeLists, key = { it.id }) { list ->
                    SwipeAction(
                        icon = Icons.Default.CheckCircle,
                        label = "Close out",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onSwipe = { closingList = list },
                    ) { ListRow(list) { onOpenList(list.id) } }
                }
            } else {
                item {
                    Text(
                        "Tap New list and pick a store to start",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            if (state.pastLists.isNotEmpty()) {
                item { SectionHeader("Past lists") }
                items(state.pastLists, key = { it.id }) { list ->
                    SwipeAction(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onSwipe = { deletingList = list },
                    ) { ListRow(list) { onOpenList(list.id) } }
                }
            }
        }
    }

    deletingList?.let { list ->
        DeleteListDialog(
            onConfirm = {
                deletingList = null
                viewModel.deleteList(list)
            },
            onDismiss = { deletingList = null },
        )
    }

    closingList?.let { list ->
        CloseOutListDialog(
            onConfirm = {
                closingList = null
                viewModel.closeOut(list)
            },
            onDismiss = { closingList = null },
        )
    }

    if (choosingMood) {
        CuisineSheet(
            title = "What are you in the mood for?",
            subtitle = "Pick up to $MAX_MOOD_CUISINES and get recipes from just those tonight.",
            initial = state.mood,
            confirm = "Get recipes",
            max = MAX_MOOD_CUISINES,
            requireSelection = true,
            onConfirm = {
                choosingMood = false
                viewModel.setMood(it)
            },
            onDismiss = { choosingMood = false },
            secondary = if (state.mood.isNotEmpty()) {
                "Back to usual" to {
                    choosingMood = false
                    viewModel.setMood(emptyList())
                }
            } else {
                null
            },
        )
    }

    if (pickingStore) {
        StorePicker(
            stores = state.yourStores,
            onRemove = viewModel::removeStore,
            onPick = { store ->
                pickingStore = false
                onOpenList(viewModel.openListFor(store))
            },
            onPickNew = { name ->
                pickingStore = false
                onOpenList(viewModel.openListForNewStore(name))
            },
            onSkip = {
                pickingStore = false
                onOpenList(viewModel.openListWithoutStore())
            },
            openStoreIds = state.activeLists.mapTo(HashSet()) { it.storeId },
            onDismiss = { pickingStore = false },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

/**
 * Swiping left asks to confirm [onSwipe] (delete, or close out); the row springs back either way, and
 * moves once the change lands.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeAction(
    icon: ImageVector,
    label: String,
    containerColor: Color,
    contentColor: Color,
    onSwipe: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.EndToStart) onSwipe()
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(containerColor)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(icon, contentDescription = label, tint = contentColor)
            }
        },
    ) { content() }
}

@Composable
private fun ListRow(list: GroceryList, onClick: () -> Unit) {
    val time = list.completedAt ?: list.createdAt
    ListItem(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = {
            if (list.storeName.isBlank()) {
                Box(Modifier.size(48.dp, 36.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null)
                }
            } else {
                StoreLogo(list.storeName, Modifier.size(48.dp, 36.dp), padding = 4.dp, cornerRadius = 8.dp, initialsSize = 14.sp)
            }
        },
        headlineContent = { Text(list.displayName) },
        supportingContent = {
            Text(
                (if (list.isActive) "Started " else "Closed out ") +
                    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS),
            )
        },
    )
}

@Composable
private fun RecipesHeader(
    updatedAt: Long?,
    refreshing: Boolean,
    kitchenCount: Int,
    mood: List<String>,
    onRefresh: () -> Unit,
    onOpenKitchen: () -> Unit,
    onChooseMood: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Text("Recipes for you", style = MaterialTheme.typography.titleLarge)
        val status = when {
            refreshing -> "Finding new recipes…"
            updatedAt != null -> "Updated " + DateUtils.getRelativeTimeSpanString(
                updatedAt,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            ).toString().replaceFirstChar(Char::lowercase)
            else -> null
        }
        status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = onOpenKitchen,
                label = { Text(if (kitchenCount > 0) "My kitchen · $kitchenCount" else "My kitchen") },
                leadingIcon = { Icon(AppIcons.Kitchen, contentDescription = null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = onRefresh,
                enabled = !refreshing,
                label = { Text("New ideas") },
                leadingIcon = {
                    if (refreshing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(18.dp))
                    }
                },
            )
            FilterChip(
                selected = mood.isNotEmpty(),
                onClick = onChooseMood,
                enabled = !refreshing,
                label = { Text(if (mood.isEmpty()) "Tonight…" else "Tonight: ${mood.joinToString(", ")}") },
                leadingIcon = { Icon(AppIcons.Restaurant, contentDescription = null, Modifier.size(18.dp)) },
            )
        }
    }
}

/** Shown before there are any recipes, pointing people at the kitchen list. */
@Composable
private fun KitchenPrompt(onOpenKitchen: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("What's in your kitchen?", style = MaterialTheme.typography.titleMedium)
            Text(
                "Add a few ingredients you already have and you'll get simple recipes that use them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = onOpenKitchen) { Text("Add ingredients") }
        }
    }
}

@Composable
private fun RecipeRow(recipes: List<Recipe>, onOpen: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(recipes, key = { it.videoId }) { recipe -> RecipeCard(recipe, onClick = { onOpen(recipe.videoId) }) }
    }
}

/** Full-bleed photo with the title on a frosted strip, so the food does the talking. */
@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit) {
    val haze = rememberHazeState()
    val shape = MaterialTheme.shapes.large
    Box(
        Modifier
            .width(280.dp)
            .height(210.dp)
            .clip(shape)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = recipe.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .sharedElement("recipe-image-${recipe.videoId}")
                .hazeSource(haze),
        )
        Text(
            "${recipe.totalMinutes} min",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(CircleShape)
                .hazeEffect(haze, style = photoGlass())
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .hazeEffect(haze, style = photoGlass())
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                recipe.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${recipe.ingredients.size} ingredients",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
