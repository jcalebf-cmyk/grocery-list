package com.grocerylist.app.data

/**
 * Logos for common US grocery chains, matched by store name ("King Soopers Marketplace" → King Soopers).
 * Stores not listed here get a lettered tile instead.
 */
object StoreBrands {
    private class Brand(val keys: List<String>, val domain: String, val logoOverride: String? = null)

    private val brands = listOf(
        Brand(listOf("king soopers"), "kingsoopers.com"),
        Brand(listOf("whole foods"), "wholefoodsmarket.com"),
        // Google's favicon service only has a 16px Costco icon; the site's app icon is sharper.
        Brand(listOf("costco"), "costco.com", "https://www.costco.com/apple-touch-icon.png"),
        Brand(listOf("walmart"), "walmart.com", "https://www.walmart.com/apple-touch-icon.png"),
        Brand(listOf("target"), "target.com"),
        Brand(listOf("safeway"), "safeway.com"),
        Brand(listOf("sprouts"), "sprouts.com"),
        Brand(listOf("trader joe"), "traderjoes.com"),
        Brand(listOf("natural grocers"), "naturalgrocers.com"),
        Brand(listOf("sams club"), "samsclub.com"),
        Brand(listOf("aldi"), "aldi.us"),
        Brand(listOf("lidl"), "lidl.com"),
        Brand(listOf("kroger"), "kroger.com"),
        Brand(listOf("albertsons"), "albertsons.com"),
        Brand(listOf("city market"), "citymarket.com"),
        Brand(listOf("smiths food", "smith s food"), "smithsfoodanddrug.com"),
        Brand(listOf("frys food", "fry s food"), "frysfood.com"),
        Brand(listOf("fred meyer"), "fredmeyer.com"),
        Brand(listOf("ralphs"), "ralphs.com"),
        Brand(listOf("dillons"), "dillons.com"),
        Brand(listOf("food 4 less", "food4less"), "food4less.com"),
        Brand(listOf("vons"), "vons.com"),
        Brand(listOf("jewel osco"), "jewelosco.com"),
        Brand(listOf("publix"), "publix.com"),
        Brand(listOf("h e b", "heb"), "heb.com"),
        Brand(listOf("wegmans"), "wegmans.com"),
        Brand(listOf("meijer"), "meijer.com"),
        Brand(listOf("hy vee"), "hy-vee.com"),
        Brand(listOf("harris teeter"), "harristeeter.com"),
        Brand(listOf("food lion"), "foodlion.com"),
        Brand(listOf("stop shop"), "stopandshop.com"),
        Brand(listOf("giant food"), "giantfood.com"),
        Brand(listOf("winco"), "wincofoods.com"),
        Brand(listOf("grocery outlet"), "groceryoutlet.com"),
        Brand(listOf("fresh market"), "thefreshmarket.com"),
        Brand(listOf("bjs wholesale", "bj s wholesale"), "bjs.com"),
        Brand(listOf("smart final"), "smartandfinal.com"),
        Brand(listOf("amazon fresh"), "amazon.com"),
        Brand(listOf("h mart"), "hmart.com"),
        Brand(listOf("99 ranch"), "99ranch.com"),
        Brand(listOf("winn dixie"), "winndixie.com"),
        Brand(listOf("shaws"), "shaws.com"),
        Brand(listOf("market basket"), "shopmarketbasket.com"),
    )

    /** Null for stores without a known logo. */
    fun logoUrl(storeName: String): String? {
        // "Trader Joe's" → "trader joe s", "Sam's Club" → "sams club" both need to match, so try both forms.
        val spaced = storeName.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()
        val joined = storeName.lowercase().replace("'", "").replace("’", "").replace(Regex("[^a-z0-9]+"), " ").trim()
        fun startsWithWord(name: String, key: String) = name == key || name.startsWith("$key ")
        val brand = brands.firstOrNull { b -> b.keys.any { k -> startsWithWord(spaced, k) || startsWithWord(joined, k) } }
            ?: return null
        return brand.logoOverride ?: "https://www.google.com/s2/favicons?domain=${brand.domain}&sz=128"
    }
}
