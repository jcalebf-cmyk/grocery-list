package com.grocerylist.app.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.grocerylist.app.ui.auth.SignInScreen
import com.grocerylist.app.ui.home.HomeScreen
import com.grocerylist.app.ui.household.HouseholdScreen
import com.grocerylist.app.ui.household.HouseholdSetupScreen
import com.grocerylist.app.ui.list.ListScreen
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

@Composable
private fun SignedInNav(session: Session.Ready, onSignOut: () -> Unit) {
    val nav = rememberNavController()
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                session = session,
                onOpenList = { nav.navigate("list/$it") },
                onOpenRecipe = { nav.navigate("recipe/$it") },
                onOpenHousehold = { nav.navigate("household") },
            )
        }
        composable("list/{listId}") { entry ->
            ListScreen(
                session = session,
                listId = entry.arguments?.getString("listId").orEmpty(),
                onBack = { nav.popBackStack() },
            )
        }
        composable("recipe/{videoId}") { entry ->
            RecipeScreen(
                session = session,
                videoId = entry.arguments?.getString("videoId").orEmpty(),
                onBack = { nav.popBackStack() },
                onOpenList = { listId ->
                    nav.navigate("list/$listId") { popUpTo("home") }
                },
            )
        }
        composable("household") {
            HouseholdScreen(session = session, onBack = { nav.popBackStack() }, onSignOut = onSignOut)
        }
    }
}
