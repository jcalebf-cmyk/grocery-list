package com.grocerylist.app.ui.settings

import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import com.grocerylist.app.ui.CuisineSheet
import com.grocerylist.app.ui.auth.requestGoogleIdToken
import com.grocerylist.app.data.NearbyStores
import com.grocerylist.app.data.AccountDeletion
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.filled.Delete
import android.widget.Toast
import android.util.Log
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
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

    private val recipes = AppGraph.recipes(householdId)
    val cuisines = recipes.observePreferences()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val errors = Channel<String>(Channel.BUFFERED)
    val errorMessages = errors.receiveAsFlow()

    /** Null while checking; checked on every resume, since the link is tapped outside the app. */
    val emailUnconfirmed = MutableStateFlow<Boolean?>(null)
    val confirmationSent = MutableStateFlow(false)

    fun checkEmailConfirmation() {
        viewModelScope.launch { emailUnconfirmed.value = AppGraph.auth.needsEmailConfirmation() }
    }

    fun resendEmailConfirmation() = run("Couldn't send the email. Try again in a few minutes.") {
        AppGraph.auth.sendEmailConfirmation()
        confirmationSent.value = true
    }

    /** Recipes pick up the change when Home is next shown. */
    fun saveCuisines(cuisines: List<String>) = recipes.savePreferences(cuisines)

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

    fun makeOwner(member: UserProfile) = run("Couldn't make ${member.displayName} the owner") {
        household.value?.let { households.transferOwnership(it, member.uid) }
    }

    // On success the session moves to household setup, so there's nothing else to do here.
    fun leave() = run("Couldn't leave the household") { households.leaveHousehold(householdId, uid) }

    /** The owner hands ownership over first; the rules don't let an owner leave directly. */
    fun makeOwnerAndLeave(member: UserProfile) = run("Couldn't leave the household") {
        household.value?.let { households.transferOwnership(it, member.uid) }
        households.leaveHousehold(householdId, uid)
    }

    val deleting = MutableStateFlow(false)
    val deleteError = MutableStateFlow<String?>(null)

    /**
     * Confirms identity with [reauthenticate], then deletes everything. Once identity is confirmed the
     * rest runs in the app-wide scope: deleting changes the session, which closes this screen mid-way.
     */
    fun deleteAccount(context: Context, newOwner: UserProfile?, reauthenticate: suspend () -> Unit) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            deleting.value = true
            deleteError.value = null
            try {
                reauthenticate()
            } catch (_: GetCredentialCancellationException) {
                deleting.value = false
                return@launch
            } catch (e: Exception) {
                deleteError.value = if (e is FirebaseAuthInvalidCredentialsException) {
                    "That password isn't right."
                } else {
                    "Couldn't confirm it's you. Try again."
                }
                deleting.value = false
                return@launch
            }
            AppGraph.appScope.launch {
                try {
                    AccountDeletion.deleteAccount(uid, householdId, newOwner)
                    NearbyStores.clear(appContext)
                } catch (e: Exception) {
                    Log.w("AccountDeletion", "Account deletion failed", e)
                    Toast.makeText(appContext, "Couldn't finish deleting your account. Try again.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

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
    // Members and leaving only matter once someone else has joined; alone, leaving would just strand your lists.
    val shared = members.size > 1
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { viewModel.errorMessages.collect { snackbar.showSnackbar(it) } }

    var editingName by rememberSaveable { mutableStateOf(false) }
    var renamingHousehold by rememberSaveable { mutableStateOf(false) }
    var confirmingLeave by rememberSaveable { mutableStateOf(false) }
    var removing by remember { mutableStateOf<UserProfile?>(null) }
    var promoting by remember { mutableStateOf<UserProfile?>(null) }
    var confirmingDelete by rememberSaveable { mutableStateOf(false) }
    val cuisines by viewModel.cuisines.collectAsStateWithLifecycle()
    var editingCuisines by rememberSaveable { mutableStateOf(false) }
    val emailUnconfirmed by viewModel.emailUnconfirmed.collectAsStateWithLifecycle()
    val confirmationSent by viewModel.confirmationSent.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.checkEmailConfirmation()
        onPauseOrDispose {}
    }

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
            if (emailUnconfirmed == true) {
                item {
                    SettingsRow(
                        title = "Confirm your email",
                        subtitle = "Tap the link we sent to ${me.email} so you can reset your password if you forget it.",
                        leading = { Icon(Icons.Default.Email, contentDescription = null) },
                        trailing = {
                            TextButton(onClick = viewModel::resendEmailConfirmation, enabled = !confirmationSent) {
                                Text(if (confirmationSent) "Sent" else "Resend")
                            }
                        },
                    )
                }
            }
            item {
                SettingsRow(
                    title = "Sign out",
                    leading = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                    onClick = onSignOut,
                )
            }
            item {
                SettingsRow(
                    title = "Delete account",
                    titleColor = MaterialTheme.colorScheme.error,
                    leading = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { confirmingDelete = true },
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
                item { InviteCard(h, shared) }
            }
            if (shared) {
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
                                MemberMenu(
                                    name = member.displayName,
                                    onMakeOwner = { promoting = member },
                                    onRemove = { removing = member },
                                )
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

            item { SectionTitle("Recipes") }
            item {
                SettingsRow(
                    title = "Favorite cuisines",
                    subtitle = cuisines?.cuisines?.joinToString(", ")?.ifEmpty { "Any" },
                    trailing = { Icon(Icons.Default.Edit, contentDescription = "Change favorite cuisines") },
                    onClick = { editingCuisines = true },
                )
            }
        }
    }

    if (editingCuisines) {
        CuisineSheet(
            title = "Favorite cuisines",
            subtitle = "Recipe ideas lean toward these, with a few others mixed in.",
            initial = cuisines?.cuisines.orEmpty(),
            confirm = "Save",
            onConfirm = {
                editingCuisines = false
                viewModel.saveCuisines(it)
            },
            onDismiss = { editingCuisines = false },
        )
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
    if (confirmingDelete) {
        val context = LocalContext.current
        DeleteAccountDialog(
            householdName = household?.name ?: "your household",
            shared = shared,
            isOwner = isOwner,
            candidates = members.filter { it.uid != session.user.uid },
            usesPassword = AppGraph.auth.usesPassword,
            viewModel = viewModel,
            onDeleteWithPassword = { password, newOwner ->
                viewModel.deleteAccount(context, newOwner) { AppGraph.auth.reauthenticateWithPassword(password) }
            },
            onDeleteWithGoogle = { newOwner ->
                viewModel.deleteAccount(context, newOwner) {
                    AppGraph.auth.reauthenticateWithGoogle(requestGoogleIdToken(context))
                }
            },
            onDismiss = {
                confirmingDelete = false
                viewModel.deleteError.value = null
            },
        )
    }
    promoting?.let { member ->
        ConfirmDialog(
            title = "Make ${member.displayName} the owner?",
            text = "${member.displayName} will be able to remove members and hand ownership on. " +
                "You'll stay in the household as a regular member.",
            confirm = "Make owner",
            destructive = false,
            onConfirm = {
                promoting = null
                viewModel.makeOwner(member)
            },
            onDismiss = { promoting = null },
        )
    }
    if (confirmingLeave && shared) {
        if (isOwner) {
            ChooseNewOwnerDialog(
                householdName = household?.name ?: "household",
                candidates = members.filter { it.uid != session.user.uid },
                onConfirm = { newOwner ->
                    confirmingLeave = false
                    viewModel.makeOwnerAndLeave(newOwner)
                },
                onDismiss = { confirmingLeave = false },
            )
        } else {
            ConfirmDialog(
                title = "Leave ${household?.name ?: "household"}?",
                text = "You'll lose access to its lists. You can rejoin with the invite code.",
                confirm = "Leave",
                onConfirm = {
                    confirmingLeave = false
                    viewModel.leave()
                },
                onDismiss = { confirmingLeave = false },
            )
        }
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
    titleColor: Color = Color.Unspecified,
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
        headlineContent = { Text(title, color = titleColor) },
        supportingContent = subtitle?.takeIf { it.isNotBlank() }?.let { { Text(it) } },
        trailingContent = trailing,
    )
}

@Composable
private fun InviteCard(household: Household, shared: Boolean) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                if (shared) "Invite code" else "Share your lists",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(household.inviteCode, style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
            Text(
                if (shared) {
                    "Anyone with this code can join and edit your lists."
                } else {
                    "Send this code to someone you shop with. Once they join, you'll both see and edit the same lists."
                },
                textAlign = TextAlign.Center,
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
private fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MemberMenu(name: String, onMakeOwner: () -> Unit, onRemove: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Options for $name")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Make owner") },
                onClick = {
                    open = false
                    onMakeOwner()
                },
            )
            DropdownMenuItem(
                text = { Text("Remove from household", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    onRemove()
                },
            )
        }
    }
}

/** Owners must hand the household to someone before leaving, so nobody is left without an owner. */
@Composable
private fun ChooseNewOwnerDialog(
    householdName: String,
    candidates: List<UserProfile>,
    onConfirm: (UserProfile) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(candidates.singleOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave $householdName?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("You're the owner. Choose who takes over before you go.")
                candidates.forEach { member ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { chosen = member }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = chosen?.uid == member.uid, onClick = { chosen = member })
                        Text(member.displayName)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { chosen?.let(onConfirm) }, enabled = chosen != null) {
                Text("Make owner & leave", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Explains what deletion removes, picks a new owner if needed, and confirms identity
 * (password or Google) as the final step, since Firebase requires a fresh sign-in to delete.
 */
@Composable
private fun DeleteAccountDialog(
    householdName: String,
    shared: Boolean,
    isOwner: Boolean,
    candidates: List<UserProfile>,
    usesPassword: Boolean,
    viewModel: SettingsViewModel,
    onDeleteWithPassword: (String, UserProfile?) -> Unit,
    onDeleteWithGoogle: (UserProfile?) -> Unit,
    onDismiss: () -> Unit,
) {
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val error by viewModel.deleteError.collectAsStateWithLifecycle()
    var password by rememberSaveable { mutableStateOf("") }
    val needsNewOwner = shared && isOwner
    var newOwner by remember { mutableStateOf(if (needsNewOwner) candidates.singleOrNull() else null) }
    val ready = !deleting && (!needsNewOwner || newOwner != null) && (!usesPassword || password.isNotEmpty())

    AlertDialog(
        onDismissRequest = { if (!deleting) onDismiss() },
        title = { Text("Delete your account?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (shared) {
                        "This permanently deletes your account and removes you from $householdName. " +
                            "Its lists stay with the other members."
                    } else {
                        "This permanently deletes your account and $householdName, including all of its " +
                            "lists, stores, kitchen items, and recipes. This can't be undone."
                    },
                )
                if (needsNewOwner) {
                    Text("You're the owner. Choose who takes over:", style = MaterialTheme.typography.titleSmall)
                    candidates.forEach { member ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { newOwner = member },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = newOwner?.uid == member.uid, onClick = { newOwner = member })
                            Text(member.displayName)
                        }
                    }
                }
                if (usesPassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Your password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = error != null,
                        enabled = !deleting,
                    )
                } else {
                    Text("You'll confirm with Google before anything is deleted.", style = MaterialTheme.typography.bodySmall)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (usesPassword) onDeleteWithPassword(password, newOwner) else onDeleteWithGoogle(newOwner) },
                enabled = ready,
            ) {
                Text(
                    when {
                        deleting -> "Deleting…"
                        usesPassword -> "Delete account"
                        else -> "Confirm with Google & delete"
                    },
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !deleting) { Text("Cancel") } },
    )
}
