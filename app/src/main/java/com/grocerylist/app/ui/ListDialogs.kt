package com.grocerylist.app.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

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
