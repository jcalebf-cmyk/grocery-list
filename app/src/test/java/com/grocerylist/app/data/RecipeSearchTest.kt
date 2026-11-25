package com.grocerylist.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeSearchTest {
    private fun recipe(title: String, vararg ingredients: String, description: String = "", channel: String = "") = Recipe(
        id = title,
        title = title,
        description = description,
        sourceName = channel,
        ingredients = ingredients.map { Ingredient(it, "") },
    )

    private val recipes = listOf(
        recipe("Weeknight curry", "chicken thighs", "coconut milk", description = "A quick Thai dinner."),
        recipe("Lemon Chicken Soup", "chicken breast", "lemon", "orzo"),
        recipe("Crème brûlée", "eggs", "cream"),
        recipe("Berry crumble", "blueberries", "oats", channel = "Baking With Mo"),
        recipe("Salsa", "tomato", "jalapeño"),
    )

    private fun titles(query: String) = searchRecipes(recipes, query).map { it.recipe.title }

    @Test fun blankGivesEverythingInOrder() {
        assertEquals(recipes.map { it.title }, titles("  "))
    }

    @Test fun titleMatchesComeBeforeIngredientMatches() {
        assertEquals(listOf("Lemon Chicken Soup", "Weeknight curry"), titles("chicken"))
    }

    @Test fun sameRankKeepsOrder() {
        val both = listOf(recipe("Chicken pie"), recipe("Baked chicken"))
        assertEquals(listOf("Chicken pie", "Baked chicken"), searchRecipes(both, "chicken").map { it.recipe.title })
    }

    @Test fun saysWhichIngredientMatched() {
        val match = searchRecipes(recipes, "curry coconut").single()
        assertEquals("coconut milk", match.ingredient)
        assertNull(searchRecipes(recipes, "soup").single().ingredient)
    }

    @Test fun namesTheIngredientThatMatchesMost() {
        val stew = listOf(recipe("Stew", "chicken stock", "chicken thighs"))
        assertEquals("chicken thighs", searchRecipes(stew, "chicken thighs").single().ingredient)
    }

    @Test fun namesOnlyThePartThatMatched() {
        val stew = listOf(recipe("Stew", "boneless, skinless chicken thighs, cut up"))
        assertEquals("skinless chicken thighs", searchRecipes(stew, "chicken").single().ingredient)
    }

    @Test fun everyWordHasToMatchSomewhere() {
        assertEquals(listOf("Lemon Chicken Soup"), titles("orzo chicken"))
        assertEquals(emptyList<String>(), titles("orzo beef"))
    }

    @Test fun ignoresAccentsCaseAndPunctuation() {
        assertEquals(listOf("Crème brûlée"), titles("CREME brulee"))
        assertEquals(listOf("Salsa"), titles("jalapeno"))
        assertEquals(listOf("Salsa"), titles("jalapeño"))
        assertEquals(listOf("Lemon Chicken Soup"), titles("lemon & chicken"))
    }

    @Test fun ignoresPlurals() {
        assertEquals(listOf("Salsa"), titles("tomatoes"))
        assertEquals(listOf("Berry crumble"), titles("berries"))
        assertEquals(listOf("Berry crumble"), titles("blueberry"))
        assertEquals(listOf("Crème brûlée"), titles("egg"))
    }

    @Test fun partOfAWordIsEnough() {
        assertEquals(listOf("Lemon Chicken Soup", "Weeknight curry"), titles("chick"))
    }

    @Test fun aShortenedPluralDoesNotMatchInsideOtherWords() {
        // "limes" also reads as "lim", which mustn't turn up slim or lima.
        assertEquals(emptyList<String>(), searchRecipes(listOf(recipe("Slim lima bean salad")), "limes").map { it.recipe.title })
    }

    @Test fun descriptionAndChannelComeLast() {
        assertEquals(listOf("Weeknight curry"), titles("thai"))
        assertEquals(listOf("Berry crumble"), titles("mo baking"))
        val mixed = listOf(recipe("Curry", description = "Great with rice"), recipe("Rice bowl"))
        assertEquals(listOf("Rice bowl", "Curry"), searchRecipes(mixed, "rice").map { it.recipe.title })
    }
}
