package com.grocerylist.app.data

import kotlin.math.pow

private const val DAY_MS = 24 * 60 * 60 * 1000.0
private const val HALF_LIFE_DAYS = 21.0
private const val MIN_TIMES_ADDED = 2
private const val LIST_MIN_TIMES_ADDED = 3
private const val LIST_MAX_DAYS_SINCE_ADDED = 60

/**
 * Suggestions for a list: the household's regulars (see [rankRegulars]) that were added at least 3 times,
 * most recently within 60 days, and not dismissed since they were last added.
 */
fun suggestItems(
    stats: List<ItemStat>,
    storeId: String,
    alreadyOnList: Set<String>,
    now: Long,
    limit: Int = 12,
): List<ItemStat> =
    rankRegulars(
        stats.filter {
            it.count >= LIST_MIN_TIMES_ADDED &&
                now - it.lastAdded <= LIST_MAX_DAYS_SINCE_ADDED * DAY_MS &&
                it.dismissedAt < it.lastAdded
        },
        storeId, alreadyOnList, now, limit,
    )

/**
 * Ranks items the household adds often, favoring recent habits and the store this list is for.
 * An item added 10 times three weeks ago scores the same as one added 5 times today.
 * Skips what's already on the list.
 */
fun rankRegulars(
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
