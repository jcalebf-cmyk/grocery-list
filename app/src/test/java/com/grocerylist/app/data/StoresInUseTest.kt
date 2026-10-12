package com.grocerylist.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class StoresInUseTest {
    private val wholeFoods = Store(id = "wf", name = "Whole Foods")
    private val costco = Store(id = "c", name = "Costco")
    private val neverUsed = Store(id = "n", name = "King Soopers")

    @Test fun hidesStoresWithoutLists() {
        val lists = listOf(GroceryList(id = "1", storeId = "wf", createdAt = 10))
        assertEquals(listOf(wholeFoods), storesInUse(listOf(wholeFoods, costco, neverUsed), lists))
    }

    @Test fun mostRecentlyUsedFirst() {
        val lists = listOf(
            GroceryList(id = "1", storeId = "wf", createdAt = 10),
            GroceryList(id = "2", storeId = "c", createdAt = 20),
            GroceryList(id = "3", storeId = "wf", createdAt = 5),
        )
        assertEquals(listOf(costco, wholeFoods), storesInUse(listOf(wholeFoods, costco), lists))
    }

    @Test fun ignoresListsWithoutAStore() {
        val lists = listOf(GroceryList(id = "1", storeId = "", createdAt = 10))
        assertEquals(emptyList<Store>(), storesInUse(listOf(wholeFoods), lists))
    }
}
