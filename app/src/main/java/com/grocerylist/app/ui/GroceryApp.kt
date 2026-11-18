package com.grocerylist.app.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
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
import androidx.compose.runtime.getValue
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
import com.grocerylist.app.ui.household.HouseholdSetupScreen
import com.grocerylist.app.ui.list.ListScreen
import com.grocerylist.app.ui.settings.SettingsScreen
import com.grocerylist.app.ui.onhand.OnHandScreen
import androidx.compose.runtime.LaunchedEffect
import com.grocerylist.app.data.SharedRecipe
import com.grocerylist.app.ui.recipe.AllRecipesScreen
import com.grocerylist.app.ui.recipe.RecipeChatScreen
import com.grocerylist.app.ui.recipe.RecipeEditScreen
import com.grocerylist.app.ui.recipe.RecipeScreen

@Composable
fun GroceryApp(sessionViewModel: SessionViewModel = viewModel()) {
    val session by sessionViewModel.session.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize()) {
        when (val s = session) {
            Session.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            Session.SignedOut -> SignInScreen()
            is Session.NeedsHousehold -> HouseholdSetupScreen(s.user, onSignOut = sessionViewModel::signOut)
            is Session.Ready -> SignedInNav(s, onSignOut = sessionViewModel::signOut)
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SignedInNav(session: Session.Ready, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    // A recipe shared from another app opens the paste screen, which reads it right away.
    val shared by SharedRecipe.pending.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        if (shared != null) nav.navigate("recipes/paste") { launchSingleTop = true }
    }
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(nav, startDestination = "home") {
                screen("home") {
                    HomeScreen(
                        session = session,
                        onOpenList = { nav.navigate("list/$it") },
                        onOpenRecipe = { nav.navigate("recipe/$it") },
                        onAskForRecipe = { nav.navigate("recipes/ask") },
                        onPasteRecipe = { nav.navigate("recipes/paste") },
                        onAllRecipes = { nav.navigate("recipes") },
                        onOpenOnHand = { nav.navigate("onhand") },
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
                screen("onhand") {
                    OnHandScreen(session = session, onBack = { nav.popBackStack() })
                }
                screen("settings") {
                    SettingsScreen(session = session, onBack = { nav.popBackStack() }, onSignOut = onSignOut)
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
