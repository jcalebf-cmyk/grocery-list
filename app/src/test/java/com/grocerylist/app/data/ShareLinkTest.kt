package com.grocerylist.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Same cases as ShareLinkTests in the iOS tests. */
class ShareLinkTest {
    @Test fun readsTheCode() {
        assertEquals("aB3dE5gH7k", shareCodeFrom("https://www.householdgroceries.app/r/aB3dE5gH7k"))
        assertEquals("aB3dE5gH7k", shareCodeFrom("https://householdgroceries.app/r/aB3dE5gH7k/"))
        assertEquals("aB3dE5gH7k", shareCodeFrom("https://WWW.householdgroceries.app/r/aB3dE5gH7k?utm=x#top"))
    }

    @Test fun rejectsOtherLinks() {
        assertNull(shareCodeFrom(null))
        assertNull(shareCodeFrom("http://www.householdgroceries.app/r/aB3dE5gH7k")) // not https
        assertNull(shareCodeFrom("https://evil.example/r/aB3dE5gH7k"))
        assertNull(shareCodeFrom("https://www.householdgroceries.app/account?mode=resetPassword"))
        assertNull(shareCodeFrom("https://www.householdgroceries.app/r/short"))
        assertNull(shareCodeFrom("https://www.householdgroceries.app/r/aB3dE5gH70")) // 0 isn't used
        assertNull(shareCodeFrom("https://www.householdgroceries.app/r/aB3dE5gH7k/more"))
    }
}
