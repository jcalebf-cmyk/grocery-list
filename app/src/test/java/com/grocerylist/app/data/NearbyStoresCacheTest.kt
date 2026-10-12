package com.grocerylist.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyStoresCacheTest {
    private val day = 24L * 60 * 60 * 1000
    private val lookup = SavedLookup(lat = 39.74, lng = -104.99, fetchedAt = 0, stores = emptyList())

    @Test fun reusesWhenNearbyAndRecent() {
        assertTrue(lookup.canReuseAt(39.74, -104.99, now = 3 * day))
        // One rounding step away (~1.1 km) still counts as the same place.
        assertTrue(lookup.canReuseAt(39.75, -104.99, now = 3 * day))
    }

    @Test fun refetchesAfterAWeek() {
        assertFalse(lookup.canReuseAt(39.74, -104.99, now = 8 * day))
    }

    @Test fun refetchesAfterMoving() {
        // About 5.5 km north.
        assertFalse(lookup.canReuseAt(39.79, -104.99, now = day))
    }
}
