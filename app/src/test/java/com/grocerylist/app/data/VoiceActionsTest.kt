package com.grocerylist.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceActionsTest {
    private val costco = Store(id = "c", name = "Costco")
    private val traderJoes = Store(id = "t", name = "Trader Joe's")
    private val stores = listOf(costco, traderJoes)

    @Test fun splitsItemsOnCommasOnly() {
        assertEquals(listOf("milk", "mac and cheese", "eggs"), spokenItems("milk, mac and cheese,  eggs."))
        assertEquals(listOf("Milk"), spokenItems("Milk, milk"))
        assertEquals(emptyList<String>(), spokenItems(" , "))
    }

    @Test fun findsStoresTheWayPeopleSayThem() {
        assertEquals(traderJoes, findStore(stores, "trader joes"))
        assertEquals(traderJoes, findStore(stores, "Joe's"))
        assertEquals(costco, findStore(stores, "Costco list"))
        assertNull(findStore(stores, "Aldi"))
        assertNull(findStore(stores, ""))
    }

    @Test fun plainListNames() {
        assertTrue(meansPlainList("shopping list"))
        assertTrue(meansPlainList("Groceries"))
        assertFalse(meansPlainList("Costco"))
    }

    @Test fun picksTheOnlyOpenList() {
        val a = GroceryList(id = "a", storeId = "c", storeName = "Costco")
        val b = GroceryList(id = "b")
        assertEquals(ListPick.NoneOpen, pickList(emptyList()))
        assertEquals(ListPick.One(a), pickList(listOf(a)))
        assertEquals(ListPick.Several(listOf(a, b)), pickList(listOf(a, b)))
        assertEquals(b, openListFor(listOf(a, b), ""))
        assertEquals("Which list: Costco or shopping?", VoiceText.whichList(listOf(a, b)))
    }

    @Test fun findsItemsBySingularOrPlural() {
        val items = listOf(
            ListItem(id = "1", name = "Eggs", checked = true),
            ListItem(id = "2", name = "eggs"),
            ListItem(id = "3", name = "Tomatoes"),
            ListItem(id = "4", name = "Greek yogurt"),
        )
        assertEquals("2", findItem(items, "eggs")?.id) // unchecked first
        assertEquals("2", findItem(items, "egg")?.id)
        assertEquals("3", findItem(items, "tomato")?.id)
        assertEquals("4", findItem(items, "yogurt")?.id)
        assertNull(findItem(items, "milk"))
    }

    @Test fun findsRecipesByTitleWords() {
        val recipes = listOf(
            Recipe(id = "1", title = "Creamy garlic pasta with spinach"),
            Recipe(id = "2", title = "Garlic pasta"),
            Recipe(id = "3", title = "Chicken soup"),
        )
        assertEquals(listOf("2", "1"), findRecipes(recipes, "garlic pasta recipe").map { it.id })
        assertEquals(emptyList<String>(), findRecipes(recipes, "tacos").map { it.id })
    }

    @Test fun wording() {
        assertEquals("Added milk to your Costco list.", VoiceText.added(listOf("milk"), emptyList(), "Costco"))
        assertEquals(
            "Added milk, eggs and bread to your shopping list.",
            VoiceText.added(listOf("milk", "eggs", "bread"), emptyList(), ""),
        )
        assertEquals("Added eggs to your Costco list. Milk was already on it.", VoiceText.added(listOf("eggs"), listOf("milk"), "Costco"))
        assertEquals("Milk and eggs are already on your Costco list.", VoiceText.added(emptyList(), listOf("milk", "eggs"), "Costco"))
        assertEquals("Started a Costco list.", VoiceText.started("Costco", alreadyGoing = false))
        assertEquals("You already have a shopping list going.", VoiceText.started("", alreadyGoing = true))
        assertEquals("Milk isn't on your Costco list.", VoiceText.notOnList("milk", "Costco"))
    }

    @Test fun readsOutWhatsLeft() {
        val items = listOf(
            ListItem(name = "milk", createdAt = 1),
            ListItem(name = "eggs", createdAt = 2, checked = true),
            ListItem(name = "bread", createdAt = 3),
        )
        assertEquals("Your Costco list has milk and bread.", VoiceText.readOut(items, "Costco"))
        assertEquals("Your shopping list is empty.", VoiceText.readOut(emptyList(), ""))
        assertEquals(
            "Everything on your Costco list is checked off.",
            VoiceText.readOut(listOf(ListItem(name = "eggs", checked = true)), "Costco"),
        )
        val many = (1..12).map { ListItem(name = "item$it", createdAt = it.toLong()) }
        assertEquals(
            "Your Costco list has item1, item2, item3, item4, item5, item6, item7, item8, item9, item10 and 2 more.",
            VoiceText.readOut(many, "Costco"),
        )
    }
}
