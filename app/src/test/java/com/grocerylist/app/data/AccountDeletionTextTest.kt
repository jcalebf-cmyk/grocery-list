package com.grocerylist.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountDeletionTextTest {
    @Test
    fun onlyHouseholdGoes() = assertEquals(
        "This permanently deletes your account and Franzen household, including all of its lists, stores, and recipes. " +
            "This can't be undone.",
        accountDeletionText(listOf("Franzen household"), emptyList()),
    )

    @Test
    fun sharedHouseholdStays() = assertEquals(
        "This permanently deletes your account and removes you from Franzen household. Its lists stay with the other members.",
        accountDeletionText(emptyList(), listOf("Franzen household")),
    )

    @Test
    fun someOfEach() = assertEquals(
        "This permanently deletes your account and A and B, including all of their lists, stores, and recipes. " +
            "This can't be undone. You'll also be removed from C, D, and E. Their lists stay with the other members.",
        accountDeletionText(listOf("A", "B"), listOf("C", "D", "E")),
    )

    @Test
    fun noHouseholds() =
        assertEquals("This permanently deletes your account. This can't be undone.", accountDeletionText(emptyList(), emptyList()))
}
