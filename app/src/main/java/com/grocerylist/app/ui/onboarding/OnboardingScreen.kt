package com.grocerylist.app.ui.onboarding

import android.content.Intent
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.AppGraph
import com.grocerylist.app.data.itemKey
import com.grocerylist.app.data.kitchenQuickAdds
import com.grocerylist.app.data.pantryQuickAdds
import com.grocerylist.app.ui.AppIcons
import com.grocerylist.app.ui.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/** How someone got their household. Decides whether onboarding starts with the invite step. */
enum class OnboardingStart { CREATED, JOINED }

/**
 * Set when someone starts or joins a household, so Home walks them through the app first. Kept in
 * memory: if the app closes partway, the walkthrough doesn't come back.
 */
object Onboarding {
    val pending = MutableStateFlow<OnboardingStart?>(null)
}

class OnboardingViewModel(householdId: String, private val uid: String) : ViewModel() {
    private val repo = AppGraph.groceries(householdId)

    val household = AppGraph.households.observeHousehold(householdId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val kitchen = repo.observeOnHand().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Suggested staples, fixed when the screen opens so chips don't move as they're added. */
    val kitchenOptions = flow { emit(kitchenQuickAdds(repo.observeStats().first(), emptySet(), System.currentTimeMillis())) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, kitchenQuickAdds(emptyList(), emptySet(), System.currentTimeMillis()))

    fun addToKitchen(name: String, pantry: Boolean) {
        if (name.isNotBlank()) repo.addOnHand(name, uid, pantry)
    }

    /** Adding an item that's already on the other tab moves it here. */
    fun toggleKitchen(name: String, pantry: Boolean) {
        val item = kitchen.value.firstOrNull { it.key == itemKey(name) && it.isPantry == pantry }
        if (item != null) repo.removeOnHand(item) else repo.addOnHand(name, uid, pantry)
    }
}

private enum class Step { INVITE, RECIPES, KITCHEN, PANTRY }

/**
 * The walkthrough after someone starts or joins a household: invite (new households only), recipes,
 * then My kitchen's ingredients and pantry. Each step teaches one thing the app does and can be skipped.
 * iOS: OnboardingView.swift.
 */
@Composable
fun OnboardingScreen(session: Session.Ready, start: OnboardingStart, onDone: () -> Unit) {
    val viewModel: OnboardingViewModel = viewModel { OnboardingViewModel(session.householdId, session.user.uid) }
    val household by viewModel.household.collectAsStateWithLifecycle()
    val kitchen by viewModel.kitchen.collectAsStateWithLifecycle()
    val kitchenOptions by viewModel.kitchenOptions.collectAsStateWithLifecycle()

    OnboardingContent(
        start = start,
        householdName = household?.name,
        inviteCode = household?.inviteCode,
        kitchenOptions = kitchenOptions,
        inKitchen = kitchen.filterNot { it.isPantry }.map { it.name },
        inPantry = kitchen.filter { it.isPantry }.map { it.name },
        onToggleKitchen = viewModel::toggleKitchen,
        onAddToKitchen = viewModel::addToKitchen,
        onDone = onDone,
    )
}

/** The steps themselves, given the household's data. */
@Composable
fun OnboardingContent(
    start: OnboardingStart,
    householdName: String?,
    inviteCode: String?,
    kitchenOptions: List<String>,
    inKitchen: List<String>,
    inPantry: List<String>,
    onToggleKitchen: (name: String, pantry: Boolean) -> Unit,
    onAddToKitchen: (name: String, pantry: Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val steps = if (start == OnboardingStart.CREATED) {
        listOf(Step.INVITE, Step.RECIPES, Step.KITCHEN, Step.PANTRY)
    } else {
        listOf(Step.RECIPES, Step.KITCHEN, Step.PANTRY)
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]
    val pantryOptions = remember { pantryQuickAdds(emptySet()) }

    fun next() {
        if (index + 1 < steps.size) index++ else onDone()
    }

    BackHandler(enabled = index > 0) { index-- }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { index-- }, enabled = index > 0, modifier = Modifier.alpha(if (index > 0) 1f else 0f)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Spacer(Modifier.weight(1f))
            ProgressDots(current = index + 1, total = steps.size + 1) // the household step was the first
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = steps.indexOf(targetState) > steps.indexOf(initialState)
                slideInHorizontally { if (forward) it else -it } togetherWith slideOutHorizontally { if (forward) -it else it }
            },
            modifier = Modifier.weight(1f),
            label = "onboarding step",
        ) { shown ->
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                when (shown) {
                    Step.INVITE -> InviteStep(householdName, inviteCode)
                    Step.RECIPES -> RecipesStep()
                    Step.KITCHEN -> ChipsStep(
                        icon = AppIcons.Kitchen,
                        title = "Already have it at home?",
                        text = "Add things you keep stocked or get delivered, like a ButcherBox. " +
                            "Lists won't suggest them, and recipes won't add them to your list.",
                        options = kitchenOptions,
                        added = inKitchen,
                        onToggle = { onToggleKitchen(it, false) },
                        onAdd = { onAddToKitchen(it, false) },
                    )
                    Step.PANTRY -> ChipsStep(
                        icon = AppIcons.Leaf,
                        title = "What's in your spice rack?",
                        text = "Add the seasonings and sauces you keep around, like soy sauce or mirin. " +
                            "Recipes won't add them to your list, so you won't buy them twice.",
                        options = pantryOptions,
                        added = inPantry,
                        onToggle = { onToggleKitchen(it, true) },
                        onAdd = { onAddToKitchen(it, true) },
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            Button(onClick = ::next, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (step == steps.last()) "Done" else "Continue")
            }
        }
    }
}

@Composable
private fun ProgressDots(current: Int, total: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.semantics { contentDescription = "Step ${current + 1} of $total" },
    ) {
        repeat(total) { dot ->
            Box(
                Modifier
                    .size(width = if (dot == current) 18.dp else 7.dp, height = 7.dp)
                    .background(
                        if (dot == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
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
private fun ColumnScope.InviteStep(householdName: String?, code: String?) {
    val context = LocalContext.current
    Intro(
        AppIcons.Group,
        "Invite your household",
        "Everyone who joins sees and edits the same lists, live. Send them this code now, or find it in Settings later.",
    )
    Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (code == null) {
                CircularProgressIndicator()
            } else {
                Text(code, style = MaterialTheme.typography.displaySmall, fontFamily = FontFamily.Monospace)
                FilledTonalButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "Join our grocery list household \"$householdName\" with code $code")
                    }
                    context.startActivity(Intent.createChooser(send, "Share invite code"))
                }) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Text("Share code", Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.RecipesStep() {
    Intro(
        AppIcons.Restaurant,
        "Your household's recipes",
        "Save recipes in one place everyone can see. Paste one from a website or a message, or ask for one, " +
            "like \"chicken thighs in the dutch oven\". Then add what you're missing to a list in a tap.",
    )
}

/** Toggleable suggestions plus a field for anything else; used for the kitchen and pantry steps. */
@Composable
private fun ColumnScope.ChipsStep(
    icon: ImageVector,
    title: String,
    text: String,
    options: List<String>,
    added: List<String>,
    onToggle: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    Intro(icon, title, text)
    // The suggestions, plus anything typed in, so added items stay visible as checked chips.
    val suggested = options.mapTo(HashSet()) { itemKey(it) }
    val kitchenKeys = added.mapTo(HashSet()) { itemKey(it) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (options + added.filter { itemKey(it) !in suggested }).forEach { name ->
            val selected = itemKey(name) in kitchenKeys
            FilterChip(
                selected = selected,
                onClick = { onToggle(name) },
                label = { Text(name) },
                leadingIcon = {
                    Icon(if (selected) Icons.Default.Check else Icons.Default.Add, contentDescription = null, Modifier.size(18.dp))
                },
            )
        }
    }
    var typed by rememberSaveable(title) { mutableStateOf("") }
    val submit = {
        onAdd(typed.trim())
        typed = ""
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = typed,
            onValueChange = { typed = it },
            placeholder = { Text("Add something else") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        FilledIconButton(onClick = submit, enabled = typed.isNotBlank()) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }
    Text(
        "You can change these anytime in My kitchen.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
