package com.grocerylist.app.data

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreBrandsTest {
    @Test fun matchesChainVariants() {
        assertTrue(StoreBrands.logoUrl("King Soopers Marketplace")!!.contains("kingsoopers.com"))
        assertTrue(StoreBrands.logoUrl("Costco Wholesale")!!.contains("costco.com"))
        assertTrue(StoreBrands.logoUrl("Whole Foods Market")!!.contains("wholefoodsmarket.com"))
    }

    @Test fun handlesApostrophes() {
        assertNotNull(StoreBrands.logoUrl("Trader Joe's"))
        assertNotNull(StoreBrands.logoUrl("Sam's Club"))
    }

    @Test fun unknownStoreHasNoLogo() {
        assertNull(StoreBrands.logoUrl("Corner Market"))
    }

    @Test fun doesNotMatchMidName() {
        // "Super Target" isn't a store name we expect, but "Targeted Foods" must not become Target.
        assertNull(StoreBrands.logoUrl("Targeted Foods Co-op"))
    }
}
