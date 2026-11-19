package com.grocerylist.app.ui.household

import org.junit.Assert.assertEquals
import org.junit.Test

class SuggestedHouseholdNameTest {
    @Test fun usesTheLastName() {
        assertEquals("Franzen household", suggestedHouseholdName("Josh Franzen"))
        assertEquals("Garcia household", suggestedHouseholdName("  Maria  de la Garcia "))
    }

    @Test fun leavesOneWordNamesBlank() {
        assertEquals("", suggestedHouseholdName("jcf"))
        assertEquals("", suggestedHouseholdName(""))
        assertEquals("", suggestedHouseholdName("   "))
    }
}
