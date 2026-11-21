package com.grocerylist.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareTextTest {
    @Test fun fullRecipe() {
        val recipe = Recipe(
            title = "Garlic pasta",
            description = "Quick weeknight dinner.",
            servings = 4,
            totalMinutes = 20,
            ingredients = listOf(Ingredient("spaghetti", "1 lb"), Ingredient("garlic", "4 cloves"), Ingredient("salt", "")),
            steps = listOf("Boil the pasta.", "Fry the garlic, then toss."),
            notes = "Add chili flakes if you like.",
            sourceUrl = "https://youtu.be/abc",
        )
        assertEquals(
            """
            Garlic pasta
            Quick weeknight dinner.
            20 min · Serves 4

            Ingredients
            • 1 lb spaghetti
            • 4 cloves garlic
            • Salt

            Steps
            1. Boil the pasta.
            2. Fry the garlic, then toss.

            Notes
            Add chili flakes if you like.

            Video: https://youtu.be/abc
            """.trimIndent(),
            recipeShareText(recipe),
        )
    }

    @Test fun bareRecipeLeavesOutEmptyParts() {
        assertEquals("Toast\n\nSteps\n1. Toast the bread.", recipeShareText(Recipe(title = "Toast", steps = listOf("Toast the bread."))))
    }

    @Test fun listIsWhatsLeftInAisleOrder() {
        val items = listOf(
            ListItem(name = "Milk"),
            ListItem(name = "Bananas"),
            ListItem(name = "Bread", checked = true),
        )
        assertEquals("Trader Joe's list\n• Bananas\n• Milk", listShareText(GroceryList(storeName = "Trader Joe's"), items))
        assertEquals("Shopping list\n• Bananas\n• Milk", listShareText(GroceryList(), items))
    }

    @Test fun nothingToShareWhenAllChecked() {
        assertNull(listShareText(GroceryList(), listOf(ListItem(name = "Bread", checked = true))))
        assertNull(listShareText(GroceryList(), emptyList()))
    }
}
