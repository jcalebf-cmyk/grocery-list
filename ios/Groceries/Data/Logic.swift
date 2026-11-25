import Foundation

// Pure logic shared in spirit with the Android app (Suggestions.kt, IngredientMatch.kt,
// StoreBrands.kt, Models.kt, Aisles.kt). Keep the two in step so both platforms behave the same.

/// A starting household name from someone's name: "Josh Franzen" → "Franzen household". Empty for a
/// one-word name (often an email's first half), which leaves the field empty with its example.
func suggestedHouseholdName(_ displayName: String) -> String {
    let words = displayName.split(whereSeparator: \.isWhitespace)
    return words.count >= 2 ? "\(words.last!) household" : ""
}

/// "  Whole  Milk " and "whole milk" are the same item. Slashes aren't allowed in document IDs.
func itemKey(_ name: String) -> String {
    name.trimmingCharacters(in: .whitespacesAndNewlines)
        .lowercased()
        .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
        .replacingOccurrences(of: "/", with: "-")
}

let maxNoteLength = 120

/// A note as it's saved: one line, no longer than `maxNoteLength`. Nil if there's nothing in it. Same as Models.kt.
func cleanNote(_ text: String) -> String? {
    let line = text.trimmed.replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
    let short = String(line.prefix(maxNoteLength)).trimmed
    return short.isEmpty ? nil : short
}

private let dayMs = 24.0 * 60 * 60 * 1000
private let halfLifeDays = 21.0
private let minTimesAdded: Int64 = 2
private let listMinTimesAdded: Int64 = 3
private let listMaxDaysSinceAdded = 60.0

/// Suggestions for a list: the household's regulars (see `rankRegulars`) that were added at least 3 times,
/// most recently within 60 days, and not dismissed since they were last added.
func suggestItems(_ stats: [ItemStat], storeId: String, alreadyOnList: Set<String>, now: Int64,
                  limit: Int = 12) -> [ItemStat] {
    rankRegulars(
        stats.filter {
            $0.count >= listMinTimesAdded && Double(now - $0.lastAdded) <= listMaxDaysSinceAdded * dayMs
                && ($0.dismissedAt ?? 0) < $0.lastAdded
        },
        storeId: storeId, alreadyOnList: alreadyOnList, now: now, limit: limit)
}

/// Ranks items the household adds often, favoring recent habits and the store this list is for.
/// Skips what's already on the list.
func rankRegulars(_ stats: [ItemStat], storeId: String, alreadyOnList: Set<String>, now: Int64,
                  limit: Int = 12) -> [ItemStat] {
    stats
        .filter { $0.count >= minTimesAdded && !alreadyOnList.contains($0.key) }
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

/// A recipe a search found. `ingredient` is the one that matched, when the title alone doesn't show why it's here.
struct RecipeMatch: Identifiable {
    let recipe: Recipe
    var ingredient: String?
    var id: String { recipe.id }
}

/// Recipes with every word of `query`, best first: in the title, then in an ingredient, then in the description,
/// notes or video channel. Within each, they stay in the order given. Accents and plurals don't matter ("jalapeno",
/// "tomatoes"), and part of a word is enough. A blank query gives every recipe. Same as RecipeSearch.kt.
func searchRecipes(_ recipes: [Recipe], query: String) -> [RecipeMatch] {
    let words = searchWords(query)
    if words.isEmpty { return recipes.map { RecipeMatch(recipe: $0) } }
    var inTitle: [RecipeMatch] = [], inIngredients: [RecipeMatch] = [], inRest: [RecipeMatch] = []
    for recipe in recipes {
        let title = SearchText(recipe.title)
        let notInTitle = words.filter { !title.has($0) }
        if notInTitle.isEmpty {
            inTitle.append(RecipeMatch(recipe: recipe))
            continue
        }
        let ingredients = recipe.ingredients.map { (name: $0.name, text: SearchText($0.name)) }
        let leftOver = notInTitle.filter { word in !ingredients.contains { $0.text.has(word) } }
        if leftOver.isEmpty {
            /// The first that matches the most words.
            func best(_ names: [String]) -> String? {
                var found: (name: String, count: Int)?
                for name in names {
                    let count = notInTitle.filter(SearchText(name).has).count
                    if count > found?.count ?? -1 { found = (name, count) }
                }
                return found?.name
            }
            // Just the part that matched: "boneless, skinless chicken thighs" is "skinless chicken thighs".
            let part = best(ingredients.map(\.name)).flatMap { best($0.split(separator: ",").map { String($0).trimmed }) }
            inIngredients.append(RecipeMatch(recipe: recipe, ingredient: part))
            continue
        }
        let rest = SearchText([recipe.description, recipe.notes, recipe.sourceName].joined(separator: " "))
        if leftOver.allSatisfy(rest.has) { inRest.append(RecipeMatch(recipe: recipe)) }
    }
    return inTitle + inIngredients + inRest
}

func searchWords(_ text: String) -> [String] {
    aisleWords(text.folding(options: .diacriticInsensitive, locale: nil))
}

private struct SearchText {
    private let words: [String]
    private let forms: Set<String>

    init(_ text: String) {
        words = searchWords(text)
        forms = words.reduce(into: []) { $0.formUnion(wordForms($1)) }
    }

    /// Part of a word, or the same word in the singular or plural.
    func has(_ word: String) -> Bool {
        words.contains { $0.contains(word) } || !wordForms(word).isDisjoint(with: forms)
    }
}

/// Stores to offer under "Your stores": only ones the household has made a list for, most recent first.
func storesInUse(_ stores: [Store], lists: [GroceryList]) -> [Store] {
    var lastUsed: [String: Int64] = [:]
    for list in lists where !list.storeId.isEmpty {
        lastUsed[list.storeId] = max(lastUsed[list.storeId] ?? 0, list.createdAt)
    }
    return stores.filter { lastUsed[$0.id] != nil }.sorted { lastUsed[$0.id]! > lastUsed[$1.id]! }
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

// MARK: Servings

// Scales a recipe's ingredient amounts for a different number of servings. Same as Scaling.kt.

private let fractionChars: [Character: Double] = ["½": 0.5, "⅓": 1.0 / 3, "⅔": 2.0 / 3, "¼": 0.25, "¾": 0.75, "⅛": 0.125, "⅜": 0.375, "⅝": 0.625, "⅞": 0.875]

/// A whole number with a fraction ("1 1/2", "1½"), a fraction, a decimal, or a whole number.
private let numberPattern = #"(?:\d+\s+\d+/\d+|\d+/\d+|\d+\s?[½⅓⅔¼¾⅛⅜⅝⅞]|\d*\.\d+|\d+|[½⅓⅔¼¾⅛⅜⅝⅞])"#
/// The amount at the start of a quantity, optionally a range ("2-3", "2 to 3").
private let leadingAmount = try! NSRegularExpression(pattern: "^\\s*(\(numberPattern))(\\s*(?:-|–|to)\\s*(\(numberPattern)))?")

/// Fractions people cook with, for rounding scaled amounts.
private let niceFractions: [(Double, String)] = [(0, ""), (0.125, "⅛"), (0.25, "¼"), (1.0 / 3, "⅓"), (0.375, "⅜"), (0.5, "½"), (0.625, "⅝"), (2.0 / 3, "⅔"), (0.75, "¾"), (0.875, "⅞"), (1, "")]

private func parseAmount(_ text: String) -> Double? {
    let t = text.trimmingCharacters(in: .whitespaces)
    if let last = t.last, let frac = fractionChars[last] {
        let whole = t.dropLast().trimmingCharacters(in: .whitespaces)
        if whole.isEmpty { return frac }
        guard let w = Double(whole) else { return nil }
        return w + frac
    }
    let parts = t.split(whereSeparator: \.isWhitespace)
    if parts.count == 2 {
        guard let w = Double(parts[0]), let f = parseAmount(String(parts[1])) else { return nil }
        return w + f
    }
    if t.contains("/") {
        let nd = t.split(separator: "/").compactMap { Double($0) }
        guard nd.count == 2, nd[1] != 0 else { return nil }
        return nd[0] / nd[1]
    }
    return Double(t)
}

private func formatAmount(_ value: Double, decimal: Bool) -> String {
    if decimal {
        var text = String(format: "%.2f", locale: Locale(identifier: "en_US_POSIX"), value)
        while text.hasSuffix("0") { text.removeLast() }
        if text.hasSuffix(".") { text.removeLast() }
        return text
    }
    var whole = Int(value.rounded(.down))
    let (frac, symbol) = niceFractions.min { abs($0.0 - (value - Double(whole))) < abs($1.0 - (value - Double(whole))) }!
    if frac == 1 { whole += 1 }
    // Never round an amount away to nothing.
    if whole == 0 && symbol.isEmpty { return "⅛" }
    return whole == 0 ? symbol : "\(whole)\(symbol)"
}

/// `quantity` with its leading amount (or range) multiplied by `factor`: "1 1/2 lb" × 2 is "3 lb", "2-3 cloves"
/// × 2 is "4–6 cloves". Amounts round to cooking fractions (⅛ to ⅞); decimals stay decimals. Quantities without
/// a leading number ("to taste", "a pinch") come back unchanged.
func scaleQuantity(_ quantity: String, factor: Double) -> String {
    guard factor != 1 else { return quantity }
    let ns = quantity as NSString
    guard let match = leadingAmount.firstMatch(in: quantity, range: NSRange(location: 0, length: ns.length)),
          let low = parseAmount(ns.substring(with: match.range(at: 1))) else { return quantity }
    let decimal = ns.substring(with: match.range).contains(".")
    var scaled = formatAmount(low * factor, decimal: decimal)
    if match.range(at: 3).location != NSNotFound {
        guard let high = parseAmount(ns.substring(with: match.range(at: 3))) else { return quantity }
        scaled += "–" + formatAmount(high * factor, decimal: decimal)
    }
    return scaled + ns.substring(from: match.range.location + match.range.length)
}

/// The factor for serving `people` from a recipe that serves `servings`, or 1 if it doesn't say.
func servingsFactor(servings: Int, people: Int) -> Double {
    servings > 0 && people > 0 ? Double(people) / Double(servings) : 1
}

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
// and "egg". Recipe search uses these too.
func wordForms(_ word: String) -> Set<String> {
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

// MARK: - Sharing

// Plain text for sending a recipe or a list by text or email through the share sheet. Same as ShareText.kt.

/// The recipe as written (its own servings), with the video link if it came from one.
func recipeShareText(_ recipe: Recipe) -> String {
    var text = recipe.title.trimmed
    if !recipe.description.trimmed.isEmpty { text += "\n" + recipe.description.trimmed }
    let summary = [recipe.totalMinutes > 0 ? "\(recipe.totalMinutes) min" : nil,
                   recipe.servings > 0 ? "Serves \(recipe.servings)" : nil]
        .compactMap { $0 }.joined(separator: " · ")
    if !summary.isEmpty { text += "\n" + summary }
    if !recipe.ingredients.isEmpty {
        text += "\n\nIngredients"
        for ingredient in recipe.ingredients { text += "\n• " + ingredientShareLine(ingredient) }
    }
    if !recipe.steps.isEmpty {
        text += "\n\nSteps"
        for (index, step) in recipe.steps.enumerated() { text += "\n\(index + 1). " + step.trimmed }
    }
    if !recipe.notes.trimmed.isEmpty { text += "\n\nNotes\n" + recipe.notes.trimmed }
    if !recipe.sourceUrl.trimmed.isEmpty { text += "\n\nVideo: " + recipe.sourceUrl.trimmed }
    return text
}

/// "2 cloves garlic", or "Salt" when there's no amount.
private func ingredientShareLine(_ ingredient: Ingredient) -> String {
    let name = ingredient.name.trimmed, quantity = ingredient.quantity.trimmed
    return quantity.isEmpty ? name.prefix(1).uppercased() + name.dropFirst() : "\(quantity) \(name)"
}

/// What's still to get on a list, in aisle order. Nil when everything's checked off.
func listShareText(_ list: GroceryList, items: [ListItem]) -> String? {
    let toGet = groupByAisle(items).filter { $0.aisle != nil }.flatMap(\.items)
    if toGet.isEmpty { return nil }
    return listShareSubject(list) + toGet.map { "\n• " + itemShareLine($0) }.joined()
}

/// "Soup (the low-sodium one)", or just the name when there's no note.
private func itemShareLine(_ item: ListItem) -> String {
    item.name.trimmed + (cleanNote(item.note ?? "").map { " (\($0))" } ?? "")
}

/// The email subject for a shared list.
func listShareSubject(_ list: GroceryList) -> String {
    list.storeName.trimmed.isEmpty ? "Shopping list" : "\(list.storeName.trimmed) list"
}

// MARK: Account deletion

/// What the delete-account confirmation says, given the names of the households that go with the account and
/// the ones that stay with their other members. Android: accountDeletionText in AccountDeletion.kt.
func accountDeletionText(deleted: [String], left: [String]) -> String {
    let goes = "\(joinNames(deleted)), including all of \(deleted.count > 1 ? "their" : "its") lists, stores, and recipes."
    let stays = left.count > 1 ? "Their lists stay" : "Its lists stay"
    switch (deleted.isEmpty, left.isEmpty) {
    case (true, true):
        return "This permanently deletes your account. This can't be undone."
    case (true, false):
        return "This permanently deletes your account and removes you from \(joinNames(left)). \(stays) with the other members."
    case (false, true):
        return "This permanently deletes your account and \(goes) This can't be undone."
    case (false, false):
        return "This permanently deletes your account and \(goes) This can't be undone. "
            + "You'll also be removed from \(joinNames(left)). \(stays) with the other members."
    }
}

private func joinNames(_ names: [String]) -> String {
    switch names.count {
    case 0: ""
    case 1: names[0]
    case 2: "\(names[0]) and \(names[1])"
    default: names.dropLast().joined(separator: ", ") + ", and " + names[names.count - 1]
    }
}
