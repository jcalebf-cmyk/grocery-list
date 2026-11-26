package com.grocerylist.app.ui.recipe

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.grocerylist.app.ui.sharedContainer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.RecipeSource
import com.grocerylist.app.data.recipeShareText
import com.grocerylist.app.data.scaleQuantity
import com.grocerylist.app.data.servingsFactor
import com.grocerylist.app.ui.AppIcons
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.grocerylist.app.ui.Session
import com.grocerylist.app.ui.StorePicker
import com.grocerylist.app.ui.shareText
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.unit.Dp
import com.grocerylist.app.ui.TwoPanes
import com.grocerylist.app.ui.readable
import com.grocerylist.app.ui.rememberWindowLayout

/** A saved recipe from the household's recipe box. */
@Composable
fun RecipeScreen(
    session: Session.Ready,
    recipeId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenList: (String) -> Unit,
) {
    val viewModel: RecipeViewModel = viewModel(key = "recipe-$recipeId") {
        RecipeViewModel(session.householdId, recipeId, session.user.uid)
    }
    val recipe by viewModel.recipe.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf(false) }

    RecipeDetail(
        recipe = recipe,
        viewModel = viewModel,
        onBack = onBack,
        onOpenList = onOpenList,
        modifier = Modifier.sharedContainer("recipe-$recipeId", CardDefaults.shape),
        actions = {
            ShareRecipeButton(recipe)
            IconButton(onClick = onEdit, enabled = recipe != null) {
                Icon(Icons.Default.Edit, contentDescription = "Edit recipe")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete recipe", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuOpen = false
                            deleting = true
                        },
                    )
                }
            }
        },
    )

    if (deleting) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete this recipe?") },
            text = { Text("It's removed from your household's recipes for everyone.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    viewModel.delete(recipeId)
                    onBack()
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

/**
 * A recipe card in full: ingredients to tick off and add to a list, then the steps. Used for saved recipes
 * and for ones the chat suggests before they're saved; [actions] go in the top bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeDetail(
    recipe: Recipe?,
    viewModel: RecipeViewModel,
    onBack: () -> Unit,
    onOpenList: (String) -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    val shopping by viewModel.shopping.collectAsStateWithLifecycle()
    // Nothing starts ticked: the shopper ticks what they need, then adds those to a list.
    // Saveable so ticks survive unfolding the phone, which rebuilds the screen.
    var checked by rememberSaveable(recipe?.ingredients, stateSaver = IndexSetSaver) { mutableStateOf(emptySet<Int>()) }
    var pickingStore by rememberSaveable { mutableStateOf(false) }
    var showingOriginal by rememberSaveable { mutableStateOf(false) }
    // How many people to cook for; amounts scale from what the recipe serves.
    var people by rememberSaveable(recipe?.id, recipe?.servings) { mutableIntStateOf(recipe?.servings ?: 0) }
    val factor = servingsFactor(recipe?.servings ?: 0, people)
    // Which way the amounts roll when the servings change.
    var growing by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    // Past the title, the top bar shows it so you still know which recipe you're on.
    val pastTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val haptics = LocalHapticFeedback.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    AnimatedVisibility(pastTitle, enter = fadeIn(), exit = fadeOut()) {
                        Text(recipe?.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = { actions() },
            )
        },
    ) { padding ->
        val layout = rememberWindowLayout()
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (recipe != null) {
                val ingredients: LazyListScope.() -> Unit = {
                    item(key = "title") { RecipeTitle(recipe, people, onPeopleChange = { growing = it > people; people = it }) }
                    if (recipe.ingredients.isNotEmpty()) {
                        item {
                            // Sits with the ingredients rather than pinned to the screen, so it's there when
                            // you're shopping and out of the way while you read or cook.
                            Row(
                                Modifier.fillMaxWidth().padding(end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.weight(1f)) { SectionTitle("Ingredients") }
                                FilledTonalButton(
                                    onClick = { pickingStore = true },
                                    enabled = checked.isNotEmpty(),
                                    modifier = Modifier.padding(top = 16.dp).animateContentSize(),
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = null, Modifier.size(18.dp))
                                    Text(if (checked.isEmpty()) "Add to list" else "Add ${checked.size} to list", Modifier.padding(start = 6.dp))
                                }
                            }
                        }
                    }
                    itemsIndexed(recipe.ingredients) { i, ingredient ->
                        val isChecked = i in checked
                        val toggle = {
                            haptics.performHapticFeedback(if (isChecked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                            checked = if (isChecked) checked - i else checked + i
                        }
                        ListItem(
                            modifier = Modifier.clickable(onClick = toggle),
                            leadingContent = { Checkbox(checked = isChecked, onCheckedChange = { toggle() }) },
                            headlineContent = { Text(ingredient.name.replaceFirstChar(Char::uppercase)) },
                            trailingContent = {
                                // A long amount ("2 oz chopped, drained (half a can)") wraps instead of squeezing the name.
                                if (ingredient.quantity.isNotBlank()) {
                                    // Changing the servings rolls each amount over to its new value.
                                    AnimatedContent(
                                        scaleQuantity(ingredient.quantity, factor),
                                        transitionSpec = { rollTransition(up = growing) },
                                        contentAlignment = Alignment.CenterEnd,
                                        label = "amount",
                                    ) { amount ->
                                        Text(amount, textAlign = TextAlign.End, modifier = Modifier.widthIn(max = 128.dp))
                                    }
                                }
                            },
                        )
                    }
                }
                val steps: LazyListScope.() -> Unit = {
                    if (recipe.steps.isNotEmpty()) {
                        item { SectionTitle("Steps", top = if (layout.twoPane) 8.dp else 20.dp) }
                        if (factor != 1.0) {
                            item {
                                Text(
                                    "Amounts in the steps are for ${recipe.servings}.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                    itemsIndexed(recipe.steps) { i, step -> StepRow(i + 1, step) }
                    if (recipe.notes.isNotBlank()) item { NotesCard(recipe.notes) }
                    if (recipe.originalText.isNotBlank()) {
                        item {
                            TextButton(onClick = { showingOriginal = !showingOriginal }, modifier = Modifier.padding(horizontal = 4.dp)) {
                                Text(if (showingOriginal) "Hide what was pasted" else "Show what was pasted")
                            }
                        }
                        if (showingOriginal) {
                            item {
                                Text(
                                    recipe.originalText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                        }
                    }
                }
                val bottom = PaddingValues(bottom = 32.dp)
                if (layout.twoPane) {
                    // Unfolded: shop from the ingredients on one side, cook from the steps on the other.
                    TwoPanes(
                        layout,
                        first = { LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = bottom) { ingredients() } },
                    ) {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = bottom) { steps() }
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = bottom.readable()) {
                        ingredients()
                        steps()
                    }
                }
            }
        }
    }

    if (pickingStore && recipe != null) {
        val chosen = { recipe.ingredients.filterIndexed { i, _ -> i in checked } }
        StorePicker(
            stores = shopping.yourStores,
            title = "Add to which list?",
            onPick = { store ->
                pickingStore = false
                onOpenList(viewModel.addToList(store, chosen()))
            },
            onPickNew = { name ->
                pickingStore = false
                onOpenList(viewModel.addToNewStore(name, chosen()))
            },
            onSkip = {
                pickingStore = false
                onOpenList(viewModel.addToList(null, chosen()))
            },
            openStoreIds = shopping.activeLists.mapTo(HashSet()) { it.storeId },
            onDismiss = { pickingStore = false },
        )
    }
}

/** Sends the recipe as text (Messages, email, …). */
@Composable
fun ShareRecipeButton(recipe: Recipe?) {
    val context = LocalContext.current
    IconButton(onClick = { recipe?.let { shareText(context, it.title, recipeShareText(it)) } }, enabled = recipe != null) {
        Icon(Icons.Default.Share, contentDescription = "Share recipe")
    }
}

/** "45 min · Serves 4 · 8 ingredients", leaving out what the recipe doesn't say. */
fun recipeSummary(recipe: Recipe): String = listOfNotNull(
    recipe.totalMinutes.takeIf { it > 0 }?.let { "$it min" },
    recipe.servings.takeIf { it > 0 }?.let { "Serves $it" },
    recipe.ingredients.size.takeIf { it > 0 }?.let { if (it == 1) "1 ingredient" else "$it ingredients" },
).joinToString(" · ")

/** Where the recipe came from, when it matters. Same wording as `sourceNote` in RecipeView.swift. */
private fun sourceNote(recipe: Recipe): String? {
    val channel = recipe.sourceName.takeIf { it.isNotBlank() }?.let { " by $it" }.orEmpty()
    return when {
        recipe.source == RecipeSource.YOUTUBE -> "From the creator's notes on a video$channel"
        recipe.source == RecipeSource.AI && recipe.sourceUrl.isNotBlank() ->
            "The video$channel didn't come with the recipe written out, so AI wrote this one for the same dish. " +
                "It may differ from the video."
        recipe.source == RecipeSource.AI && recipe.id.isNotEmpty() -> "Suggested by AI"
        else -> null
    }
}

@Composable
private fun RecipeTitle(recipe: Recipe, people: Int, onPeopleChange: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(recipe.title, style = MaterialTheme.typography.headlineMedium)
        if (recipe.description.isNotBlank()) Text(recipe.description, style = MaterialTheme.typography.bodyLarge)
        // The servings control below says how many it serves, so the summary leaves that out.
        val summary = recipeSummary(recipe.copy(servings = 0))
        if (summary.isNotEmpty()) {
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (recipe.servings > 0) ServingsControl(people, onPeopleChange)
        sourceNote(recipe)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (recipe.sourceUrl.isNotBlank()) {
            val context = LocalContext.current
            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, recipe.sourceUrl.toUri())) }) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, Modifier.size(18.dp))
                Text("Watch the video", Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** "Serves  − 4 +": scales the ingredient amounts. */
@Composable
private fun ServingsControl(people: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Serves", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 4.dp))
        IconButton(onClick = { onChange(people - 1) }, enabled = people > 1) {
            Icon(AppIcons.Minus, contentDescription = "Fewer servings")
        }
        AnimatedContent(
            people,
            transitionSpec = { rollTransition(up = targetState > initialState) },
            contentAlignment = Alignment.Center,
            label = "servings",
        ) { count ->
            Text("$count", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "Serves $count" })
        }
        IconButton(onClick = { onChange(people + 1) }, enabled = people < MAX_PEOPLE) {
            Icon(Icons.Default.Add, contentDescription = "More servings")
        }
    }
}

private const val MAX_PEOPLE = 24

/** A number rolling over like an odometer: up when it grows, down when it shrinks. */
private fun rollTransition(up: Boolean): ContentTransform {
    val dir = if (up) 1 else -1
    return ContentTransform(
        targetContentEnter = slideInVertically(spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)) { dir * it } +
            fadeIn(tween(150)),
        initialContentExit = slideOutVertically(tween(150)) { -dir * it } + fadeOut(tween(100)),
        sizeTransform = SizeTransform(clip = false),
    )
}

@Composable
private fun StepRow(number: Int, step: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text("$number", style = MaterialTheme.typography.labelLarge) }
        }
        Text(step, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
private fun NotesCard(notes: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Notes", style = MaterialTheme.typography.titleSmall)
            Text(notes, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SectionTitle(text: String, top: Dp = 20.dp) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = top, bottom = 4.dp))
}

/** Which ingredients are ticked, by position. */
private val IndexSetSaver = Saver<Set<Int>, ArrayList<Int>>(save = { ArrayList(it) }, restore = { it.toSet() })
