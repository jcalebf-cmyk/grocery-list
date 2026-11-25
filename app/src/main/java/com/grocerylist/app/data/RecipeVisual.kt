package com.grocerylist.app.data

/** The color behind a recipe card's emoji. The screens pick the light and dark shades. */
enum class RecipeTint { RED, ORANGE, YELLOW, GREEN, PINK, BLUE, TAN }

data class RecipeEmoji(val emoji: String, val tint: RecipeTint)

private val PLATE = RecipeEmoji("🍽️", RecipeTint.TAN)

/**
 * A food emoji for a recipe's title: the kind of dish if it names one, else what it's mostly made of. The last one named
 * wins ("taco salad" is a salad), and what comes after "with" only counts if nothing before it does. A plate if nothing
 * matches. Same as `recipeEmoji` in RecipeVisual.swift.
 */
fun recipeEmoji(title: String): RecipeEmoji {
    val words = searchWords(title)
    val head = words.takeWhile { it !in SIDE_WORDS }
    for (part in listOf(head, words)) {
        for (table in listOf(DISHES, MAINS, INGREDIENTS)) {
            for (end in part.size downTo 1) {
                table.firstOrNull { (phrase, _) -> endsWith(part, end, phrase) }?.let { return it.second }
            }
        }
    }
    return PLATE
}

/** The video's thumbnail, for a recipe that came from YouTube. Same as `videoThumbnail` in RecipeVisual.swift. */
fun videoThumbnail(sourceUrl: String): String? =
    VIDEO_ID.find(sourceUrl)?.let { "https://i.ytimg.com/vi/${it.groupValues[1]}/hqdefault.jpg" }

private val VIDEO_ID = Regex("(?:youtu\\.be/|youtube\\.com/(?:watch\\?(?:[^#\\s]*&)?v=|shorts/|live/|embed/))([A-Za-z0-9_-]{11})")

private val SIDE_WORDS = setOf("with", "in", "on", "over")

/** [phrase] is the last of the words before [end]. */
private fun endsWith(words: List<String>, end: Int, phrase: List<String>): Boolean =
    phrase.size <= end && phrase.indices.all { phrase[it] in wordForms(words[end - phrase.size + it]) }

private fun emojiTable(vararg rows: Triple<String, RecipeTint, String>): List<Pair<List<String>, RecipeEmoji>> =
    rows.flatMap { (emoji, tint, phrases) -> phrases.split(", ").map { it.split(" ") to RecipeEmoji(emoji, tint) } }
        // "Sweet potato" before "potato".
        .sortedByDescending { it.first.size }

// Singular, lowercase, no accents; emoji old enough for Android 8. Keep in step with RecipeVisual.swift.

// Kinds of dish, which say more about a recipe than what's in it: a chicken soup is a soup.
private val DISHES = emojiTable(
    Triple("🍝", RecipeTint.RED, "pasta, spaghetti, penne, linguine, fettuccine, lasagna, ravioli, gnocchi, macaroni, carbonara, bolognese, alfredo, rigatoni, ziti, orzo, mac and cheese, mac cheese, tortellini"),
    Triple("🍜", RecipeTint.RED, "ramen, noodle, pho, lo mein, pad thai, udon, soba, laksa, chow mein, yakisoba"),
    Triple("🍕", RecipeTint.RED, "pizza, flatbread, calzone, stromboli"),
    Triple("🍔", RecipeTint.ORANGE, "burger, hamburger, cheeseburger, slider, sloppy joe"),
    Triple("🌭", RecipeTint.ORANGE, "hot dog, bratwurst, corn dog"),
    Triple("🌮", RecipeTint.YELLOW, "taco, fajita, quesadilla, tostada, nacho, carnitas, enchilada, tamale"),
    Triple("🌯", RecipeTint.YELLOW, "burrito, wrap, shawarma"),
    Triple("🥙", RecipeTint.GREEN, "gyro, pita, falafel, kebab, kabob, souvlaki"),
    Triple("🥪", RecipeTint.TAN, "sandwich, panini, grilled cheese, melt, sub, hoagie, blt, cheesesteak, lobster roll"),
    Triple("🥗", RecipeTint.GREEN, "salad, slaw, coleslaw, tabbouleh"),
    Triple("🍲", RecipeTint.ORANGE, "soup, stew, chowder, bisque, gumbo, broth, minestrone, pozole, jambalaya, pot roast, goulash, ratatouille"),
    Triple("🍛", RecipeTint.ORANGE, "curry, masala, korma, dal, dahl, vindaloo, biryani"),
    Triple("🥘", RecipeTint.ORANGE, "paella, casserole, tagine, stir fry, stirfry, skillet, risotto"),
    Triple("🍣", RecipeTint.BLUE, "sushi, poke, sashimi, sushi roll, california roll"),
    Triple("🥟", RecipeTint.TAN, "dumpling, potsticker, gyoza, wonton, pierogi, empanada, samosa, egg roll, spring roll, summer roll"),
    Triple("🥞", RecipeTint.YELLOW, "pancake, waffle, crepe, french toast"),
    Triple("🍳", RecipeTint.YELLOW, "omelet, omelette, frittata, quiche, shakshuka, scramble, hash, hash brown"),
    Triple("🥣", RecipeTint.TAN, "oatmeal, porridge, granola, oat, chia pudding, congee, grits"),
    Triple("🍞", RecipeTint.TAN, "bread, cornbread, toast, loaf, focaccia, biscuit, roll, naan, bun, scone, muffin, bagel, sourdough"),
    Triple("🥐", RecipeTint.TAN, "croissant, pastry, danish, strudel"),
    Triple("🍪", RecipeTint.TAN, "cookie, brownie, biscotti, shortbread, blondie, snickerdoodle"),
    Triple("🍰", RecipeTint.PINK, "cake, cupcake, cheesecake, tiramisu, shortcake, trifle"),
    Triple("🥧", RecipeTint.TAN, "pie, tart, cobbler, crumble, crisp, galette, pot pie"),
    Triple("🍮", RecipeTint.YELLOW, "pudding, custard, flan, brulee, panna cotta, caramel"),
    Triple("🍨", RecipeTint.PINK, "ice cream, gelato, sorbet, sundae, frozen yogurt, milkshake"),
    Triple("🍩", RecipeTint.PINK, "donut, doughnut, fritter, beignet"),
    Triple("🍫", RecipeTint.TAN, "fudge, truffle, mousse, brittle"),
    Triple("🥤", RecipeTint.PINK, "smoothie, shake, lemonade, juice, agua fresca"),
    Triple("🍹", RecipeTint.ORANGE, "cocktail, mojito, sangria, punch, daiquiri, spritz"),
    Triple("🥑", RecipeTint.GREEN, "guacamole, avocado toast"),
    Triple("🍿", RecipeTint.YELLOW, "popcorn"),
    Triple("🍟", RecipeTint.YELLOW, "fries"),
    Triple("🦀", RecipeTint.BLUE, "crab cake"),
    Triple("🐟", RecipeTint.BLUE, "fish cake, fish stick"),
    Triple("🥩", RecipeTint.RED, "meatloaf, meat loaf"),
    Triple("☕", RecipeTint.TAN, "hot chocolate, hot cocoa"),
)

// What a dish without a kind is mostly made of.
private val MAINS = emojiTable(
    Triple("🍗", RecipeTint.ORANGE, "chicken, wing, drumstick, duck"),
    Triple("🦃", RecipeTint.ORANGE, "turkey"),
    Triple("🥩", RecipeTint.RED, "steak, beef, brisket, lamb, sirloin, ribeye, short rib, roast, bulgogi"),
    Triple("🍖", RecipeTint.RED, "rib, pork, meatball, ham, sausage, carnita, barbecue, bbq, chop, tenderloin"),
    Triple("🥓", RecipeTint.RED, "bacon, pancetta"),
    Triple("🐟", RecipeTint.BLUE, "fish, salmon, tuna, cod, tilapia, halibut, trout, snapper, mahi, sardine, anchovy, catfish, seafood"),
    Triple("🦐", RecipeTint.BLUE, "shrimp, prawn, scampi, lobster, scallop"),
    Triple("🦀", RecipeTint.BLUE, "crab"),
    Triple("🦑", RecipeTint.BLUE, "squid, calamari, octopus"),
    Triple("🥚", RecipeTint.YELLOW, "egg"),
    Triple("🌶️", RecipeTint.RED, "chili, chile, chilli"),
    Triple("🍚", RecipeTint.TAN, "rice, pilaf, quinoa, couscous"),
)

// Everything else.
private val INGREDIENTS = emojiTable(
    Triple("🥔", RecipeTint.TAN, "potato, tater"),
    Triple("🍠", RecipeTint.ORANGE, "sweet potato, yam, squash, pumpkin"),
    Triple("🥦", RecipeTint.GREEN, "broccoli, cauliflower, vegetable, veggie"),
    Triple("🥕", RecipeTint.ORANGE, "carrot"),
    Triple("🌽", RecipeTint.YELLOW, "corn, polenta"),
    Triple("🍅", RecipeTint.RED, "tomato, salsa, marinara"),
    Triple("🍆", RecipeTint.PINK, "eggplant"),
    Triple("🥒", RecipeTint.GREEN, "cucumber, pickle, zucchini"),
    Triple("🍄", RecipeTint.TAN, "mushroom"),
    Triple("🥑", RecipeTint.GREEN, "avocado"),
    Triple("🌱", RecipeTint.GREEN, "spinach, kale, lettuce, cabbage, brussels sprout, asparagus, green bean, pea, edamame, herb, pesto, basil, tofu"),
    Triple("🥫", RecipeTint.RED, "bean, lentil, chickpea, hummus, sauce, gravy, dressing, dip"),
    Triple("🧀", RecipeTint.YELLOW, "cheese, fondue, queso, paneer"),
    Triple("🥜", RecipeTint.TAN, "peanut, nut, almond, cashew, pecan, walnut, peanut butter"),
    Triple("🥥", RecipeTint.TAN, "coconut"),
    Triple("🍎", RecipeTint.RED, "apple"),
    Triple("🍌", RecipeTint.YELLOW, "banana, plantain"),
    Triple("🍓", RecipeTint.PINK, "strawberry, berry, jam, jelly"),
    Triple("🍒", RecipeTint.PINK, "cherry"),
    Triple("🍇", RecipeTint.PINK, "blueberry, blackberry, raspberry, grape"),
    Triple("🍋", RecipeTint.YELLOW, "lemon, lime, citrus"),
    Triple("🍊", RecipeTint.ORANGE, "orange, tangerine"),
    Triple("🍑", RecipeTint.ORANGE, "peach, apricot, mango, nectarine"),
    Triple("🍍", RecipeTint.YELLOW, "pineapple"),
    Triple("🍉", RecipeTint.RED, "watermelon, melon"),
    Triple("🍐", RecipeTint.GREEN, "pear"),
    Triple("🍫", RecipeTint.TAN, "chocolate, cocoa"),
    Triple("🍯", RecipeTint.YELLOW, "honey, syrup"),
    Triple("☕", RecipeTint.TAN, "coffee, espresso, latte, mocha, tea, chai"),
)
