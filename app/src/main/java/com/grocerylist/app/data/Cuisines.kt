package com.grocerylist.app.data

/** Cuisines offered as favorites and for "Tonight". Same list and order as `cuisines` in Logic.swift. */
val CUISINES = listOf(
    "American", "Southern", "Mexican", "Caribbean", "Italian", "French", "Greek",
    "Middle Eastern", "Indian", "Chinese", "Japanese", "Korean", "Thai", "Vietnamese",
)

/** "Tonight I'm feeling x, y, or z." */
const val MAX_MOOD_CUISINES = 3

/** The household's favorite cuisines. [chosen] is false until someone picks (or skips) them. */
data class CuisinePrefs(val cuisines: List<String>, val chosen: Boolean)
