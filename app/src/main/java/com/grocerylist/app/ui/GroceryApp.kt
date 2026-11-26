package com.grocerylist.app.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.grocerylist.app.data.RecipeChats
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.grocerylist.app.ui.auth.SignInScreen
import com.grocerylist.app.ui.home.HomeScreen
import com.grocerylist.app.ui.home.PastListsScreen
import com.grocerylist.app.ui.household.AddHouseholdScreen
import com.grocerylist.app.ui.household.HouseholdSetupScreen
import com.grocerylist.app.ui.list.ListScreen
import com.grocerylist.app.ui.settings.SettingsScreen
import androidx.compose.runtime.LaunchedEffect
import com.grocerylist.app.data.SharedRecipe
import com.grocerylist.app.voice.OpenRecipeRequest
import com.grocerylist.app.ui.recipe.AllRecipesScreen
import com.grocerylist.app.ui.recipe.RecipeChatScreen
import com.grocerylist.app.ui.recipe.RecipeEditScreen
import com.grocerylist.app.ui.recipe.RecipeScreen
import kotlinx.coroutines.delay

@Composable
fun GroceryApp(sessionViewModel: SessionViewModel = viewModel()) {
    val session by sessionViewModel.session.collectAsStateWithLifecycle()
    // Recipe chats stay on the phone only while someone's signed in (sign-out or account deletion clears them).
    val context = LocalContext.current
    LaunchedEffect(session == Session.SignedOut) {
        if (session == Session.SignedOut) RecipeChats.clearAll(context.applicationContext)
    }

    Surface(Modifier.fillMaxSize()) {
        // Signing in, picking a household, or switching to another fades from one to the next.
        AnimatedContent(
            targetState = session,
            contentKey = { s -> if (s is Session.Ready) "ready/${s.householdId}" else s::class.simpleName },
            transitionSpec = { fadeIn(tween(300, delayMillis = 60)) togetherWith fadeOut(tween(160)) },
            label = "session",
        ) { s ->
            when (s) {
                Session.Loading -> LoadingScreen(onSignOut = sessionViewModel::signOut)
                Session.SignedOut -> SignInScreen()
                is Session.NeedsHousehold -> HouseholdSetupScreen(s.user, onSignOut = sessionViewModel::signOut)
                // Fresh screens, starting on Home, when another household opens.
                is Session.Ready -> key(s.householdId) { SignedInNav(s, onSignOut = sessionViewModel::signOut) }
                is Session.Failed -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(s.message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    TextButton(onClick = sessionViewModel::signOut) { Text("Sign out") }
                }
            }
        }
    }
}

/** After a while it offers a way out: without a connection, or with an account that's gone, it can't finish. */
@Composable
private fun LoadingScreen(onSignOut: () -> Unit) {
    var slow by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(8_000)
        slow = true
    }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        if (slow) {
            Text(
                "Still connecting. Check your internet connection.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            TextButton(onClick = onSignOut) { Text("Sign out") }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SignedInNav(session: Session.Ready, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    // A recipe shared from another app opens the paste screen, which reads it right away.
    val shared by SharedRecipe.pending.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        if (shared != null) nav.navigate("recipes/paste") { launchSingleTop = true }
    }
    // A recipe a voice action (Gemini) asked to open.
    val voiceRecipe by OpenRecipeRequest.pending.collectAsStateWithLifecycle()
    LaunchedEffect(voiceRecipe) {
        voiceRecipe?.let { id ->
            OpenRecipeRequest.pending.value = null
            nav.navigate("recipe/$id") { popUpTo("home"); launchSingleTop = true }
        }
    }
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(
                nav,
                startDestination = "home",
                enterTransition = navEnter,
                exitTransition = navExit,
                popEnterTransition = navPopEnter,
                popExitTransition = navPopExit,
            ) {
                screen("home") {
                    HomeScreen(
                        session = session,
                        onOpenList = { nav.navigate("list/$it") },
                        onOpenRecipe = { nav.navigate("recipe/$it") },
                        onAskForRecipe = { nav.navigate("recipes/ask") },
                        onPasteRecipe = { nav.navigate("recipes/paste") },
                        onSearchRecipes = { nav.navigate("recipes") },
                        onOpenPastLists = { nav.navigate("lists/past") },
                        onOpenSettings = { nav.navigate("settings") },
                    )
                }
                screen("list/{listId}") { entry ->
                    ListScreen(
                        session = session,
                        listId = entry.arguments?.getString("listId").orEmpty(),
                        onBack = { nav.popBackStack() },
                    )
                }
                screen("lists/past") {
                    PastListsScreen(session = session, onBack = { nav.popBackStack() }, onOpenList = { nav.navigate("list/$it") })
                }
                screen("recipes") {
                    AllRecipesScreen(
                        session = session,
                        onBack = { nav.popBackStack() },
                        onOpenRecipe = { nav.navigate("recipe/$it") },
                        onAsk = { nav.navigate("recipes/ask") },
                        onPaste = { nav.navigate("recipes/paste") },
                    )
                }
                screen("recipes/ask") {
                    RecipeChatScreen(
                        session = session,
                        onBack = { nav.popBackStack() },
                        onOpenList = { listId -> nav.navigate("list/$listId") },
                    )
                }
                screen("recipes/paste") {
                    RecipeEditScreen(
                        session = session,
                        recipeId = null,
                        onClose = { nav.popBackStack() },
                        // The pasted recipe replaces the paste screen, so Back from it goes where you came from.
                        onSaved = { id -> nav.navigate("recipe/$id") { popUpTo("recipes/paste") { inclusive = true } } },
                    )
                }
                screen("recipe/{recipeId}") { entry ->
                    val recipeId = entry.arguments?.getString("recipeId").orEmpty()
                    RecipeScreen(
                        session = session,
                        recipeId = recipeId,
                        onBack = { nav.popBackStack() },
                        onEdit = { nav.navigate("recipe/$recipeId/edit") },
                        onOpenList = { listId -> nav.navigate("list/$listId") { popUpTo("home") } },
                    )
                }
                screen("recipe/{recipeId}/edit") { entry ->
                    RecipeEditScreen(
                        session = session,
                        recipeId = entry.arguments?.getString("recipeId").orEmpty(),
                        onClose = { nav.popBackStack() },
                        onSaved = { nav.popBackStack() },
                    )
                }
                screen("settings") {
                    SettingsScreen(
                        session = session,
                        onBack = { nav.popBackStack() },
                        onAddHousehold = { nav.navigate("household/add") },
                        onSignOut = onSignOut,
                    )
                }
                screen("household/add") {
                    AddHouseholdScreen(
                        user = session.user,
                        onClose = { nav.popBackStack() },
                        onAdded = { nav.popBackStack("home", inclusive = false) },
                    )
                }
            }
        }
    }
}

/** A destination that exposes its transition to [sharedElement]. */
private fun NavGraphBuilder.screen(route: String, content: @Composable (NavBackStackEntry) -> Unit) {
    composable(route) { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}
