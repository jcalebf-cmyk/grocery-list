package com.grocerylist.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyStoresCacheTest {
    private val day = 24L * 60 * 60 * 1000
    private val lookup = SavedLookup(lat = 39.74, lng = -104.99, fetchedAt = 0, stores = emptyList())

    @Test fun reusesWhenNearbyAndRecent() {
        assertTrue(lookup.canReuseAt(39.74, -104.99, now = 3 * day))
        // About 1.1 km away still counts as the same place.
        assertTrue(lookup.canReuseAt(39.75, -104.99, now = 3 * day))
    }

    @Test fun refetchesAfterAWeek() {
        assertFalse(lookup.canReuseAt(39.74, -104.99, now = 8 * day))
    }

    @Test fun refetchesAfterMoving() {
        // About 5.5 km north.
        assertFalse(lookup.canReuseAt(39.79, -104.99, now = day))
    }

    @Test fun refetchesWhenSavedStoresHaveNoCoordinates() {
        val old = lookup.copy(stores = listOf(NearbyStore("King Soopers", 900)))
        assertFalse(old.canReuseAt(39.74, -104.99, now = day))
    }

    @Test fun remeasuresDistancesFromWhereYouAre() {
        val saved = lookup.copy(
            stores = listOf(
                NearbyStore("Safeway", 500, lat = 39.75, lng = -104.99),
                NearbyStore("King Soopers", 600, lat = 39.73, lng = -104.99),
            ),
        )
        // Standing 0.005° (~556 m) south of the lookup point, King Soopers is now the closer one.
        val stores = saved.measuredFrom(39.735, -104.99)
        assertEquals(listOf("King Soopers", "Safeway"), stores.map { it.name })
        assertEquals(556.0, stores[0].distanceMeters.toDouble(), 2.0)
    }
}
