package com.grocerylist.app.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import com.grocerylist.app.data.MAX_NOTE_LENGTH

/** Shared by the list's menu and swiping a past list on Home. */
@Composable
fun DeleteListDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete this list?") },
        text = { Text("It's removed for everyone in your household. Your suggestions won't change.") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** From long-pressing an item on an active list, mostly to fix typos. */
@Composable
fun EditItemDialog(name: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(name, selection = TextRange(name.length)))
    }
    val focus = remember { FocusRequester() }
    val save = { if (text.text.isNotBlank()) onSave(text.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit name") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = save, enabled = text.text.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** From long-pressing an item on an active list. Saving it blank removes the note. */
@Composable
fun ItemNoteDialog(itemName: String, note: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(note, selection = TextRange(note.length)))
    }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note for $itemName", maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.copy(text = it.text.take(MAX_NOTE_LENGTH)) },
                placeholder = { Text("The low-sodium one") },
                maxLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onSave(text.text) }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text.text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) { focus.requestFocus() }
}

/** Shared by the list's menu and swiping an active list on Home. */
@Composable
fun CloseOutListDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Close out this list?") },
        text = { Text("It moves to Past lists. You can reopen it anytime.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Close out") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
