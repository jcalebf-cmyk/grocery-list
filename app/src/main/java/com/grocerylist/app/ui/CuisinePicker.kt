package com.grocerylist.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.grocerylist.app.data.CUISINES

/** Toggleable cuisine chips. Once [max] are picked, the rest are disabled. */
@Composable
fun CuisineChips(selected: List<String>, onChange: (List<String>) -> Unit, max: Int? = null) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CUISINES.forEach { cuisine ->
            val isSelected = cuisine in selected
            FilterChip(
                selected = isSelected,
                onClick = {
                    onChange(CUISINES.filter { if (it == cuisine) !isSelected else it in selected })
                },
                enabled = isSelected || max == null || selected.size < max,
                label = { Text(cuisine) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp)) }
                } else {
                    null
                },
            )
        }
    }
}

/** Shown once per household, before the first recipes, so they start out leaning the right way. */
@Composable
fun CuisineSetupScreen(onDone: (List<String>) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(emptyList<String>()) }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("What do you like to eat?", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Pick a few cuisines and your recipe ideas will lean toward them. You can change these in Settings.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        CuisineChips(selected, onChange = { selected = it })
        Button(onClick = { onDone(selected) }, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text("Continue")
        }
        TextButton(onClick = { onDone(emptyList()) }, modifier = Modifier.fillMaxWidth()) { Text("Skip for now") }
    }
}

/**
 * A bottom sheet of cuisine chips with a confirm button, an optional secondary action like "Clear",
 * and an optional [surprise] action that also gets the current picks.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuisineSheet(
    title: String,
    subtitle: String,
    initial: List<String>,
    confirm: String,
    onConfirm: (List<String>) -> Unit,
    onDismiss: () -> Unit,
    max: Int? = null,
    requireSelection: Boolean = false,
    secondary: Pair<String, () -> Unit>? = null,
    surprise: Pair<String, (List<String>) -> Unit>? = null,
) {
    var selected by rememberSaveable { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CuisineChips(selected, onChange = { selected = it }, max = max)
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                secondary?.let { (label, action) -> TextButton(onClick = action) { Text(label) } }
                Spacer(Modifier.weight(1f))
                Button(onClick = { onConfirm(selected) }, enabled = !requireSelection || selected.isNotEmpty()) {
                    Text(confirm)
                }
            }
            surprise?.let { (label, action) ->
                OutlinedButton(onClick = { action(selected) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(AppIcons.Shuffle, contentDescription = null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(label)
                }
            }
        }
    }
}
