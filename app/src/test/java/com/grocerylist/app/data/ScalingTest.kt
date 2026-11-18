package com.grocerylist.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScalingTest {
    @Test fun wholeNumbersAndUnits() {
        assertEquals("4 cloves", scaleQuantity("2 cloves", 2.0))
        assertEquals("2 large, diced", scaleQuantity("1 large, diced", 2.0))
    }

    @Test fun fractions() {
        assertEquals("3 lb", scaleQuantity("1 1/2 lb", 2.0))
        assertEquals("1½ cups", scaleQuantity("½ cups", 3.0))
        assertEquals("¾ tsp", scaleQuantity("1/2 tsp", 1.5))
        assertEquals("2¼ lb", scaleQuantity("1½ lb", 1.5))
        assertEquals("⅓ cup", scaleQuantity("2/3 cup", 0.5))
    }

    @Test fun decimalsStayDecimal() {
        assertEquals("3 lb", scaleQuantity("1.5 lb", 2.0))
        assertEquals("0.75 lb", scaleQuantity("1.5 lb", 0.5))
    }

    @Test fun ranges() {
        assertEquals("4–6 cloves", scaleQuantity("2-3 cloves", 2.0))
        assertEquals("2–3 cups", scaleQuantity("1 to 1½ cups", 2.0))
    }

    @Test fun nonNumbersAndNoChange() {
        assertEquals("to taste", scaleQuantity("to taste", 2.0))
        assertEquals("a pinch", scaleQuantity("a pinch", 3.0))
        assertEquals("", scaleQuantity("", 2.0))
        assertEquals("1/2 tsp", scaleQuantity("1/2 tsp", 1.0))
    }

    @Test fun tinyAmountsDontVanish() {
        assertEquals("⅛ tsp", scaleQuantity("1/8 tsp", 0.25))
    }

    @Test fun factor() {
        assertEquals(1.5, servingsFactor(4, 6), 1e-9)
        assertEquals(1.0, servingsFactor(0, 6), 1e-9)
    }
}
