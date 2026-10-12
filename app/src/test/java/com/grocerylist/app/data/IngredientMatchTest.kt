package com.grocerylist.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IngredientMatchTest {
    @Test fun moreSpecificIngredientIsCovered() {
        assertTrue(isCoveredBy("Lean ground beef", listOf("ground beef")))
        assertTrue(isCoveredBy("chicken breast", listOf("Chicken")))
    }

    @Test fun pluralsMatch() {
        assertTrue(isCoveredBy("2 large eggs", listOf("egg")))
    }

    @Test fun partialWordsDoNotMatch() {
        assertFalse(isCoveredBy("unsalted butter", listOf("salt")))
    }

    @Test fun lessSpecificIngredientIsNotCovered() {
        assertFalse(isCoveredBy("beef", listOf("ground beef")))
    }

    @Test fun nothingOnHand() {
        assertFalse(isCoveredBy("rice", emptyList()))
    }
}
