package com.grocerylist.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestionsTest {
    private val now = 100L * 24 * 60 * 60 * 1000
    private val day = 24L * 60 * 60 * 1000

    private fun stat(key: String, count: Long, daysAgo: Int, storeCounts: Map<String, Long> = emptyMap()) =
        ItemStat(key = key, displayName = key, count = count, lastAdded = now - daysAgo * day, storeCounts = storeCounts)

    @Test fun skipsItemsAddedOnlyOnce() {
        val result = suggestItems(listOf(stat("milk", 1, 0)), "s", emptySet(), now)
        assertEquals(emptyList<ItemStat>(), result)
    }

    @Test fun skipsItemsAlreadyOnTheList() {
        val result = suggestItems(listOf(stat("milk", 5, 0), stat("eggs", 5, 0)), "s", setOf("milk"), now)
        assertEquals(listOf("eggs"), result.map { it.key })
    }

    @Test fun skipsItemsInTheKitchen() {
        val stats = listOf(stat("Ground beef", 5, 0), stat("Chicken breast", 5, 0), stat("Rice vinegar", 5, 0), stat("Milk", 5, 0))
        val result = suggestItems(stats, "s", emptySet(), now, onHand = listOf("ground beef", "Chicken", "Rice"))
        // "Chicken" covers "Chicken breast", but "Rice" doesn't cover "Rice vinegar".
        assertEquals(setOf("Rice vinegar", "Milk"), result.map { it.key }.toSet())
    }

    @Test fun recentHabitsOutrankOldOnes() {
        val result = suggestItems(listOf(stat("old", 6, 60), stat("recent", 3, 1)), "s", emptySet(), now)
        assertEquals(listOf("recent", "old"), result.map { it.key })
    }

    @Test fun favorsItemsBoughtAtThisStore() {
        val stats = listOf(
            stat("milk", 4, 0, mapOf("wholefoods" to 4)),
            stat("paper towels", 4, 0, mapOf("costco" to 4)),
        )
        assertEquals("paper towels", suggestItems(stats, "costco", emptySet(), now).first().key)
        assertEquals("milk", suggestItems(stats, "wholefoods", emptySet(), now).first().key)
    }

    @Test fun normalizesItemKeys() {
        assertEquals("whole milk", itemKey("  Whole   Milk "))
        assertEquals("salt-pepper", itemKey("Salt/Pepper"))
    }

    @Test fun kitchenQuickAddsPutRegularsFirst() {
        val result = kitchenQuickAdds(listOf(stat("broccoli", 5, 0)), emptySet(), now)
        assertEquals("broccoli", result.first())
        assertEquals(12, result.size)
    }

    @Test fun kitchenQuickAddsSkipKitchenItemsAndDuplicates() {
        val stats = listOf(ItemStat(key = "eggs", displayName = "Eggs", count = 4, lastAdded = now))
        val result = kitchenQuickAdds(stats, inKitchen = setOf("rice"), now = now)
        assertEquals(1, result.count { itemKey(it) == "eggs" }) // a regular and a staple, shown once
        assertTrue(result.none { itemKey(it) == "rice" })
    }

    @Test fun pantryQuickAddsTakeTurnsAcrossCuisines() {
        val result = pantryQuickAdds(listOf("Japanese", "Mexican"), emptySet())
        assertEquals(listOf("Soy sauce", "Cumin", "Mirin", "Chili powder"), result.take(4))
        assertEquals(12, result.size)
    }

    @Test fun pantryQuickAddsSkipKitchenItemsAndFallBackToCommon() {
        val result = pantryQuickAdds(emptyList(), inKitchen = setOf("soy sauce"))
        assertEquals("Garlic powder", result.first())
        assertTrue(result.none { itemKey(it) == "soy sauce" })
    }
}
