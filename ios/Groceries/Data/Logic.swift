import Foundation

// Pure logic shared in spirit with the Android app (Suggestions.kt, IngredientMatch.kt,
// StoreBrands.kt, Models.kt, Aisles.kt). Keep the two in step so both platforms behave the same.

/// "  Whole  Milk " and "whole milk" are the same item. Slashes aren't allowed in document IDs.
func itemKey(_ name: String) -> String {
    name.trimmingCharacters(in: .whitespacesAndNewlines)
        .lowercased()
        .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        .replacingOccurrences(of: "/", with: "-")
}

private let dayMs = 24.0 * 60 * 60 * 1000
private let halfLifeDays = 21.0
private let minTimesAdded: Int64 = 2

/// Ranks items the household adds often, favoring recent habits and the store this list is for.
/// Skips what's already on the list, and what's in My kitchen (`onHand` names), matched like recipe
/// ingredients so "Chicken" in the kitchen also skips "Chicken breast".
func suggestItems(_ stats: [ItemStat], storeId: String, alreadyOnList: Set<String>, now: Int64,
                  onHand: [String] = [], limit: Int = 12) -> [ItemStat] {
    stats
        .filter { $0.count >= minTimesAdded && !alreadyOnList.contains($0.key) && !isCoveredBy($0.displayName, onHand: onHand) }
        .map { stat -> (ItemStat, Double) in
            let daysSince = max(0, Double(now - stat.lastAdded) / dayMs)
            let recency = pow(0.5, daysSince / halfLifeDays)
            let atThisStore = Double(stat.storeCounts?[storeId] ?? 0)
            return (stat, (Double(stat.count) + 2 * atThisStore) * recency)
        }
        .sorted { $0.1 > $1.1 }
        .prefix(limit)
        .map(\.0)
}

private let commonKitchenItems = [
    "Chicken breast", "Ground beef", "Salmon", "Steak", "Pork chops", "Eggs",
    "Rice", "Pasta", "Potatoes", "Onions", "Black beans", "Frozen vegetables",
]

/// Quick-add chips for My kitchen: the household's regulars first, then common staples.
func kitchenQuickAdds(_ stats: [ItemStat], inKitchen: Set<String>, now: Int64, limit: Int = 12) -> [String] {
    let regulars = suggestItems(stats, storeId: "", alreadyOnList: inKitchen, now: now, limit: 8).map(\.displayName)
    var seen = inKitchen
    return (regulars + commonKitchenItems).filter { seen.insert(itemKey($0)).inserted }.prefix(limit).map { $0 }
}

/// Quick-add chips for the pantry: salt and pepper, then seasonings from the household's favorite cuisines,
/// taking turns so each cuisine gets its essentials in early, then common ones. Skips anything already in the kitchen.
func pantryQuickAdds(cuisines picked: [String], inKitchen: Set<String>, limit: Int = 12) -> [String] {
    let lists = picked.compactMap { cuisinePantry[$0] }
    let longest = lists.map(\.count).max() ?? 0
    let alternating = (0..<longest).flatMap { i in lists.compactMap { i < $0.count ? $0[i] : nil } }
    var seen = inKitchen
    return (basicPantry + alternating + commonPantry).filter { seen.insert(itemKey($0)).inserted }.prefix(limit).map { $0 }
}

/// Stores to offer under "Your stores": only ones the household has made a list for, most recent first.
func storesInUse(_ stores: [Store], lists: [GroceryList]) -> [Store] {
    var lastUsed: [String: Int64] = [:]
    for list in lists where !list.storeId.isEmpty {
        lastUsed[list.storeId] = max(lastUsed[list.storeId] ?? 0, list.createdAt)
    }
    return stores.filter { lastUsed[$0.id] != nil }.sorted { lastUsed[$0.id]! > lastUsed[$1.id]! }
}

/// True if an on-hand item covers a recipe ingredient (every word of it appears, whole words only).
/// An extra word that names a different product breaks the match: "rice" doesn't cover "rice vinegar".
func isCoveredBy(_ ingredient: String, onHand: [String]) -> Bool {
    let ingredientWords = words(ingredient)
    return onHand.contains { item in
        let w = words(item)
        return !w.isEmpty && w.isSubset(of: ingredientWords) && ingredientWords.subtracting(w).isDisjoint(with: productWords)
    }
}

// Words that make "<item> <word>" a different thing to buy than the item itself. Singular, since words()
// drops a trailing "s". Keep in step with IngredientMatch.kt.
private let productWords: Set<String> = [
    "vinegar", "flour", "noodle", "wine", "paper", "cake", "cracker", "chip", "milk", "cream", "butter",
    "cheese", "oil", "sauce", "paste", "powder", "flake", "broth", "stock", "bouillon", "soup", "starch",
    "meal", "syrup", "extract", "jam", "jelly", "spread", "dressing", "seasoning", "bread",
]

private func words(_ text: String) -> Set<String> {
    Set(itemKey(text).split(whereSeparator: { !$0.isLetter && !$0.isNumber }).map { word in
        let s = String(word)
        return s.hasSuffix("s") ? String(s.dropLast()) : s
    })
}

/// Logos for common US grocery chains, matched by store name. Others get a lettered tile.
enum StoreBrands {
    private static let brands: [(keys: [String], domain: String, logo: String?)] = [
        (["king soopers"], "kingsoopers.com", nil),
        (["whole foods"], "wholefoodsmarket.com", nil),
        (["costco"], "costco.com", "https://www.costco.com/apple-touch-icon.png"),
        (["walmart"], "walmart.com", "https://www.walmart.com/apple-touch-icon.png"),
        (["target"], "target.com", nil),
        (["safeway"], "safeway.com", nil),
        (["sprouts"], "sprouts.com", nil),
        (["trader joe"], "traderjoes.com", nil),
        (["natural grocers"], "naturalgrocers.com", nil),
        (["sams club"], "samsclub.com", nil),
        (["aldi"], "aldi.us", nil),
        (["lidl"], "lidl.com", nil),
        (["kroger"], "kroger.com", nil),
        (["albertsons"], "albertsons.com", nil),
        (["city market"], "citymarket.com", nil),
        (["smiths food", "smith s food"], "smithsfoodanddrug.com", nil),
        (["frys food", "fry s food"], "frysfood.com", nil),
        (["fred meyer"], "fredmeyer.com", nil),
        (["ralphs"], "ralphs.com", nil),
        (["dillons"], "dillons.com", nil),
        (["food 4 less", "food4less"], "food4less.com", nil),
        (["vons"], "vons.com", nil),
        (["jewel osco"], "jewelosco.com", nil),
        (["publix"], "publix.com", nil),
        (["h e b", "heb"], "heb.com", nil),
        (["wegmans"], "wegmans.com", nil),
        (["meijer"], "meijer.com", nil),
        (["hy vee"], "hy-vee.com", nil),
        (["harris teeter"], "harristeeter.com", nil),
        (["food lion"], "foodlion.com", nil),
        (["stop shop"], "stopandshop.com", nil),
        (["giant food"], "giantfood.com", nil),
        (["winco"], "wincofoods.com", nil),
        (["grocery outlet"], "groceryoutlet.com", nil),
        (["fresh market"], "thefreshmarket.com", nil),
        (["bjs wholesale", "bj s wholesale"], "bjs.com", nil),
        (["smart final"], "smartandfinal.com", nil),
        (["amazon fresh"], "amazon.com", nil),
        (["h mart"], "hmart.com", nil),
        (["99 ranch"], "99ranch.com", nil),
        (["winn dixie"], "winndixie.com", nil),
        (["shaws"], "shaws.com", nil),
        (["market basket"], "shopmarketbasket.com", nil),
    ]

    static func logoURL(for storeName: String) -> URL? {
        func normalize(_ s: String) -> String {
            s.lowercased().replacingOccurrences(of: #"[^a-z0-9]+"#, with: " ", options: .regularExpression)
                .trimmingCharacters(in: .whitespaces)
        }
        let spaced = normalize(storeName)
        let joined = normalize(storeName.replacingOccurrences(of: "'", with: "").replacingOccurrences(of: "’", with: ""))
        func startsWithWord(_ name: String, _ key: String) -> Bool { name == key || name.hasPrefix(key + " ") }
        guard let brand = brands.first(where: { b in b.keys.contains { startsWithWord(spaced, $0) || startsWithWord(joined, $0) } })
        else { return nil }
        return URL(string: brand.logo ?? "https://www.google.com/s2/favicons?domain=\(brand.domain)&sz=128")
    }
}

// MARK: Cuisines

/// Cuisines offered as favorites and for "Tonight". Same list and order as `CUISINES` in Cuisines.kt.
let cuisines = [
    "American", "Southern", "Mexican", "Caribbean", "Italian", "French", "Greek",
    "Middle Eastern", "Indian", "Chinese", "Japanese", "Korean", "Thai", "Vietnamese",
]

/// "Tonight I'm feeling x, y, or z."
let maxMoodCuisines = 3

/// Seasonings and sauces each cuisine leans on, for pantry quick adds. Same as `CUISINE_PANTRY` in Cuisines.kt.
let cuisinePantry: [String: [String]] = [
    "American": ["BBQ sauce", "Worcestershire sauce", "Garlic powder", "Paprika", "Mustard", "Ketchup"],
    "Southern": ["Cajun seasoning", "Hot sauce", "Paprika", "Garlic powder", "Onion powder", "Cayenne"],
    "Mexican": ["Cumin", "Chili powder", "Dried oregano", "Chipotles in adobo", "Salsa", "Taco seasoning"],
    "Caribbean": ["Jerk seasoning", "Allspice", "Dried thyme", "Hot sauce", "Curry powder", "Coconut milk"],
    "Italian": ["Dried oregano", "Dried basil", "Red pepper flakes", "Balsamic vinegar", "Canned tomatoes", "Parmesan"],
    "French": ["Dijon mustard", "Dried thyme", "Herbes de Provence", "Red wine vinegar", "White wine", "Bay leaves"],
    "Greek": ["Dried oregano", "Dried dill", "Red wine vinegar", "Kalamata olives", "Cinnamon", "Lemon pepper"],
    "Middle Eastern": ["Cumin", "Za'atar", "Sumac", "Tahini", "Cinnamon", "Smoked paprika"],
    "Indian": ["Garam masala", "Turmeric", "Cumin", "Curry powder", "Ground coriander", "Coconut milk"],
    "Chinese": ["Soy sauce", "Oyster sauce", "Sesame oil", "Rice vinegar", "Hoisin sauce", "Shaoxing wine"],
    "Japanese": ["Soy sauce", "Mirin", "Cooking sake", "Miso", "Rice vinegar", "Sesame oil"],
    "Korean": ["Gochujang", "Soy sauce", "Sesame oil", "Gochugaru", "Rice vinegar", "Sesame seeds"],
    "Thai": ["Fish sauce", "Red curry paste", "Coconut milk", "Soy sauce", "Sriracha", "Oyster sauce"],
    "Vietnamese": ["Fish sauce", "Hoisin sauce", "Sriracha", "Rice vinegar", "Soy sauce", "Lemongrass paste"],
]

/// Every kitchen's basics, offered first.
let basicPantry = ["Salt", "Black pepper"]

/// Pantry quick adds for households that haven't picked cuisines, and to fill out the row.
let commonPantry = [
    "Soy sauce", "Garlic powder", "Paprika", "Cumin", "Dried oregano", "Red pepper flakes",
    "Honey", "Dijon mustard", "Hot sauce", "Rice vinegar", "Chili powder", "Sesame oil",
]

// MARK: Aisles

/// Store sections in the order most US grocery stores are walked: produce by the door, the perimeter and
/// center aisles, then dairy and frozen last so cold things stay cold. Same order as `Aisle` in Aisles.kt.
enum Aisle: Int, CaseIterable, Comparable {
    case produce, bakery, meat, pantry, snacks, drinks, household, personalCare, pet, dairy, frozen, other

    var label: String {
        switch self {
        case .produce: "Produce"
        case .bakery: "Bakery"
        case .meat: "Meat & seafood"
        case .pantry: "Pantry"
        case .snacks: "Snacks"
        case .drinks: "Drinks"
        case .household: "Household"
        case .personalCare: "Personal care"
        case .pet: "Pet"
        case .dairy: "Dairy & eggs"
        case .frozen: "Frozen"
        case .other: "Other"
        }
    }

    static func < (a: Aisle, b: Aisle) -> Bool { a.rawValue < b.rawValue }
}

/// One section of a list. `aisle` is nil for the checked-off items, which go last.
struct ItemSection: Identifiable {
    let aisle: Aisle?
    let items: [ListItem]
    var id: Int { aisle?.rawValue ?? -1 }
}

/// Unchecked items grouped by aisle in walking order, then everything checked off in one last section.
/// Keeps the given order within each section.
func groupByAisle(_ items: [ListItem]) -> [ItemSection] {
    var sections = Dictionary(grouping: items.filter { !$0.checked }) { aisleFor($0.name) }
        .sorted { $0.key < $1.key }
        .map { ItemSection(aisle: $0.key, items: $0.value) }
    let checked = items.filter(\.checked)
    if !checked.isEmpty { sections.append(ItemSection(aisle: nil, items: checked)) }
    return sections
}

/// The aisle an item is usually in, from the words in its name. The phrase ending nearest the end wins, since
/// the last word is usually the thing itself ("chicken broth" is broth, "sweet potato fries" are fries), then
/// the longest ("peanut butter" over "butter"). "Frozen", "canned" and "dried" override.
func aisleFor(_ name: String) -> Aisle {
    let words = aisleWords(name).map(wordForms)
    for forms in words {
        if let aisle = forms.lazy.compactMap({ aisleOverrides[$0] }).first { return aisle }
    }
    var best: Aisle?, bestLength = 0, bestEnd = 0
    for (phrase, aisle) in aislePhrases where phrase.count <= words.count {
        for start in 0...(words.count - phrase.count)
        where phrase.indices.allSatisfy({ words[start + $0].contains(phrase[$0]) }) {
            let end = start + phrase.count
            if end > bestEnd || (end == bestEnd && phrase.count > bestLength) {
                best = aisle
                bestLength = phrase.count
                bestEnd = end
            }
        }
    }
    return best ?? .other
}

private func aisleWords(_ text: String) -> [String] {
    itemKey(text).split(whereSeparator: { !$0.isLetter && !$0.isNumber }).map(String.init)
}

// A word and its likely singulars, so "berries", "tomatoes", "loaves" and "eggs" match "berry", "tomato", "loaf"
// and "egg".
private func wordForms(_ word: String) -> Set<String> {
    var forms: Set<String> = [word]
    if word.count > 3 && word.hasSuffix("s") && !word.hasSuffix("ss") {
        forms.insert(String(word.dropLast()))
        if word.hasSuffix("es") { forms.insert(String(word.dropLast(2))) }
        if word.hasSuffix("ies") { forms.insert(String(word.dropLast(3)) + "y") }
        if word.hasSuffix("ves") { forms.insert(String(word.dropLast(3)) + "f") }
    }
    return forms
}

private let aisleOverrides: [String: Aisle] = ["frozen": .frozen, "canned": .pantry, "dried": .pantry]

private let aislePhrases: [([String], Aisle)] = aisleKeywords.flatMap { aisle, phrases in
    phrases.map { (aisleWords($0), aisle) }
}

/// Singular, lowercase, in the same order as `AISLE_KEYWORDS` in Aisles.kt (order breaks rare ties).
let aisleKeywords: [(Aisle, [String])] = [
    (.produce, [
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
    ]),
    (.bakery, [
        "bread", "baguette", "bun", "dinner roll", "hawaiian roll", "crescent roll", "bagel", "croissant", "muffin",
        "english muffin", "tortilla", "pita", "naan", "flatbread", "sourdough", "loaf", "brioche", "biscuit", "cake",
        "cupcake", "pie", "donut", "doughnut", "pastry",
    ]),
    (.meat, [
        "beef", "ground beef", "steak", "sirloin", "ribeye", "brisket", "rib", "roast", "hamburger", "chicken",
        "thigh", "drumstick", "wing", "turkey", "pork", "pork chop", "tenderloin", "bacon", "sausage", "ham", "lamb",
        "veal", "bison", "salami", "pepperoni", "prosciutto", "hot dog", "bratwurst", "chorizo", "meatball",
        "deli meat", "lunch meat", "meat", "fish", "salmon", "cod", "tilapia", "halibut", "trout", "shrimp",
        "scallop", "crab", "lobster", "mussel", "clam", "oyster", "seafood",
    ]),
    (.pantry, [
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
    ]),
    (.snacks, [
        "chip", "cracker", "pretzel", "popcorn", "rice cake", "cookie", "candy", "chocolate", "granola bar",
        "protein bar", "snack bar", "fruit snack", "nut", "peanut", "almond", "cashew", "pistachio", "walnut",
        "pecan", "trail mix", "jerky", "raisin", "dip", "snack", "gum",
    ]),
    (.drinks, [
        "water", "sparkling water", "seltzer", "juice", "soda", "lemonade", "coffee", "tea", "tea bag",
        "coffee bean", "kombucha", "beer", "wine", "sports drink", "energy drink", "drink", "beverage", "coke",
        "pepsi", "gatorade", "la croix",
    ]),
    (.household, [
        "paper towel", "toilet paper", "tissue", "kleenex", "napkin", "trash bag", "garbage bag", "bag", "foil",
        "plastic wrap", "parchment", "dish soap", "soap", "detergent", "dishwasher", "laundry", "bleach",
        "fabric softener", "dryer sheet", "cleaner", "sponge", "wipe", "paper plate", "paper cup", "light bulb",
        "battery", "candle", "coffee filter", "ziploc", "clorox", "lysol", "windex",
    ]),
    (.personalCare, [
        "shampoo", "conditioner", "body wash", "face wash", "toothpaste", "toothbrush", "floss", "mouthwash",
        "deodorant", "razor", "shaving cream", "lotion", "sunscreen", "tampon", "vitamin", "medicine", "ibuprofen",
        "tylenol", "advil", "band aid", "bandaid", "bandage", "diaper", "baby wipe", "formula", "cotton swab",
        "lip balm", "hand sanitizer", "cough drop", "q tip", "chapstick",
    ]),
    (.pet, [
        "dog", "cat", "pet", "litter", "kibble",
    ]),
    (.dairy, [
        "milk", "egg", "butter", "cheese", "cream", "sour cream", "heavy cream", "half and half", "half & half",
        "creamer", "yogurt", "kefir", "buttermilk", "margarine", "mozzarella", "cheddar", "parmesan", "feta",
        "ricotta", "provolone", "brie", "gouda",
    ]),
    (.frozen, [
        "ice cream", "ice", "pizza", "popsicle", "waffle", "tater tot", "french fry", "fry", "hash brown", "nugget",
        "sorbet", "gelato", "burrito", "edamame",
    ]),
]
