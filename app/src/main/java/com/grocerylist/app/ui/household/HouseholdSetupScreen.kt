package com.grocerylist.app.ui.household

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import com.grocerylist.app.ui.AppIcons
import com.grocerylist.app.ui.onboarding.Onboarding
import com.grocerylist.app.ui.onboarding.OnboardingStart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HouseholdSetupViewModel : ViewModel() {
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun create(uid: String, name: String) = run(OnboardingStart.CREATED) { AppGraph.households.createHousehold(uid, name) }

    fun join(uid: String, code: String) = run(OnboardingStart.JOINED) {
        if (!AppGraph.households.joinHousehold(uid, code)) error.value = "No household uses that code. Check it and try again."
    }

    // Marks onboarding first: on success the session moves straight on to Home, which shows the rest of it.
    private fun run(start: OnboardingStart, block: suspend () -> Unit) {
        busy.value = true
        error.value = null
        Onboarding.pending.value = start
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                error.value = e.localizedMessage ?: "Something went wrong. Try again."
            } finally {
                if (error.value != null) Onboarding.pending.value = null
                busy.value = false
            }
        }
    }
}

@Composable
fun HouseholdSetupScreen(
    user: UserProfile,
    onSignOut: () -> Unit,
    viewModel: HouseholdSetupViewModel = viewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var householdName by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .padding(top = 24.dp)
                .size(52.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Group, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Text("Welcome, ${user.displayName}", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Groceries keeps one shared list for your whole household, updated live as anyone adds or checks off " +
                "items. Start a household, or join one with an invite code.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Start a household", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = householdName,
                    onValueChange = { householdName = it },
                    label = { Text("Household name") },
                    placeholder = { Text("The Smiths") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { viewModel.create(user.uid, householdName) },
                    enabled = !busy && householdName.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Create household") }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Have an invite code?", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(6) },
                    label = { Text("6-character invite code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { viewModel.join(user.uid, code) },
                    enabled = !busy && code.length == 6,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Join household") }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text("Sign out")
        }
    }
}
