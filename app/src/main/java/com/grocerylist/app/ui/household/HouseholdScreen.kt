package com.grocerylist.app.ui.household

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.Household
import com.grocerylist.app.data.UserProfile
import com.grocerylist.app.ui.Session
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdViewModel(householdId: String) : ViewModel() {
    val household = AppGraph.households.observeHousehold(householdId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val members = AppGraph.households.observeHousehold(householdId)
        .flatMapLatest { AppGraph.households.observeMembers(it?.memberIds.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HouseholdScreen(session: Session.Ready, onBack: () -> Unit, onSignOut: () -> Unit) {
    val viewModel: HouseholdViewModel = viewModel { HouseholdViewModel(session.householdId) }
    val household by viewModel.household.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(household?.name ?: "Household") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            household?.let { item { InviteCard(it) } }
            item {
                Text(
                    "Members",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 4.dp),
                )
            }
            items(members, key = { it.uid }) { member -> MemberRow(member, isMe = member.uid == session.user.uid) }
            item {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                ) { Text("Sign out") }
            }
        }
    }
}

@Composable
private fun InviteCard(household: Household) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Invite code", style = MaterialTheme.typography.labelLarge)
            Text(
                household.inviteCode,
                style = MaterialTheme.typography.displaySmall,
                fontFamily = FontFamily.Monospace,
            )
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
private fun MemberRow(member: UserProfile, isMe: Boolean) {
    ListItem(
        leadingContent = { Icon(Icons.Default.Person, contentDescription = null) },
        headlineContent = { Text(if (isMe) "${member.displayName} (you)" else member.displayName) },
        supportingContent = { Text(member.email) },
    )
}
