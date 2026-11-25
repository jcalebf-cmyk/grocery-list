import Foundation

/// The color behind a recipe card's emoji. The screens pick the light and dark shades.
enum RecipeTint { case red, orange, yellow, green, pink, blue, tan }

struct RecipeEmoji: Equatable {
    let emoji: String
    let tint: RecipeTint
}

private let plate = RecipeEmoji(emoji: "🍽️", tint: .tan)

/// A food emoji for a recipe's title: the kind of dish if it names one, else what it's mostly made of. The last one named
/// wins ("taco salad" is a salad), and what comes after "with" only counts if nothing before it does. A plate if nothing
/// matches. Same as `recipeEmoji` in RecipeVisual.kt.
func recipeEmoji(_ title: String) -> RecipeEmoji {
    let words = searchWords(title)
    let head = Array(words.prefix { !sideWords.contains($0) })
    for part in [head, words] {
        let forms = part.map(wordForms)
        for table in [dishes, mains, ingredients] {
            for end in stride(from: forms.count, to: 0, by: -1) {
                if let match = table.first(where: { ends(forms, end, $0.phrase) }) { return match.emoji }
            }
        }
    }
    return plate
}

/// The video's thumbnail, for a recipe that came from YouTube. Same as `videoThumbnail` in RecipeVisual.kt.
func videoThumbnail(_ sourceUrl: String) -> URL? {
    guard let match = sourceUrl.firstMatch(of: videoID) else { return nil }
    return URL(string: "https://i.ytimg.com/vi/\(match.output.1)/hqdefault.jpg")
}

private let videoID = #/(?:youtu\.be/|youtube\.com/(?:watch\?(?:[^#\s]*&)?v=|shorts/|live/|embed/))([A-Za-z0-9_-]{11})/#

private let sideWords: Set<String> = ["with", "in", "on", "over"]

/// `phrase` is the last of the words before `end`.
private func ends(_ forms: [Set<String>], _ end: Int, _ phrase: [String]) -> Bool {
    phrase.count <= end && phrase.indices.allSatisfy { forms[end - phrase.count + $0].contains(phrase[$0]) }
}

private func emojiTable(_ rows: [(String, RecipeTint, String)]) -> [(phrase: [String], emoji: RecipeEmoji)] {
    let all = rows.flatMap { emoji, tint, phrases in
        phrases.components(separatedBy: ", ").map { (phrase: $0.components(separatedBy: " "), emoji: RecipeEmoji(emoji: emoji, tint: tint)) }
    }
    // "Sweet potato" before "potato". Grouped rather than sorted, which needn't keep the order of equals.
    return [3, 2, 1].flatMap { count in all.filter { $0.phrase.count == count } }
}

// Singular, lowercase, no accents; emoji old enough for Android 8. Keep in step with RecipeVisual.kt.

// Kinds of dish, which say more about a recipe than what's in it: a chicken soup is a soup.
private let dishes = emojiTable([
    ("🍝", .red, "pasta, spaghetti, penne, linguine, fettuccine, lasagna, ravioli, gnocchi, macaroni, carbonara, bolognese, alfredo, rigatoni, ziti, orzo, mac and cheese, mac cheese, tortellini"),
    ("🍜", .red, "ramen, noodle, pho, lo mein, pad thai, udon, soba, laksa, chow mein, yakisoba"),
    ("🍕", .red, "pizza, flatbread, calzone, stromboli"),
    ("🍔", .orange, "burger, hamburger, cheeseburger, slider, sloppy joe"),
    ("🌭", .orange, "hot dog, bratwurst, corn dog"),
    ("🌮", .yellow, "taco, fajita, quesadilla, tostada, nacho, carnitas, enchilada, tamale"),
    ("🌯", .yellow, "burrito, wrap, shawarma"),
    ("🥙", .green, "gyro, pita, falafel, kebab, kabob, souvlaki"),
    ("🥪", .tan, "sandwich, panini, grilled cheese, melt, sub, hoagie, blt, cheesesteak, lobster roll"),
    ("🥗", .green, "salad, slaw, coleslaw, tabbouleh"),
    ("🍲", .orange, "soup, stew, chowder, bisque, gumbo, broth, minestrone, pozole, jambalaya, pot roast, goulash, ratatouille"),
    ("🍛", .orange, "curry, masala, korma, dal, dahl, vindaloo, biryani"),
    ("🥘", .orange, "paella, casserole, tagine, stir fry, stirfry, skillet, risotto"),
    ("🍣", .blue, "sushi, poke, sashimi, sushi roll, california roll"),
    ("🥟", .tan, "dumpling, potsticker, gyoza, wonton, pierogi, empanada, samosa, egg roll, spring roll, summer roll"),
    ("🥞", .yellow, "pancake, waffle, crepe, french toast"),
    ("🍳", .yellow, "omelet, omelette, frittata, quiche, shakshuka, scramble, hash, hash brown"),
    ("🥣", .tan, "oatmeal, porridge, granola, oat, chia pudding, congee, grits"),
    ("🍞", .tan, "bread, cornbread, toast, loaf, focaccia, biscuit, roll, naan, bun, scone, muffin, bagel, sourdough"),
    ("🥐", .tan, "croissant, pastry, danish, strudel"),
    ("🍪", .tan, "cookie, brownie, biscotti, shortbread, blondie, snickerdoodle"),
    ("🍰", .pink, "cake, cupcake, cheesecake, tiramisu, shortcake, trifle"),
    ("🥧", .tan, "pie, tart, cobbler, crumble, crisp, galette, pot pie"),
    ("🍮", .yellow, "pudding, custard, flan, brulee, panna cotta, caramel"),
    ("🍨", .pink, "ice cream, gelato, sorbet, sundae, frozen yogurt, milkshake"),
    ("🍩", .pink, "donut, doughnut, fritter, beignet"),
    ("🍫", .tan, "fudge, truffle, mousse, brittle"),
    ("🥤", .pink, "smoothie, shake, lemonade, juice, agua fresca"),
    ("🍹", .orange, "cocktail, mojito, sangria, punch, daiquiri, spritz"),
    ("🥑", .green, "guacamole, avocado toast"),
    ("🍿", .yellow, "popcorn"),
    ("🍟", .yellow, "fries"),
    ("🦀", .blue, "crab cake"),
    ("🐟", .blue, "fish cake, fish stick"),
    ("🥩", .red, "meatloaf, meat loaf"),
    ("☕", .tan, "hot chocolate, hot cocoa"),
])

// What a dish without a kind is mostly made of.
private let mains = emojiTable([
    ("🍗", .orange, "chicken, wing, drumstick, duck"),
    ("🦃", .orange, "turkey"),
    ("🥩", .red, "steak, beef, brisket, lamb, sirloin, ribeye, short rib, roast, bulgogi"),
    ("🍖", .red, "rib, pork, meatball, ham, sausage, carnita, barbecue, bbq, chop, tenderloin"),
    ("🥓", .red, "bacon, pancetta"),
    ("🐟", .blue, "fish, salmon, tuna, cod, tilapia, halibut, trout, snapper, mahi, sardine, anchovy, catfish, seafood"),
    ("🦐", .blue, "shrimp, prawn, scampi, lobster, scallop"),
    ("🦀", .blue, "crab"),
    ("🦑", .blue, "squid, calamari, octopus"),
    ("🥚", .yellow, "egg"),
    ("🌶️", .red, "chili, chile, chilli"),
    ("🍚", .tan, "rice, pilaf, quinoa, couscous"),
])

// Everything else.
private let ingredients = emojiTable([
    ("🥔", .tan, "potato, tater"),
    ("🍠", .orange, "sweet potato, yam, squash, pumpkin"),
    ("🥦", .green, "broccoli, cauliflower, vegetable, veggie"),
    ("🥕", .orange, "carrot"),
    ("🌽", .yellow, "corn, polenta"),
    ("🍅", .red, "tomato, salsa, marinara"),
    ("🍆", .pink, "eggplant"),
    ("🥒", .green, "cucumber, pickle, zucchini"),
    ("🍄", .tan, "mushroom"),
    ("🥑", .green, "avocado"),
    ("🌱", .green, "spinach, kale, lettuce, cabbage, brussels sprout, asparagus, green bean, pea, edamame, herb, pesto, basil, tofu"),
    ("🥫", .red, "bean, lentil, chickpea, hummus, sauce, gravy, dressing, dip"),
    ("🧀", .yellow, "cheese, fondue, queso, paneer"),
    ("🥜", .tan, "peanut, nut, almond, cashew, pecan, walnut, peanut butter"),
    ("🥥", .tan, "coconut"),
    ("🍎", .red, "apple"),
    ("🍌", .yellow, "banana, plantain"),
    ("🍓", .pink, "strawberry, berry, jam, jelly"),
    ("🍒", .pink, "cherry"),
    ("🍇", .pink, "blueberry, blackberry, raspberry, grape"),
    ("🍋", .yellow, "lemon, lime, citrus"),
    ("🍊", .orange, "orange, tangerine"),
    ("🍑", .orange, "peach, apricot, mango, nectarine"),
    ("🍍", .yellow, "pineapple"),
    ("🍉", .red, "watermelon, melon"),
    ("🍐", .green, "pear"),
    ("🍫", .tan, "chocolate, cocoa"),
    ("🍯", .yellow, "honey, syrup"),
    ("☕", .tan, "coffee, espresso, latte, mocha, tea, chai"),
])
