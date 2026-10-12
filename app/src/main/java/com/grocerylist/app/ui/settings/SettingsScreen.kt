package com.grocerylist.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.Household
import com.grocerylist.app.data.UserProfile
import com.grocerylist.app.ui.MemberBadge
import com.grocerylist.app.ui.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModel(private val householdId: String, private val uid: String) : ViewModel() {
    private val households = AppGraph.households

    val household = households.observeHousehold(householdId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val members = households.observeHousehold(householdId)
        .flatMapLatest { households.observeMembers(it?.memberIds.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val errors = Channel<String>(Channel.BUFFERED)
    val errorMessages = errors.receiveAsFlow()

    fun renameHousehold(name: String) = run("Couldn't rename the household") {
        households.renameHousehold(householdId, name)
    }

    fun updateDisplayName(name: String) = run("Couldn't update your name") {
        households.updateDisplayName(uid, name)
        AppGraph.auth.updateDisplayName(name)
    }

    fun removeMember(member: UserProfile) = run("Couldn't remove ${member.displayName}") {
        households.removeMember(householdId, member.uid)
    }

    // On success the session moves to household setup, so there's nothing else to do here.
    fun leave() = run("Couldn't leave the household") { households.leaveHousehold(householdId, uid) }

    private fun run(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: Exception) {
                errors.send(failure)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(session: Session.Ready, onBack: () -> Unit, onSignOut: () -> Unit) {
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(session.householdId, session.user.uid) }
    val household by viewModel.household.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val me = members.firstOrNull { it.uid == session.user.uid } ?: session.user
    val isOwner = household?.ownerId == session.user.uid
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.errorMessages.collect { snackbar.showSnackbar(it) } }

    var editingName by rememberSaveable { mutableStateOf(false) }
    var renamingHousehold by rememberSaveable { mutableStateOf(false) }
    var confirmingLeave by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<UserProfile?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item { SectionTitle("Account") }
            item {
                SettingsRow(
                    title = me.displayName,
                    subtitle = me.email,
                    leading = { MemberBadge(me.displayName) },
                    trailing = { Icon(Icons.Default.Edit, contentDescription = "Edit your name") },
                    onClick = { editingName = true },
                )
            }
            item {
                SettingsRow(
                    title = "Sign out",
                    leading = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                    onClick = onSignOut,
                )
            }

            item { SectionTitle("Household") }
            household?.let { h ->
                item {
                    SettingsRow(
                        title = h.name,
                        subtitle = "Household name",
                        trailing = { Icon(Icons.Default.Edit, contentDescription = "Rename household") },
                        onClick = { renamingHousehold = true },
                    )
                }
                item { InviteCard(h) }
            }
            item {
                Text(
                    "Members",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            items(members, key = { it.uid }) { member ->
                val isMe = member.uid == session.user.uid
                SettingsRow(
                    title = if (isMe) "${member.displayName} (you)" else member.displayName,
                    subtitle = if (member.uid == household?.ownerId) "Owner · ${member.email}" else member.email,
                    leading = { MemberBadge(member.displayName) },
                    trailing = if (isOwner && !isMe) {
                        {
                            IconButton(onClick = { removing = member }) {
                                Icon(Icons.Default.Close, contentDescription = "Remove ${member.displayName}")
                            }
                        }
                    } else {
                        null
                    },
                )
            }
            item {
                TextButton(
                    onClick = { confirmingLeave = true },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
                ) { Text("Leave household", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (editingName) {
        TextInputDialog(
            title = "Your name",
            initial = me.displayName,
            confirm = "Save",
            onConfirm = {
                editingName = false
                viewModel.updateDisplayName(it)
            },
            onDismiss = { editingName = false },
        )
    }
    if (renamingHousehold) {
        TextInputDialog(
            title = "Household name",
            initial = household?.name.orEmpty(),
            confirm = "Rename",
            onConfirm = {
                renamingHousehold = false
                viewModel.renameHousehold(it)
            },
            onDismiss = { renamingHousehold = false },
        )
    }
    removing?.let { member ->
        ConfirmDialog(
            title = "Remove ${member.displayName}?",
            text = "They'll lose access to your lists. They can rejoin with the invite code.",
            confirm = "Remove",
            onConfirm = {
                removing = null
                viewModel.removeMember(member)
            },
            onDismiss = { removing = null },
        )
    }
    if (confirmingLeave) {
        val alone = members.size <= 1
        ConfirmDialog(
            title = "Leave ${household?.name ?: "household"}?",
            text = if (alone) {
                "You're the only member, so its lists and recipes will no longer be reachable by anyone."
            } else {
                "You'll lose access to its lists. You can rejoin with the invite code."
            },
            confirm = "Leave",
            onConfirm = {
                confirmingLeave = false
                viewModel.leave()
            },
            onDismiss = { confirmingLeave = false },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(MaterialTheme.shapes.large)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        leadingContent = leading,
        headlineContent = { Text(title) },
        supportingContent = subtitle?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        trailingContent = trailing,
    )
}

@Composable
private fun InviteCard(household: Household) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Invite code", style = MaterialTheme.typography.labelLarge)
            Text(household.inviteCode, style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
            Text(
                "Anyone with this code can join and edit your lists.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FilledTonalButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(
                        Intent.EXTRA_TEXT,
                        "Join our grocery list household \"${household.name}\" with code ${household.inviteCode}",
                    )
                }
                context.startActivity(Intent.createChooser(send, "Share invite code"))
            }) {
                Icon(Icons.Default.Share, contentDescription = null)
                Text("Share", Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank() && value.trim() != initial) {
                Text(confirm)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
