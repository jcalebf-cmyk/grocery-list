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
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.grocerylist.app.ui.AppIcons
import androidx.compose.material3.AssistChip
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarHost
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Card
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.grocerylist.app.data.Household
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
import androidx.compose.ui.unit.Dp
import androidx.compose.animation.AnimatedContent
import com.grocerylist.app.data.RecipeMatch
import com.grocerylist.app.ui.ReadableWidth
import com.grocerylist.app.ui.recipe.RecipeSearchBar
import com.grocerylist.app.ui.recipe.recipeColumns
import com.grocerylist.app.ui.recipe.recipeRows
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import com.grocerylist.app.ui.TwoPanes
import com.grocerylist.app.ui.readable
import com.grocerylist.app.ui.rememberWindowLayout
import com.grocerylist.app.ui.sharedContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    session: Session.Ready,
    onOpenList: (String) -> Unit,
    onOpenRecipe: (String) -> Unit,
    onAskForRecipe: () -> Unit,
    onPasteRecipe: () -> Unit,
    onSearchRecipes: () -> Unit,
    onOpenPastLists: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: HomeViewModel = viewModel { HomeViewModel(session.householdId, session.user.uid) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val households by viewModel.households.collectAsStateWithLifecycle()
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
                    HouseholdTitle(
                        name = state.householdName.ifEmpty { "Groceries" },
                        households = households,
                        openId = session.householdId,
                        onSwitch = viewModel::switchHousehold,
                    )
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        // Lists come first: they're what people open the app for.
        val lists: LazyListScope.() -> Unit = {
            item(key = "lists-header") {
                SectionHeader("Shopping lists") {
                    // With no list going, the Start a list card does this job instead.
                    if (state.activeLists.isNotEmpty()) NewListButton { pickingStore = true }
                }
            }
            if (state.activeLists.isEmpty()) {
                item(key = "start-list") { Box(Modifier.animateItem()) { StartListCard(onStart = { pickingStore = true }) } }
            }
            // New lists slide in, and closed-out ones fold away.
            items(state.activeLists, key = { it.id }) { list ->
                Box(Modifier.animateItem()) {
                    SwipeAction(
                        icon = Icons.Default.CheckCircle,
                        label = "Close out",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onSwipe = { closingListId = list.id },
                    ) { ListRow(list) { onOpenList(list.id) } }
                }
            }
            // Past lists stay out of the way: one row under the lists that opens the full history.
            if (state.pastListCount > 0) {
                item(key = "past-lists") { Box(Modifier.animateItem()) { PastListsRow(state.pastListCount, onClick = onOpenPastLists) } }
            }
        }
        val recipesHeader = @Composable { top: Dp ->
            RecipesHeader(
                hasRecipes = state.recipes.isNotEmpty(),
                onAsk = onAskForRecipe,
                onPaste = onPasteRecipe,
                onSearch = onSearchRecipes,
                top = top,
            )
        }
        val noRecipes = @Composable {
            Text(
                "Save recipes you like here: ask for one, or paste one from anywhere.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        val recipes = remember(state.recipes) { state.recipes.map { RecipeMatch(it) } }

        if (layout.twoPane) {
            // Unfolded: lists on one side, recipes on the other.
            val panePadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp)
            TwoPanes(
                layout,
                Modifier.padding(top = padding.calculateTopPadding()),
                first = { LazyColumn(Modifier.fillMaxSize(), contentPadding = panePadding) { lists() } },
            ) {
                BoxWithConstraints {
                    val columns = recipeColumns(maxWidth)
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = panePadding) {
                        item { recipesHeader(0.dp) }
                        if (recipes.isEmpty()) item { noRecipes() }
                        recipeRows(recipes, columns, onOpenRecipe)
                    }
                }
            }
        } else {
            val columns = recipeColumns(minOf(layout.width, ReadableWidth))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = padding.readable()) {
                lists()
                item { recipesHeader(16.dp) }
                if (recipes.isEmpty()) item { noRecipes() }
                recipeRows(recipes, columns, onOpenRecipe)
                item { Spacer(Modifier.height(16.dp)) }
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

/** The open household's name. With more than one, tapping it switches between them. iOS: HouseholdTitle. */
@Composable
fun HouseholdTitle(name: String, households: List<Household>, openId: String, onSwitch: (Household) -> Unit) {
    val title = @Composable { modifier: Modifier ->
        Text(name, modifier, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (households.size < 2) {
        title(Modifier)
        return
    }
    var choosing by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                // Keeps the name where it sits without the menu, with room around it for the ripple.
                .offset(x = (-8).dp)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClickLabel = "Switch household", role = Role.DropdownList) { choosing = true }
                .minimumInteractiveComponentSize()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            title(Modifier.weight(1f, fill = false))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
            households.forEach { household ->
                val open = household.id == openId
                DropdownMenuItem(
                    modifier = Modifier.semantics { selected = open },
                    text = { Text(household.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { if (open) Icon(Icons.Default.Check, contentDescription = null) else Spacer(Modifier.size(24.dp)) },
                    onClick = {
                        choosing = false
                        if (!open) onSwitch(household)
                    },
                )
            }
        }
    }
}

/**
 * A section's title, with its button at the end of the line (or under it, when large text leaves no room). The same
 * height with or without one, so two panes line up.
 */
@Composable
private fun SectionHeader(text: String, top: Dp = 0.dp, action: @Composable () -> Unit = {}) {
    FlowRow(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = top).heightIn(min = 56.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.Center,
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(end = 16.dp))
        action()
    }
}

/** Sits with the lists, not floating over the recipes, so it's clear what it starts. */
@Composable
private fun NewListButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
        Text("New list", Modifier.padding(start = 8.dp))
    }
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
                AnimatedContent(count, label = "past count") { Text("$it", color = MaterialTheme.colorScheme.onSurfaceVariant) }
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
                Box(Modifier.animateItem()) {
                    SwipeAction(
                        icon = Icons.Default.Delete,
                        label = "Delete",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onSwipe = { deletingListId = list.id },
                    ) { ListRow(list) { onOpenList(list.id) } }
                }
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
            // Only while swiping: otherwise it would show through as the row grows into its list.
            if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
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
            // Opening it grows the row into the list.
            .sharedContainer("list-${list.id}", MaterialTheme.shapes.large)
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
private fun RecipesHeader(hasRecipes: Boolean, onAsk: () -> Unit, onPaste: () -> Unit, onSearch: () -> Unit, top: Dp) {
    Column(Modifier.fillMaxWidth().padding(top = top, bottom = 6.dp)) {
        SectionHeader("Recipes")
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
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
        if (hasRecipes) RecipeSearchBar(onSearch, Modifier.padding(horizontal = 16.dp).padding(top = 4.dp))
    }
}
