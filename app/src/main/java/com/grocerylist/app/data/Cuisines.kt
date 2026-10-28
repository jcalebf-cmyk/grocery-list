package com.grocerylist.app.data

/** Cuisines offered as favorites and for "Tonight". Same list and order as `cuisines` in Logic.swift. */
val CUISINES = listOf(
    "American", "Southern", "Mexican", "Caribbean", "Italian", "French", "Greek",
    "Middle Eastern", "Indian", "Chinese", "Japanese", "Korean", "Thai", "Vietnamese",
)

/** Seasonings and sauces each cuisine leans on, for pantry quick adds. Same as `cuisinePantry` in Logic.swift. */
val CUISINE_PANTRY = mapOf(
    "American" to listOf("BBQ sauce", "Worcestershire sauce", "Garlic powder", "Paprika", "Mustard", "Ketchup"),
    "Southern" to listOf("Cajun seasoning", "Hot sauce", "Paprika", "Garlic powder", "Onion powder", "Cayenne"),
    "Mexican" to listOf("Cumin", "Chili powder", "Dried oregano", "Chipotles in adobo", "Salsa", "Taco seasoning"),
    "Caribbean" to listOf("Jerk seasoning", "Allspice", "Dried thyme", "Hot sauce", "Curry powder", "Coconut milk"),
    "Italian" to listOf("Dried oregano", "Dried basil", "Red pepper flakes", "Balsamic vinegar", "Canned tomatoes", "Parmesan"),
    "French" to listOf("Dijon mustard", "Dried thyme", "Herbes de Provence", "Red wine vinegar", "White wine", "Bay leaves"),
    "Greek" to listOf("Dried oregano", "Dried dill", "Red wine vinegar", "Kalamata olives", "Cinnamon", "Lemon pepper"),
    "Middle Eastern" to listOf("Cumin", "Za'atar", "Sumac", "Tahini", "Cinnamon", "Smoked paprika"),
    "Indian" to listOf("Garam masala", "Turmeric", "Cumin", "Curry powder", "Ground coriander", "Coconut milk"),
    "Chinese" to listOf("Soy sauce", "Oyster sauce", "Sesame oil", "Rice vinegar", "Hoisin sauce", "Shaoxing wine"),
    "Japanese" to listOf("Soy sauce", "Mirin", "Cooking sake", "Miso", "Rice vinegar", "Sesame oil"),
    "Korean" to listOf("Gochujang", "Soy sauce", "Sesame oil", "Gochugaru", "Rice vinegar", "Sesame seeds"),
    "Thai" to listOf("Fish sauce", "Red curry paste", "Coconut milk", "Soy sauce", "Sriracha", "Oyster sauce"),
    "Vietnamese" to listOf("Fish sauce", "Hoisin sauce", "Sriracha", "Rice vinegar", "Soy sauce", "Lemongrass paste"),
)

/** Every kitchen's basics, offered first. */
val BASIC_PANTRY = listOf("Salt", "Black pepper")

/** Pantry quick adds for households that haven't picked cuisines, and to fill out the row. */
val COMMON_PANTRY = listOf(
    "Soy sauce", "Garlic powder", "Paprika", "Cumin", "Dried oregano", "Red pepper flakes",
    "Honey", "Dijon mustard", "Hot sauce", "Rice vinegar", "Chili powder", "Sesame oil",
)

/** "Tonight I'm feeling x, y, or z." */
const val MAX_MOOD_CUISINES = 3

/** The household's favorite cuisines. [chosen] is false until someone picks (or skips) them. */
data class CuisinePrefs(val cuisines: List<String>, val chosen: Boolean)
