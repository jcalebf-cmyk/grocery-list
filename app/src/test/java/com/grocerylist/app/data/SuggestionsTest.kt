package com.grocerylist.app.data

import org.junit.Assert.assertEquals
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
}
