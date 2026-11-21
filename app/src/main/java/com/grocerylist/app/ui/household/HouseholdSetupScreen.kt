package com.grocerylist.app.ui.household

import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import com.grocerylist.app.ui.ReadableWidth
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.UserProfile
import com.grocerylist.app.ui.AppIcons
import com.grocerylist.app.ui.onboarding.Onboarding
import com.grocerylist.app.ui.onboarding.OnboardingStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class HouseholdSetupViewModel : ViewModel() {
    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun create(uid: String, name: String) = run(OnboardingStart.CREATED) { AppGraph.households.createHousehold(uid, name) }

    fun join(uid: String, code: String) = run(OnboardingStart.JOINED) {
        if (!AppGraph.households.joinHousehold(uid, code)) error.value = "No household uses that code. Check it and try again."
    }

    fun clearError() {
        error.value = null
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

/**
 * A starting household name from someone's name: "Josh Franzen" → "Franzen household". Blank for a
 * one-word name (often an email's first half), which leaves the field empty with its example.
 * iOS: suggestedHouseholdName in Logic.swift.
 */
fun suggestedHouseholdName(displayName: String): String {
    val words = displayName.trim().split(Regex("\\s+"))
    return if (words.size >= 2) "${words.last()} household" else ""
}

private enum class SetupStep { CHOOSE, JOIN, START }

/**
 * Start a household or join one with an invite code: the first onboarding step. It asks which first,
 * then shows just that one field, so nobody starts a second household by mistake. The rest
 * (OnboardingScreen) follows on Home once the household is set.
 * iOS: HouseholdSetupView in SignInView.swift.
 */
@Composable
fun HouseholdSetupScreen(
    user: UserProfile,
    onSignOut: () -> Unit,
    viewModel: HouseholdSetupViewModel = viewModel(),
) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var step by rememberSaveable { mutableStateOf(SetupStep.CHOOSE) }

    fun goTo(next: SetupStep) {
        viewModel.clearError()
        step = next
    }

    BackHandler(enabled = step != SetupStep.CHOOSE && !busy) { goTo(SetupStep.CHOOSE) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().wrapContentWidth().widthIn(max = ReadableWidth)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            val canGoBack = step != SetupStep.CHOOSE
            IconButton(
                onClick = { goTo(SetupStep.CHOOSE) },
                enabled = canGoBack && !busy,
                modifier = Modifier.alpha(if (canGoBack) 1f else 0f),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState != SetupStep.CHOOSE
                slideInHorizontally { if (forward) it else -it } togetherWith slideOutHorizontally { if (forward) -it else it }
            },
            modifier = Modifier.weight(1f),
            label = "household setup step",
        ) { shown ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (shown) {
                    SetupStep.CHOOSE -> ChooseStep(user, onJoin = { goTo(SetupStep.JOIN) }, onStart = { goTo(SetupStep.START) }, onSignOut)
                    SetupStep.JOIN -> JoinStep(busy, error, onJoin = { viewModel.join(user.uid, it) })
                    SetupStep.START -> StartStep(user, busy, error, onCreate = { viewModel.create(user.uid, it) })
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Intro(icon: ImageVector, title: String, text: String) {
    Box(
        Modifier
            .size(52.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
    }
    Text(title, style = MaterialTheme.typography.headlineMedium)
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ColumnScope.ChooseStep(user: UserProfile, onJoin: () -> Unit, onStart: () -> Unit, onSignOut: () -> Unit) {
    Intro(
        AppIcons.Group,
        "Welcome, ${user.displayName}",
        "Groceries keeps one shared list for your whole household, updated live as anyone adds or checks off items.",
    )
    Text(
        "Is someone in your household already using Groceries?",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
    ChoiceCard(AppIcons.Group, "Join my household", "Yes, someone sent me an invite code", onJoin)
    ChoiceCard(Icons.Default.Home, "Start a new household", "No, I'm the first one here", onStart)
    TextButton(onClick = onSignOut, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
        Text("Sign out")
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColumnScope.JoinStep(busy: Boolean, error: String?, onJoin: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canJoin = !busy && code.length == 6

    Intro(
        AppIcons.Group,
        "Join your household",
        "Ask anyone already in your household for the invite code. They'll find it in Settings.",
    )
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isLetterOrDigit).uppercase().take(6) },
        label = { Text("6-character invite code") },
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { if (canJoin) onJoin(code) }),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus),
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = { onJoin(code) }, enabled = canJoin, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Join household")
    }
}

@Composable
private fun ColumnScope.StartStep(user: UserProfile, busy: Boolean, error: String?, onCreate: (String) -> Unit) {
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val suggested = suggestedHouseholdName(user.displayName)
        mutableStateOf(TextFieldValue(suggested, TextRange(suggested.length)))
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val canCreate = !busy && name.text.isNotBlank()

    Intro(
        Icons.Default.Home,
        "Name your household",
        "Everyone you invite sees this name. You can change it in Settings later.",
    )
    OutlinedTextField(
        value = name,
        onValueChange = { name = it },
        label = { Text("Household name") },
        placeholder = { Text("The Smiths") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (canCreate) onCreate(name.text) }),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus),
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = { onCreate(name.text) }, enabled = canCreate, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Create household")
    }
}
