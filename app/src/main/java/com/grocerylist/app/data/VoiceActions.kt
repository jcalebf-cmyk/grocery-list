package com.grocerylist.app.data

/*
 * What the voice actions (Gemini on Android, Siri on iPhone) decide and say. Kept free of Firebase so it can be
 * unit-tested. Same rules and wording as ios/Groceries/Intents/VoiceLogic.swift: change both together.
 */

object VoiceText {
    const val SIGNED_OUT = "Open Groceries and sign in first."
    const val NO_HOUSEHOLD = "Open Groceries and set up your household first."
    const val NO_OPEN_LISTS = "You don't have any open lists."
    const val WHICH_LIST = "Which list?"
    const val NOTHING_TO_ADD = "What should I add?"
    const val MOST_READ = 10

    /** "your Costco list", or "your shopping list" for the list that isn't tied to a store. */
    fun yourList(storeName: String) = if (storeName.isBlank()) "your shopping list" else "your $storeName list"

    /** "a Costco list" / "a shopping list". */
    fun aList(storeName: String) = if (storeName.isBlank()) "a shopping list" else "a $storeName list"

    /** Asked when nobody named a list and several are open: "Which list: Costco, Trader Joe's or shopping?" */
    fun whichList(lists: List<GroceryList>): String {
        val names = lists.map { it.storeName.ifBlank { "shopping" } }
        return "Which list: " + (if (names.size < 2) names.joinToString() else names.dropLast(1).joinToString(", ") + " or " + names.last()) + "?"
    }

    fun noOpenList(storeName: String) =
        if (storeName.isBlank()) "There's no open shopping list." else "There's no open $storeName list."

    fun added(added: List<String>, alreadyThere: List<String>, storeName: String): String {
        val list = yourList(storeName)
        val skipped = alreadyThere.takeIf { it.isNotEmpty() }?.let { names ->
            "${spokenList(names).capitalized()} ${if (names.size == 1) "was" else "were"} already on it."
        }
        return when {
            added.isEmpty() -> "${spokenList(alreadyThere).capitalized()} ${if (alreadyThere.size == 1) "is" else "are"} already on $list."
            skipped == null -> "Added ${spokenList(added)} to $list."
            else -> "Added ${spokenList(added)} to $list. $skipped"
        }
    }

    /** Reads out what's still to get, up to [MOST_READ] items. */
    fun readOut(items: List<ListItem>, storeName: String): String {
        val list = yourList(storeName).capitalized()
        val left = items.filter { !it.checked }.sortedBy { it.createdAt }.map { it.name }
        return when {
            items.isEmpty() -> "$list is empty."
            left.isEmpty() -> "Everything on ${yourList(storeName)} is checked off."
            left.size <= MOST_READ -> "$list has ${spokenList(left)}."
            else -> "$list has ${left.take(MOST_READ).joinToString(", ")} and ${left.size - MOST_READ} more."
        }
    }

    fun started(storeName: String, alreadyGoing: Boolean) =
        if (alreadyGoing) "You already have ${aList(storeName)} going." else "Started ${aList(storeName)}."

    fun checkedOff(name: String, storeName: String) = "Checked off $name on ${yourList(storeName)}."
    fun alreadyChecked(name: String) = "${name.capitalized()} is already checked off."
    fun notOnList(name: String, storeName: String) = "${name.trim().capitalized()} isn't on ${yourList(storeName)}."
    fun noRecipe(name: String) = "There's no recipe called ${name.trim()} in your recipe box."
    fun openingRecipe(title: String) = "Here's your $title recipe."

    /** "milk", "milk and eggs", "milk, eggs and bread". */
    fun spokenList(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }

    private fun String.capitalized() = replaceFirstChar { it.uppercase() }
}

/**
 * Items said in one go. Splits on commas (and new lines) only, since "mac and cheese" is one item.
 * Drops blanks and repeats.
 */
fun spokenItems(text: String): List<String> =
    text.split(',', ';', '\n').map { it.trim().trimEnd('.') }.filter { it.isNotEmpty() }.distinctBy(::itemKey)

/** For matching what someone said: lowercase, no apostrophes or punctuation, single spaces. */
fun spokenKey(text: String): String =
    text.lowercase().replace(Regex("['’`]"), "").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

/** Names that mean the list that isn't tied to a store. */
fun meansPlainList(name: String): Boolean =
    spokenKey(name).removeSuffix(" list").removeSuffix(" lists").trim() in setOf("shopping", "grocery", "groceries", "plain", "")

/** The saved store someone meant: the same name first, then one name containing the other ("Joe's" → "Trader Joe's"). */
fun findStore(stores: List<Store>, name: String): Store? {
    val said = spokenKey(name).removeSuffix(" list").trim()
    if (said.isEmpty()) return null
    return stores.firstOrNull { spokenKey(it.name) == said }
        ?: stores.firstOrNull { spokenKey(it.name).contains(said) }
        ?: stores.firstOrNull { said.contains(spokenKey(it.name)) && spokenKey(it.name).isNotEmpty() }
}

/** The open list for a store ("" = the list that isn't tied to one). */
fun openListFor(activeLists: List<GroceryList>, storeId: String): GroceryList? =
    activeLists.firstOrNull { it.storeId == storeId }

/** Which list to use when nobody named one. */
sealed interface ListPick {
    data class One(val list: GroceryList) : ListPick
    data object NoneOpen : ListPick
    data class Several(val lists: List<GroceryList>) : ListPick
}

fun pickList(activeLists: List<GroceryList>): ListPick = when (activeLists.size) {
    0 -> ListPick.NoneOpen
    1 -> ListPick.One(activeLists[0])
    else -> ListPick.Several(activeLists)
}

/** The list item someone meant: the same name, then singular/plural ("eggs" for "egg"), then a partial match. */
fun findItem(items: List<ListItem>, name: String): ListItem? {
    val said = spokenKey(name)
    if (said.isEmpty()) return null
    val ordered = items.sortedBy { it.checked } // unchecked first
    fun singular(s: String) = s.removeSuffix("es").takeIf { s.endsWith("oes") } ?: s.removeSuffix("s")
    return ordered.firstOrNull { spokenKey(it.name) == said }
        ?: ordered.firstOrNull { singular(spokenKey(it.name)) == singular(said) }
        ?: ordered.firstOrNull { spokenKey(it.name).contains(said) }
}

/** Recipes whose title has every word said, closest title first. */
fun findRecipes(recipes: List<Recipe>, name: String): List<Recipe> {
    val said = spokenKey(name).removeSuffix(" recipe").trim()
    if (said.isEmpty()) return emptyList()
    val words = said.split(' ')
    return recipes
        .filter { recipe -> spokenKey(recipe.title).let { title -> words.all { title.contains(it) } } }
        .sortedWith(compareBy({ spokenKey(it.title) != said }, { it.title.length }))
}
