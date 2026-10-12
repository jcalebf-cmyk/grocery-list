package com.grocerylist.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

import com.grocerylist.app.data.AuthRepository
import com.grocerylist.app.data.GroceryRepository
import com.grocerylist.app.data.HouseholdRepository
import com.grocerylist.app.data.RecipeRepository

/** Hand-rolled dependency holder; the app is small enough not to need a DI framework. */
object AppGraph {
    /**
     * For work that must finish even if the screen that started it goes away, like account deletion,
     * which changes the session (and so the screen) partway through.
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val auth = AuthRepository()
    val households = HouseholdRepository()

    private val groceryRepos = mutableMapOf<String, GroceryRepository>()
    private val recipeRepos = mutableMapOf<String, RecipeRepository>()

    fun groceries(householdId: String): GroceryRepository =
        synchronized(groceryRepos) { groceryRepos.getOrPut(householdId) { GroceryRepository(householdId) } }

    fun recipes(householdId: String): RecipeRepository =
        synchronized(recipeRepos) { recipeRepos.getOrPut(householdId) { RecipeRepository(householdId) } }
}
