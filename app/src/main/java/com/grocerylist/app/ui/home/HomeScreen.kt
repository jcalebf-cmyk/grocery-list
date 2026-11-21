package com.grocerylist.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.grocerylist.app.ui.onboarding.Onboarding
import com.grocerylist.app.ui.onboarding.OnboardingScreen

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import com.grocerylist.app.ui.AppIcons
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.foundation.layout.size
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.RecipeSource
import com.grocerylist.app.ui.recipe.recipeSummary
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.data.GroceryList
import com.grocerylist.app.ui.Session
import com.grocerylist.app.data.NearbyStores
import com.grocerylist.app.ui.StoreLogo
import com.grocerylist.app.ui.rememberLocationPermissionState
import com.grocerylist.app.ui.StorePicker
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.FabPosition
import androidx.compose.ui.unit.Dp
import com.grocerylist.app.ui.TwoPanes
import com.grocerylist.app.ui.readable
import com.grocerylist.app.ui.rememberWindowLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    session: Session.Ready,
    onOpenList: (String) -> Unit,
    onOpenRecipe: (String) -> Unit,
    onAskForRecipe: () -> Unit,
    onPasteRecipe: () -> Unit,
    onAllRecipes: () -> Unit,
    onOpenPastLists: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel { HomeViewModel(session.householdId, session.user.uid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingStore by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    // The store picker asks for location the first time it opens. Once allowed, nearby stores are
    // fetched here in the background so New list opens straight onto them.
    val location = rememberLocationPermissionState()
    val context = LocalContext.current
    LaunchedEffect(location.granted) {
        if (location.granted) runCatching { NearbyStores.findCached(context) }
    }
    LaunchedEffect(Unit) { viewModel.userMessages.collect { snackbar.showSnackbar(it) } }

    // New members get the walkthrough first.
    val onboarding by Onboarding.pending.collectAsStateWithLifecycle()
    onboarding?.let { start ->
        OnboardingScreen(session, start, onDone = { Onboarding.pending.value = null })
        return
    }
    var deletingList by remember { mutableStateOf<GroceryList?>(null) }
    // By id so the confirmation survives unfolding the phone (which rebuilds the screen).
    var closingListId by rememberSaveable { mutableStateOf<String?>(null) }
    val closingList = state.activeLists.firstOrNull { it.id == closingListId }
    val layout = rememberWindowLayout()

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
        // Side by side, New list sits under the lists rather than over the recipes.
        floatingActionButtonPosition = if (layout.twoPane) FabPosition.Start else FabPosition.End,
        // With no list going, the Start a list card does this job instead.
        floatingActionButton = {
            if (state.activeLists.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { pickingStore = true },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("New list") },
                )
            }
        },
    ) { padding ->
        // Lists come first: they're what people open the app for.
        val lists: LazyListScope.() -> Unit = {
            item { SectionHeader("Shopping lists", top = 8.dp) }
            if (state.activeLists.isEmpty()) {
                item { StartListCard(onStart = { pickingStore = true }) }
            }
            items(state.activeLists, key = { it.id }) { list ->
                SwipeAction(
                    icon = Icons.Default.CheckCircle,
                    label = "Close out",
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onSwipe = { closingListId = list.id },
                ) { ListRow(list) { onOpenList(list.id) } }
            }
            // Past lists stay out of the way: one row under the lists that opens the full history.
            if (state.pastListCount > 0) {
                item { PastListsRow(state.pastListCount, onClick = onOpenPastLists) }
            }
        }
        val recipesHeader = @Composable { top: Dp, inset: Dp ->
            RecipesHeader(
                hasRecipes = state.recipes.isNotEmpty(),
                onAsk = onAskForRecipe,
                onPaste = onPasteRecipe,
                onSeeAll = onAllRecipes,
                top = top,
                inset = inset,
            )
        }
        val noRecipes = @Composable { inset: Dp ->
            Text(
                "Save recipes you like here: ask for one, or paste one from anywhere.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = inset, vertical = 8.dp),
            )
        }
        val recipes = state.recipes.take(RECIPES_SHOWN)

        if (layout.twoPane) {
            // Unfolded: lists on one side, recipes as a grid on the other.
            val panePadding = PaddingValues(bottom = padding.calculateBottomPadding())
            TwoPanes(
                layout,
                Modifier.padding(top = padding.calculateTopPadding()),
                first = { LazyColumn(Modifier.fillMaxSize(), contentPadding = panePadding) { lists() } },
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(180.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = padding.calculateBottomPadding() + 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // The grid already sits 16dp in, so the header and chips line up with the cards.
                    item(span = { GridItemSpan(maxLineSpan) }) { recipesHeader(0.dp, 0.dp) }
                    if (recipes.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) { noRecipes(0.dp) }
                    }
                    gridItems(recipes, key = { it.id }) { recipe ->
                        RecipeCard(recipe, onClick = { onOpenRecipe(recipe.id) }, Modifier.fillMaxWidth().height(128.dp))
                    }
                }
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.readable()) {
                lists()
                item { recipesHeader(24.dp, 16.dp) }
                item { if (recipes.isNotEmpty()) RecipeRow(recipes, onOpenRecipe) else noRecipes(16.dp) }
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
                closingListId = null
                viewModel.closeOut(list)
            },
            onDismiss = { closingListId = null },
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
private fun SectionHeader(text: String, top: androidx.compose.ui.unit.Dp = 24.dp) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, top = top, bottom = 8.dp),
    )
}

/** Shown under Shopping lists when none is going, so starting one is the obvious next step. */
@Composable
private fun StartListCard(onStart: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("No lists going", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Pick a store and start adding what you need.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(
            onClick = onStart,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
            Text("Start a list", Modifier.padding(start = 8.dp))
        }
    }
}

/** "Past lists · 12 ›" under the shopping lists on Home. */
@Composable
private fun PastListsRow(count: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = { Icon(AppIcons.History, contentDescription = null) },
        headlineContent = { Text("Past lists") },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$count", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        },
    )
}

/** Every closed-out list, newest first. Swipe left to delete one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PastListsScreen(session: Session.Ready, onBack: () -> Unit, onOpenList: (String) -> Unit) {
    val viewModel: HomeViewModel = viewModel(key = "past-lists") { HomeViewModel(session.householdId, session.user.uid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deletingListId by rememberSaveable { mutableStateOf<String?>(null) }
    val deletingList = state.pastLists.firstOrNull { it.id == deletingListId }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.userMessages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Past lists") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.readable()) {
            items(state.pastLists, key = { it.id }) { list ->
                SwipeAction(
                    icon = Icons.Default.Delete,
                    label = "Delete",
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onSwipe = { deletingListId = list.id },
                ) { ListRow(list) { onOpenList(list.id) } }
            }
            if (state.pastLists.isEmpty()) {
                item {
                    Text(
                        "Lists you close out show up here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }

    deletingList?.let { list ->
        DeleteListDialog(
            onConfirm = {
                deletingListId = null
                viewModel.deleteList(list)
            },
            onDismiss = { deletingListId = null },
        )
    }
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

/** Home shows the newest few; the rest are a tap away. */
private const val RECIPES_SHOWN = 10

@Composable
private fun RecipesHeader(
    hasRecipes: Boolean,
    onAsk: () -> Unit,
    onPaste: () -> Unit,
    onSeeAll: () -> Unit,
    top: Dp = 24.dp,
    inset: Dp = 16.dp,
) {
    Column(Modifier.fillMaxWidth().padding(top = top, bottom = 4.dp)) {
        // "See all" is a text button, whose own padding already insets its label.
        Row(Modifier.fillMaxWidth().padding(start = inset, end = (inset - 8.dp).coerceAtLeast(0.dp)), verticalAlignment = Alignment.CenterVertically) {
            Text("Recipes", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (hasRecipes) TextButton(onClick = onSeeAll) { Text("See all") }
        }
        Row(
            Modifier.padding(top = 4.dp).horizontalScroll(rememberScrollState()).padding(horizontal = inset),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = onAsk,
                label = { Text("Ask for a recipe") },
                leadingIcon = { Icon(AppIcons.Sparkle, contentDescription = null, Modifier.size(18.dp)) },
            )
            AssistChip(
                onClick = onPaste,
                label = { Text("Paste a recipe") },
                leadingIcon = { Icon(AppIcons.Paste, contentDescription = null, Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun RecipeRow(recipes: List<Recipe>, onOpen: (String) -> Unit) {
    // A newly saved recipe goes first; without this the row stays on the card it showed and hides it.
    val state = rememberLazyListState()
    LaunchedEffect(recipes.first().id) { state.scrollToItem(0) }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(recipes, key = { it.id }) { recipe -> RecipeCard(recipe, onClick = { onOpen(recipe.id) }) }
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, onClick: () -> Unit, modifier: Modifier = Modifier.width(220.dp).height(128.dp)) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                when {
                    recipe.sourceUrl.isNotBlank() -> Icons.Default.PlayArrow
                    recipe.source == RecipeSource.AI -> AppIcons.Sparkle
                    else -> AppIcons.Restaurant
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(recipe.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text(
                recipeSummary(recipe),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
