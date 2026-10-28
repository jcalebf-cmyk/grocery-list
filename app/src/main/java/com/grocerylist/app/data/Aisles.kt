package com.grocerylist.app.data

/**
 * Store sections in the order most US grocery stores are walked: produce by the door, the perimeter and
 * center aisles, then dairy and frozen last so cold things stay cold. Same order as `Aisle` in Logic.swift.
 */
enum class Aisle(val label: String) {
    PRODUCE("Produce"),
    BAKERY("Bakery"),
    MEAT("Meat & seafood"),
    PANTRY("Pantry"),
    SNACKS("Snacks"),
    DRINKS("Drinks"),
    HOUSEHOLD("Household"),
    PERSONAL_CARE("Personal care"),
    PET("Pet"),
    DAIRY("Dairy & eggs"),
    FROZEN("Frozen"),
    OTHER("Other"),
}

/** One section of a list. `aisle` is null for the checked-off items, which go last. */
data class ItemSection(val aisle: Aisle?, val items: List<ListItem>)

/**
 * Unchecked items grouped by aisle in walking order, then everything checked off in one last section.
 * Keeps the given order within each section.
 */
fun groupByAisle(items: List<ListItem>): List<ItemSection> {
    val (checked, toGet) = items.partition { it.checked }
    val sections = toGet.groupBy { aisleFor(it.name) }.toSortedMap()
        .map { (aisle, inAisle) -> ItemSection(aisle, inAisle) }
    return if (checked.isEmpty()) sections else sections + ItemSection(null, checked)
}

/**
 * The aisle an item is usually in, from the words in its name. The phrase ending nearest the end wins, since
 * the last word is usually the thing itself ("chicken broth" is broth, "sweet potato fries" are fries), then
 * the longest ("peanut butter" over "butter"). "Frozen", "canned" and "dried" override.
 */
fun aisleFor(name: String): Aisle {
    val words = aisleWords(name).map(::wordForms)
    words.firstNotNullOfOrNull { forms -> forms.firstNotNullOfOrNull { aisleOverrides[it] } }?.let { return it }
    var best: Aisle? = null
    var bestLength = 0
    var bestEnd = 0
    for ((phrase, aisle) in aislePhrases) {
        for (start in 0..words.size - phrase.size) {
            if (phrase.indices.all { phrase[it] in words[start + it] }) {
                val end = start + phrase.size
                if (end > bestEnd || (end == bestEnd && phrase.size > bestLength)) {
                    best = aisle
                    bestLength = phrase.size
                    bestEnd = end
                }
            }
        }
    }
    return best ?: Aisle.OTHER
}

private fun aisleWords(text: String): List<String> =
    itemKey(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

// A word and its likely singulars, so "berries", "tomatoes", "loaves" and "eggs" match "berry", "tomato", "loaf"
// and "egg".
private fun wordForms(word: String): Set<String> = buildSet {
    add(word)
    if (word.length > 3 && word.endsWith("s") && !word.endsWith("ss")) {
        add(word.dropLast(1))
        if (word.endsWith("es")) add(word.dropLast(2))
        if (word.endsWith("ies")) add(word.dropLast(3) + "y")
        if (word.endsWith("ves")) add(word.dropLast(3) + "f")
    }
}

private val aisleOverrides = mapOf("frozen" to Aisle.FROZEN, "canned" to Aisle.PANTRY, "dried" to Aisle.PANTRY)

private val aislePhrases: List<Pair<List<String>, Aisle>> by lazy {
    AISLE_KEYWORDS.flatMap { (aisle, phrases) -> phrases.map { aisleWords(it) to aisle } }
}

// Singular, lowercase. Keep in step with `aisleKeywords` in Logic.swift.
internal val AISLE_KEYWORDS: Map<Aisle, List<String>> = mapOf(
    Aisle.PRODUCE to listOf(
        "apple", "banana", "orange", "lemon", "lime", "grape", "strawberry", "blueberry", "raspberry", "blackberry",
        "berry", "cherry", "peach", "pear", "plum", "nectarine", "apricot", "mango", "pineapple", "kiwi", "melon",
        "watermelon", "cantaloupe", "honeydew", "grapefruit", "clementine", "mandarin", "tangerine", "pomegranate",
        "papaya", "fig", "plantain", "avocado", "tomato", "potato", "yam", "onion", "green onion", "scallion",
        "shallot", "garlic", "ginger", "carrot", "celery", "broccoli", "cauliflower", "cabbage", "lettuce",
        "romaine", "spinach", "kale", "arugula", "chard", "salad", "spring mix", "cucumber", "zucchini", "squash",
        "pumpkin", "eggplant", "pepper", "bell pepper", "jalapeno", "jalapeño", "poblano", "serrano", "mushroom",
        "asparagus", "green bean", "pea", "snap pea", "snow pea", "corn", "beet", "radish", "turnip", "parsnip",
        "leek", "fennel", "artichoke", "brussels sprout", "sprout", "bok choy", "cilantro", "parsley", "basil",
        "mint", "dill", "rosemary", "thyme", "sage", "chive", "herb", "fruit", "vegetable", "veggie", "coconut",
        "tofu", "hummus", "guacamole", "coleslaw",
    ),
    Aisle.BAKERY to listOf(
        "bread", "baguette", "bun", "dinner roll", "hawaiian roll", "crescent roll", "bagel", "croissant", "muffin",
        "english muffin", "tortilla", "pita", "naan", "flatbread", "sourdough", "loaf", "brioche", "biscuit", "cake",
        "cupcake", "pie", "donut", "doughnut", "pastry",
    ),
    Aisle.MEAT to listOf(
        "beef", "ground beef", "steak", "sirloin", "ribeye", "brisket", "rib", "roast", "hamburger", "chicken",
        "thigh", "drumstick", "wing", "turkey", "pork", "pork chop", "tenderloin", "bacon", "sausage", "ham", "lamb",
        "veal", "bison", "salami", "pepperoni", "prosciutto", "hot dog", "bratwurst", "chorizo", "meatball",
        "deli meat", "lunch meat", "meat", "fish", "salmon", "cod", "tilapia", "halibut", "trout", "shrimp",
        "scallop", "crab", "lobster", "mussel", "clam", "oyster", "seafood",
    ),
    Aisle.PANTRY to listOf(
        "rice", "pasta", "spaghetti", "penne", "macaroni", "linguine", "fettuccine", "rotini", "orzo", "tortellini",
        "ravioli", "gnocchi", "mac and cheese", "mac & cheese", "macaroni and cheese", "noodle", "ramen", "lasagna",
        "quinoa", "couscous", "flour", "sugar", "baking soda", "baking powder", "yeast", "cornstarch", "starch",
        "vanilla", "extract", "chocolate chip", "cocoa", "oil", "vinegar", "salt", "black pepper", "white pepper",
        "lemon pepper", "peppercorn", "pepper flake", "salt and pepper", "spice", "seasoning", "powder", "cumin",
        "paprika", "oregano", "cinnamon", "nutmeg", "turmeric", "cayenne", "allspice", "coriander", "curry",
        "garam masala", "bay leaf", "sumac", "za'atar", "herbes de provence", "sauce", "marinara", "salsa",
        "ketchup", "mustard", "mayo", "mayonnaise", "relish", "dressing", "honey", "syrup", "jam", "jelly",
        "peanut butter", "almond butter", "nutella", "applesauce", "broth", "stock", "bouillon", "soup", "bean",
        "chickpea", "lentil", "paste", "diced tomato", "crushed tomato", "tuna", "olive", "pickle", "caper",
        "coconut milk", "evaporated milk", "condensed milk", "lemon juice", "lime juice", "cereal", "oatmeal", "oat",
        "granola", "pancake mix", "cake mix", "brownie mix", "bread crumb", "breadcrumb", "panko", "taco shell",
        "tahini", "gochujang", "miso", "mirin", "sake", "gochugaru", "adobo", "worcestershire", "sriracha",
        "molasses", "seed", "marshmallow", "sprinkle", "pudding",
    ),
    Aisle.SNACKS to listOf(
        "chip", "cracker", "pretzel", "popcorn", "rice cake", "cookie", "candy", "chocolate", "granola bar",
        "protein bar", "snack bar", "fruit snack", "nut", "peanut", "almond", "cashew", "pistachio", "walnut",
        "pecan", "trail mix", "jerky", "raisin", "dip", "snack", "gum",
    ),
    Aisle.DRINKS to listOf(
        "water", "sparkling water", "seltzer", "juice", "soda", "lemonade", "coffee", "tea", "tea bag",
        "coffee bean", "kombucha", "beer", "wine", "sports drink", "energy drink", "drink", "beverage", "coke",
        "pepsi", "gatorade", "la croix",
    ),
    Aisle.HOUSEHOLD to listOf(
        "paper towel", "toilet paper", "tissue", "kleenex", "napkin", "trash bag", "garbage bag", "bag", "foil",
        "plastic wrap", "parchment", "dish soap", "soap", "detergent", "dishwasher", "laundry", "bleach",
        "fabric softener", "dryer sheet", "cleaner", "sponge", "wipe", "paper plate", "paper cup", "light bulb",
        "battery", "candle", "coffee filter", "ziploc", "clorox", "lysol", "windex",
    ),
    Aisle.PERSONAL_CARE to listOf(
        "shampoo", "conditioner", "body wash", "face wash", "toothpaste", "toothbrush", "floss", "mouthwash",
        "deodorant", "razor", "shaving cream", "lotion", "sunscreen", "tampon", "vitamin", "medicine", "ibuprofen",
        "tylenol", "advil", "band aid", "bandaid", "bandage", "diaper", "baby wipe", "formula", "cotton swab",
        "lip balm", "hand sanitizer", "cough drop", "q tip", "chapstick",
    ),
    Aisle.PET to listOf(
        "dog", "cat", "pet", "litter", "kibble",
    ),
    Aisle.DAIRY to listOf(
        "milk", "egg", "butter", "cheese", "cream", "sour cream", "heavy cream", "half and half", "half & half",
        "creamer", "yogurt", "kefir", "buttermilk", "margarine", "mozzarella", "cheddar", "parmesan", "feta",
        "ricotta", "provolone", "brie", "gouda",
    ),
    Aisle.FROZEN to listOf(
        "ice cream", "ice", "pizza", "popsicle", "waffle", "tater tot", "french fry", "fry", "hash brown", "nugget",
        "sorbet", "gelato", "burrito", "edamame",
    ),
)
