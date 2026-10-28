package com.grocerylist.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AislesTest {
    private fun assertAisle(aisle: Aisle, vararg names: String) {
        for (name in names) assertEquals(name, aisle, aisleFor(name))
    }

    @Test fun everydayItems() {
        assertAisle(
            Aisle.PRODUCE,
            "Bananas", "Strawberries", "Tomatoes", "Baby spinach", "Green beans", "Brussels sprouts",
        )
        assertAisle(Aisle.BAKERY, "Bread", "Bagels", "Flour tortillas", "Hot dog buns", "Hawaiian rolls")
        assertAisle(Aisle.MEAT, "Chicken breast", "Ground beef", "Salmon", "Hot dogs", "Shrimp")
        assertAisle(
            Aisle.PANTRY,
            "Rice", "Black beans", "Chicken broth", "Peanut butter", "Olive oil", "Chocolate chips",
        )
        assertAisle(Aisle.SNACKS, "Potato chips", "Tortilla chips", "Chocolate chip cookies", "Granola bars", "Almonds")
        assertAisle(Aisle.DRINKS, "Orange juice", "Coffee", "Coconut water", "Tea bags", "Sparkling water")
        assertAisle(Aisle.HOUSEHOLD, "Paper towels", "Toilet paper rolls", "Dish soap", "Trash bags", "AA batteries")
        assertAisle(Aisle.PERSONAL_CARE, "Toothpaste", "Shampoo", "Baby wipes", "Shaving cream")
        assertAisle(Aisle.PET, "Dog food", "Cat litter")
        assertAisle(
            Aisle.DAIRY,
            "Milk", "Eggs", "Butter", "Cream cheese", "Strawberry yogurt", "Half & half", "Almond milk",
        )
        assertAisle(Aisle.FROZEN, "Ice cream", "Frozen pizza", "Sweet potato fries", "Chicken nuggets")
    }

    @Test fun lastWordWins() {
        assertAisle(Aisle.PANTRY, "Chicken broth", "Tomato sauce", "Garlic powder")
        assertAisle(Aisle.DAIRY, "Chocolate milk", "Coffee creamer")
        assertAisle(Aisle.PRODUCE, "Butter lettuce")
        assertAisle(Aisle.FROZEN, "Mint chocolate chip ice cream", "Sweet potato fries")
    }

    @Test fun thenLongerPhraseWins() {
        assertAisle(
            Aisle.PANTRY,
            "Peanut butter", "Mac & cheese", "Coconut milk", "Red pepper flakes", "Salt and pepper", "Baking soda",
        )
    }

    @Test fun frozenCannedAndDriedOverride() {
        assertAisle(Aisle.FROZEN, "Frozen peas", "Frozen strawberries")
        assertAisle(Aisle.PANTRY, "Canned tomatoes", "Dried oregano")
    }

    @Test fun wholeWordsOnly() {
        assertAisle(Aisle.PRODUCE, "Eggplant")
        assertAisle(Aisle.BAKERY, "Bagels")
        assertAisle(Aisle.OTHER, "Birthday card", "")
    }

    @Test fun quickAddsAllHaveAnAisle() {
        val quickAdds = BASIC_PANTRY + COMMON_PANTRY + CUISINE_PANTRY.values.flatten() +
            kitchenQuickAdds(emptyList(), emptySet(), 0, limit = 100)
        for (name in quickAdds) assertNotEquals(name, Aisle.OTHER, aisleFor(name))
    }

    @Test fun eachPhraseIsInOneAisle() {
        val phrases = AISLE_KEYWORDS.values.flatten()
        assertEquals(phrases.groupBy { it }.filterValues { it.size > 1 }.keys, emptySet<String>())
    }

    @Test fun groupsInStoreOrderWithCheckedLast() {
        fun item(name: String, checked: Boolean = false) = ListItem(id = name, name = name, checked = checked)
        val sections = groupByAisle(
            listOf(item("Ice cream"), item("Milk", checked = true), item("Apples"), item("Bread"), item("Bananas")),
        )
        assertEquals(listOf(Aisle.PRODUCE, Aisle.BAKERY, Aisle.FROZEN, null), sections.map { it.aisle })
        assertEquals(listOf("Apples", "Bananas"), sections[0].items.map { it.name })
        assertEquals(listOf("Milk"), sections.last().items.map { it.name })
        assertTrue(groupByAisle(emptyList()).isEmpty())
    }
}
