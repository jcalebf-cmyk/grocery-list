package com.grocerylist.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.grocerylist.app.data.SharedRecipe
import com.grocerylist.app.ui.GroceryApp
import com.grocerylist.app.ui.theme.GroceryListTheme
import com.grocerylist.app.voice.OpenRecipeRequest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) receive(intent)
        enableEdgeToEdge()
        setContent {
            GroceryListTheme {
                GroceryApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        receive(intent)
    }

    private fun receive(intent: Intent?) {
        receiveShare(intent)
        // A voice action (Gemini) asked to open a recipe.
        if (intent?.action == ACTION_OPEN_RECIPE) intent.getStringExtra(EXTRA_RECIPE_ID)?.let { OpenRecipeRequest.pending.value = it }
    }

    private fun receiveShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
            .distinct().joinToString("\n").trim()
        if (text.isNotEmpty()) SharedRecipe.pending.value = text
    }

    companion object {
        const val ACTION_OPEN_RECIPE = "com.grocerylist.app.OPEN_RECIPE"
        const val EXTRA_RECIPE_ID = "recipeId"
    }
}
