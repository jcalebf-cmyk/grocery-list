package com.grocerylist.app.ui.recipe

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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import android.content.Intent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.data.Recipe
import com.grocerylist.app.data.RecipeSource
import com.grocerylist.app.data.scaleQuantity
import com.grocerylist.app.data.servingsFactor
import com.grocerylist.app.ui.AppIcons
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.grocerylist.app.ui.Session
import com.grocerylist.app.ui.StorePicker

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
    var deleting by remember { mutableStateOf(false) }

    RecipeDetail(
        recipe = recipe,
        viewModel = viewModel,
        onBack = onBack,
        onOpenList = onOpenList,
        actions = {
            IconButton(onClick = onEdit, enabled = recipe != null) {
                Icon(Icons.Default.Edit, contentDescription = "Edit recipe")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = "More") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Delete recipe") },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
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
                }) { Text("Delete") }
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
    actions: @Composable () -> Unit = {},
) {
    val shopping by viewModel.shopping.collectAsStateWithLifecycle()
    // Everything starts checked except what's on hand; the shopper unticks anything else they have.
    val onHandIndices = remember(recipe, shopping.onHandNames) { recipe?.let(shopping::onHandIndices).orEmpty() }
    var unchecked by remember(recipe?.ingredients, onHandIndices) { mutableStateOf(onHandIndices) }
    var pickingStore by rememberSaveable { mutableStateOf(false) }
    var showingOriginal by rememberSaveable { mutableStateOf(false) }
    // How many people to cook for; amounts scale from what the recipe serves.
    var people by rememberSaveable(recipe?.id, recipe?.servings) { mutableIntStateOf(recipe?.servings ?: 0) }
    val factor = servingsFactor(recipe?.servings ?: 0, people)
    val listState = rememberLazyListState()
    // Past the title, the top bar shows it so you still know which recipe you're on.
    val pastTitle by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    Scaffold(
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (recipe != null) {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 112.dp)) {
                    item(key = "title") { RecipeTitle(recipe, people, onPeopleChange = { people = it }) }
                    if (recipe.ingredients.isNotEmpty()) item { SectionTitle("Ingredients") }
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
                            trailingContent = {
                                if (ingredient.quantity.isNotBlank()) Text(scaleQuantity(ingredient.quantity, factor))
                            },
                        )
                    }
                    if (recipe.steps.isNotEmpty()) {
                        item { SectionTitle("Steps") }
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

                val count = recipe.ingredients.size - unchecked.size
                if (recipe.ingredients.isNotEmpty()) {
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
        }
    }

    if (pickingStore && recipe != null) {
        val chosen = { recipe.ingredients.filterIndexed { i, _ -> i !in unchecked } }
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
        Text("$people", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = "Serves $people" })
        IconButton(onClick = { onChange(people + 1) }, enabled = people < MAX_PEOPLE) {
            Icon(Icons.Default.Add, contentDescription = "More servings")
        }
    }
}

private const val MAX_PEOPLE = 24

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
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
}
