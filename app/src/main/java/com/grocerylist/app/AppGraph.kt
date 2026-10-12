package com.grocerylist.app

import com.grocerylist.app.data.AuthRepository
import com.grocerylist.app.data.GroceryRepository
import com.grocerylist.app.data.HouseholdRepository
import com.grocerylist.app.data.RecipeRepository

/** Hand-rolled dependency holder; the app is small enough not to need a DI framework. */
object AppGraph {
    val auth = AuthRepository()
    val households = HouseholdRepository()

    private val groceryRepos = mutableMapOf<String, GroceryRepository>()
    private val recipeRepos = mutableMapOf<String, RecipeRepository>()

    fun groceries(householdId: String): GroceryRepository =
        synchronized(groceryRepos) { groceryRepos.getOrPut(householdId) { GroceryRepository(householdId) } }

    fun recipes(householdId: String): RecipeRepository =
        synchronized(recipeRepos) { recipeRepos.getOrPut(householdId) { RecipeRepository(householdId) } }
}
