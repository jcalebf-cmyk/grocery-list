package com.grocerylist.app.data

import kotlin.math.pow

private const val DAY_MS = 24 * 60 * 60 * 1000.0
private const val HALF_LIFE_DAYS = 21.0
private const val MIN_TIMES_ADDED = 2

/**
 * Ranks items the household adds often, favoring recent habits and the store this list is for.
 * An item added 10 times three weeks ago scores the same as one added 5 times today.
 */
fun suggestItems(
    stats: List<ItemStat>,
    storeId: String,
    alreadyOnList: Set<String>,
    now: Long,
    limit: Int = 12,
): List<ItemStat> =
    stats
        .filter { it.count >= MIN_TIMES_ADDED && it.key !in alreadyOnList }
        .map { stat ->
            val daysSince = ((now - stat.lastAdded) / DAY_MS).coerceAtLeast(0.0)
            val recency = 0.5.pow(daysSince / HALF_LIFE_DAYS)
            val atThisStore = stat.storeCounts[storeId] ?: 0
            stat to (stat.count + 2.0 * atThisStore) * recency
        }
        .sortedByDescending { it.second }
        .take(limit)
        .map { it.first }

// Common things people keep stocked or get from subscription boxes.
private val COMMON_KITCHEN_ITEMS = listOf(
    "Chicken breast", "Ground beef", "Salmon", "Steak", "Pork chops", "Eggs",
    "Rice", "Pasta", "Potatoes", "Onions", "Black beans", "Frozen vegetables",
)

/**
 * Quick-add chips for My kitchen: the household's own regulars first (ranked like list suggestions,
 * but across all stores), then common staples to fill out the row. Skips anything already in the kitchen.
 */
fun kitchenQuickAdds(
    stats: List<ItemStat>,
    inKitchen: Set<String>,
    now: Long,
    limit: Int = 12,
    maxRegulars: Int = 8,
): List<String> {
    val regulars = suggestItems(stats, storeId = "", alreadyOnList = inKitchen, now = now, limit = maxRegulars)
        .map { it.displayName }
    val seen = HashSet(inKitchen)
    return (regulars + COMMON_KITCHEN_ITEMS).filter { seen.add(itemKey(it)) }.take(limit)
}
