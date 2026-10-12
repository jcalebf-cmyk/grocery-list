import Foundation

// Pure logic shared in spirit with the Android app (Suggestions.kt, IngredientMatch.kt,
// StoreBrands.kt, Models.kt). Keep the two in step so both platforms behave the same.

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
func suggestItems(_ stats: [ItemStat], storeId: String, alreadyOnList: Set<String>, now: Int64, limit: Int = 12) -> [ItemStat] {
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

/// Stores to offer under "Your stores": only ones the household has made a list for, most recent first.
func storesInUse(_ stores: [Store], lists: [GroceryList]) -> [Store] {
    var lastUsed: [String: Int64] = [:]
    for list in lists where !list.storeId.isEmpty {
        lastUsed[list.storeId] = max(lastUsed[list.storeId] ?? 0, list.createdAt)
    }
    return stores.filter { lastUsed[$0.id] != nil }.sorted { lastUsed[$0.id]! > lastUsed[$1.id]! }
}

/// True if an on-hand item covers a recipe ingredient (every word of it appears, whole words only).
func isCoveredBy(_ ingredient: String, onHand: [String]) -> Bool {
    let ingredientWords = words(ingredient)
    return onHand.contains { item in
        let w = words(item)
        return !w.isEmpty && w.isSubset(of: ingredientWords)
    }
}

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
