package com.grocerylist.app.data

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatExamplesTest {
    @Test
    fun oneFromEachGroup() {
        repeat(50) { seed ->
            val picked = ChatExamples.pick(random = Random(seed))
            assertEquals(3, picked.size)
            assertEquals(1, picked.count { it in ChatExamples.INGREDIENT })
            assertEquals(1, picked.count { it in ChatExamples.GEAR })
            assertEquals(1, picked.count { it in ChatExamples.OCCASION })
        }
    }

    @Test
    fun nextSetIsAllNew() {
        var last = ChatExamples.pick(random = Random(1))
        repeat(50) { seed ->
            val next = ChatExamples.pick(avoid = last, random = Random(seed))
            assertTrue("$next repeats $last", next.none { it in last })
            last = next
        }
    }
}
