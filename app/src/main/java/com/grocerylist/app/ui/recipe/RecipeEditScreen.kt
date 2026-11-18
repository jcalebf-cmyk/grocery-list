package com.grocerylist.app.ui.recipe

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.ui.AppIcons
import com.grocerylist.app.ui.Session

/**
 * Paste a recipe ([recipeId] null) or edit a saved one. A pasted recipe is read by the server, then shown
 * here to check before saving. [onSaved] gets the recipe's ID. iOS: RecipeEditView.swift.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeEditScreen(session: Session.Ready, recipeId: String?, onClose: () -> Unit, onSaved: (String) -> Unit) {
    val viewModel: RecipeEditViewModel = viewModel(key = "edit-${recipeId ?: "new"}") {
        RecipeEditViewModel(session.householdId, recipeId, session.user.uid)
    }
    var confirmingDiscard by rememberSaveable { mutableStateOf(false) }
    val close = { if (viewModel.hasChanges) confirmingDiscard = true else onClose() }
    BackHandler(onBack = close)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            recipeId != null -> "Edit recipe"
                            viewModel.phase == RecipeEditViewModel.Phase.EDIT -> "Check the recipe"
                            else -> "Paste a recipe"
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = close) { Icon(Icons.Default.Close, contentDescription = "Close") } },
                actions = {
                    if (viewModel.phase == RecipeEditViewModel.Phase.EDIT) {
                        TextButton(
                            onClick = { viewModel.save()?.let(onSaved) },
                            enabled = viewModel.canSave,
                            modifier = Modifier.padding(end = 4.dp),
                        ) { Text("Save") }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (viewModel.phase) {
                RecipeEditViewModel.Phase.PASTE, RecipeEditViewModel.Phase.READING -> PasteStep(viewModel)
                RecipeEditViewModel.Phase.EDIT -> if (viewModel.loaded) {
                    Editor(viewModel)
                } else {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
            }
        }
    }

    if (confirmingDiscard) {
        AlertDialog(
            onDismissRequest = { confirmingDiscard = false },
            title = { Text(if (recipeId == null) "Discard this recipe?" else "Discard your changes?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmingDiscard = false
                    onClose()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmingDiscard = false }) { Text("Keep editing") } },
        )
    }
}

@Composable
private fun PasteStep(viewModel: RecipeEditViewModel) {
    val context = LocalContext.current
    val reading = viewModel.phase == RecipeEditViewModel.Phase.READING
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Copy a recipe from a website, a message, or a note and paste it here. It's sorted into ingredients " +
                "and steps, keeping the wording as written. You can paste a YouTube link to a cooking video, too.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = viewModel.pasted,
            onValueChange = { viewModel.pasted = it },
            placeholder = { Text("Paste a recipe or a YouTube link") },
            enabled = !reading,
            minLines = 10,
            modifier = Modifier.fillMaxWidth(),
        )
        if (viewModel.pasted.isEmpty()) {
            FilledTonalButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.let {
                    viewModel.pasted = it.toString()
                }
            }) {
                Icon(AppIcons.Paste, contentDescription = null, Modifier.size(18.dp))
                Text("Paste from clipboard", Modifier.padding(start = 8.dp))
            }
        }
        viewModel.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            onClick = viewModel::read,
            enabled = !reading && viewModel.pasted.trim().length >= RecipeEditViewModel.MIN_PASTE,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            if (reading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Text("Reading the recipe…", Modifier.padding(start = 10.dp))
            } else {
                Text("Read recipe")
            }
        }
    }
}

@Composable
private fun Editor(viewModel: RecipeEditViewModel) {
    val sentences = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    val numbers = KeyboardOptions(keyboardType = KeyboardType.Number)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            OutlinedTextField(
                value = viewModel.title,
                onValueChange = { viewModel.title = it },
                label = { Text("Title") },
                singleLine = true,
                isError = viewModel.title.isBlank(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = viewModel.description,
                onValueChange = { viewModel.description = it },
                label = { Text("Description (optional)") },
                keyboardOptions = sentences,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = viewModel.servings,
                    onValueChange = { viewModel.servings = it.filter(Char::isDigit).take(3) },
                    label = { Text("Serves") },
                    singleLine = true,
                    keyboardOptions = numbers,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = viewModel.minutes,
                    onValueChange = { viewModel.minutes = it.filter(Char::isDigit).take(4) },
                    label = { Text("Minutes") },
                    singleLine = true,
                    keyboardOptions = numbers,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item { EditorHeading("Ingredients") }
        itemsIndexed(viewModel.ingredients, key = { _, it -> "i${it.key}" }) { _, ingredient ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = ingredient.quantity,
                    onValueChange = { ingredient.quantity = it },
                    placeholder = { Text("Amount") },
                    singleLine = true,
                    modifier = Modifier.weight(0.4f),
                )
                OutlinedTextField(
                    value = ingredient.name,
                    onValueChange = { ingredient.name = it },
                    placeholder = { Text("Ingredient") },
                    singleLine = true,
                    keyboardOptions = sentences,
                    modifier = Modifier.weight(0.6f),
                )
                IconButton(onClick = { viewModel.ingredients.remove(ingredient) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove ${ingredient.name}")
                }
            }
        }
        item { AddRowButton("Add ingredient", viewModel::addIngredient) }

        item { EditorHeading("Steps") }
        itemsIndexed(viewModel.steps, key = { _, it -> "s${it.key}" }) { i, step ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = step.text,
                    onValueChange = { step.text = it },
                    label = { Text("Step ${i + 1}") },
                    keyboardOptions = sentences,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.steps.remove(step) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove step ${i + 1}")
                }
            }
        }
        item { AddRowButton("Add step", viewModel::addStep) }

        item { EditorHeading("Notes") }
        item {
            OutlinedTextField(
                value = viewModel.notes,
                onValueChange = { viewModel.notes = it },
                placeholder = { Text("Tips, swaps, what you'd change next time") },
                minLines = 3,
                keyboardOptions = sentences,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun EditorHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
}

@Composable
private fun AddRowButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
        Text(label, Modifier.padding(start = 6.dp))
    }
}
