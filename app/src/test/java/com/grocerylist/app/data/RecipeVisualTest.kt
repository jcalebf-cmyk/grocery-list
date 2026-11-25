package com.grocerylist.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecipeVisualTest {
    private fun check(vararg expected: Pair<String, String>) =
        assertEquals(expected.toList(), expected.map { (title, _) -> title to recipeEmoji(title).emoji })

    @Test fun theKindOfDishComesFirst() = check(
        "Chicken Noodle Soup" to "🍲",
        "Black Bean Tacos" to "🌮",
        "Beef Stir-Fry" to "🥘",
        "Grandma's Banana Bread" to "🍞",
        "Blueberry Pancakes" to "🥞",
        "Weeknight Thai Red Curry" to "🍛",
        "Bandera's cornbread" to "🍞",
        "Crème Brûlée" to "🍮",
        "Spaghetti and Meatballs" to "🍝",
        "Peanut Butter and Jelly Sandwich" to "🥪",
        "Mac & Cheese" to "🍝",
    )

    @Test fun theLastOneNamedWins() = check(
        "Taco Salad" to "🥗",
        "Chicken Pot Pie" to "🥧",
        "Ice Cream Cake" to "🍰",
        "Beef Chili" to "🌶️",
        "Sweet Chili Salmon" to "🐟",
        "Chicken Fried Rice" to "🍚",
        "Crab Cakes" to "🦀",
        "Egg Rolls" to "🥟",
        "Cinnamon Rolls" to "🍞",
        "Air Fryer French Fries" to "🍟",
        "Hot Dogs" to "🌭",
    )

    @Test fun otherwiseWhatItsMadeOf() = check(
        "Lemon Garlic Chicken Thighs" to "🍗",
        "Miso Salmon" to "🐟",
        "Beef and Broccoli" to "🥩",
        "Roasted Sweet Potatoes" to "🍠",
        "Mashed Potatoes" to "🥔",
        "Salsa" to "🍅",
        "Deviled Eggs" to "🥚",
    )

    @Test fun sidesOnlyCountWhenNothingElseDoes() = check(
        "Chicken with Rice" to "🍗",
        "Creamy Tuscan Sausage Pasta with Spinach" to "🍝",
        "Pot of Gold with Shrimp" to "🦐",
    )

    @Test fun wholeWordsOnly() = check("Graham's Surprise" to "🍽️", "Sunday Supper" to "🍽️", "" to "🍽️")

    @Test fun thumbnails() {
        val expected = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"
        assertEquals(expected, videoThumbnail("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals(expected, videoThumbnail("https://www.youtube.com/watch?t=3&v=dQw4w9WgXcQ"))
        assertEquals(expected, videoThumbnail("https://youtu.be/dQw4w9WgXcQ?si=abc"))
        assertEquals(expected, videoThumbnail("https://youtube.com/shorts/dQw4w9WgXcQ"))
        assertNull(videoThumbnail(""))
        assertNull(videoThumbnail("https://example.com/watch?v=dQw4w9WgXcQ"))
    }
}
