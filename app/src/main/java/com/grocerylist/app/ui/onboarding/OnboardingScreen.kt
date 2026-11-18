package com.grocerylist.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grocerylist.app.AppGraph
import com.grocerylist.app.ui.AppIcons
import com.grocerylist.app.ui.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
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

class OnboardingViewModel(householdId: String) : ViewModel() {
    val household = AppGraph.households.observeHousehold(householdId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

private enum class Step { INVITE, RECIPES }

/**
 * The walkthrough after someone starts or joins a household: invite (new households only), then the
 * recipe box. Each step teaches one thing the app does.
 * iOS: OnboardingView.swift.
 */
@Composable
fun OnboardingScreen(session: Session.Ready, start: OnboardingStart, onDone: () -> Unit) {
    val viewModel: OnboardingViewModel = viewModel { OnboardingViewModel(session.householdId) }
    val household by viewModel.household.collectAsStateWithLifecycle()

    OnboardingContent(
        start = start,
        householdName = household?.name,
        inviteCode = household?.inviteCode,
        onDone = onDone,
    )
}

/** The steps themselves, given the household's data. */
@Composable
fun OnboardingContent(
    start: OnboardingStart,
    householdName: String?,
    inviteCode: String?,
    onDone: () -> Unit,
) {
    val steps = if (start == OnboardingStart.CREATED) {
        listOf(Step.INVITE, Step.RECIPES)
    } else {
        listOf(Step.RECIPES)
    }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val step = steps[index]

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
        "Save recipes in one place everyone can see. Paste one from a website, a message, or a YouTube video, " +
            "share one to Groceries from another app, or ask for one, like \"chicken thighs in the dutch oven\". " +
            "Then add what you're missing to a list in a tap.",
    )
}
