import Foundation

// What the voice actions (Siri here, Gemini on Android) decide and say. Same rules and wording as Android's
// data/VoiceActions.kt: change both together.

enum VoiceText {
    static let signedOut = "Open Groceries and sign in first."
    static let noHousehold = "Open Groceries and set up your household first."
    static let noOpenLists = "You don't have any open lists."
    static let whichListPrompt = "Which list?"
    static let nothingToAdd = "What should I add?"
    static let mostRead = 10

    /// "your Costco list", or "your shopping list" for the list that isn't tied to a store.
    static func yourList(_ storeName: String) -> String { storeName.trimmed.isEmpty ? "your shopping list" : "your \(storeName) list" }
    /// "a Costco list" / "a shopping list".
    static func aList(_ storeName: String) -> String { storeName.trimmed.isEmpty ? "a shopping list" : "a \(storeName) list" }

    /// Asked when nobody named a list and several are open: "Which list: Costco, Trader Joe's or shopping?"
    static func whichList(_ lists: [GroceryList]) -> String {
        let names = lists.map { $0.storeName.trimmed.isEmpty ? "shopping" : $0.storeName }
        let joined = names.count < 2 ? names.joined(separator: ", ") : names.dropLast().joined(separator: ", ") + " or " + names.last!
        return "Which list: \(joined)?"
    }

    static func noOpenList(_ storeName: String) -> String {
        storeName.trimmed.isEmpty ? "There's no open shopping list." : "There's no open \(storeName) list."
    }

    static func added(_ added: [String], alreadyThere: [String], storeName: String) -> String {
        let list = yourList(storeName)
        if added.isEmpty {
            return "\(spokenList(alreadyThere).capitalizedFirst) \(alreadyThere.count == 1 ? "is" : "are") already on \(list)."
        }
        let sentence = "Added \(spokenList(added)) to \(list)."
        guard !alreadyThere.isEmpty else { return sentence }
        return sentence + " \(spokenList(alreadyThere).capitalizedFirst) \(alreadyThere.count == 1 ? "was" : "were") already on it."
    }

    /// Reads out what's still to get, up to `mostRead` items.
    static func readOut(_ items: [ListItem], storeName: String) -> String {
        let list = yourList(storeName).capitalizedFirst
        let left = items.filter { !$0.checked }.sorted { $0.createdAt < $1.createdAt }.map(\.name)
        if items.isEmpty { return "\(list) is empty." }
        if left.isEmpty { return "Everything on \(yourList(storeName)) is checked off." }
        if left.count <= mostRead { return "\(list) has \(spokenList(left))." }
        return "\(list) has \(left.prefix(mostRead).joined(separator: ", ")) and \(left.count - mostRead) more."
    }

    static func started(_ storeName: String, alreadyGoing: Bool) -> String {
        alreadyGoing ? "You already have \(aList(storeName)) going." : "Started \(aList(storeName))."
    }

    static func checkedOff(_ name: String, storeName: String) -> String { "Checked off \(name) on \(yourList(storeName))." }
    static func alreadyChecked(_ name: String) -> String { "\(name.capitalizedFirst) is already checked off." }
    static func notOnList(_ name: String, storeName: String) -> String { "\(name.trimmed.capitalizedFirst) isn't on \(yourList(storeName))." }
    static func noRecipe(_ name: String) -> String { "There's no recipe called \(name.trimmed) in your recipe box." }
    static func openingRecipe(_ title: String) -> String { "Here's your \(title) recipe." }

    /// "milk", "milk and eggs", "milk, eggs and bread".
    static func spokenList(_ names: [String]) -> String {
        switch names.count {
        case 0: ""
        case 1: names[0]
        default: names.dropLast().joined(separator: ", ") + " and " + names.last!
        }
    }
}

private extension String {
    var capitalizedFirst: String { prefix(1).uppercased() + dropFirst() }
}

/// Items said in one go. Splits on commas (and new lines) only, since "mac and cheese" is one item.
/// Drops blanks and repeats.
func spokenItems(_ text: String) -> [String] {
    var seen = Set<String>()
    return text.components(separatedBy: CharacterSet(charactersIn: ",;\n"))
        .map { piece in
            var name = piece.trimmed
            while name.hasSuffix(".") { name.removeLast() }
            return name
        }
        .filter { !$0.isEmpty && seen.insert(itemKey($0)).inserted }
}

/// For matching what someone said: lowercase, no apostrophes or punctuation, single spaces.
func spokenKey(_ text: String) -> String {
    text.lowercased()
        .replacingOccurrences(of: "['’`]", with: "", options: .regularExpression)
        .replacingOccurrences(of: "[^\\p{L}\\p{N}]+", with: " ", options: .regularExpression)
        .trimmed
}

private func dropSuffix(_ s: String, _ suffix: String) -> String { s.hasSuffix(suffix) ? String(s.dropLast(suffix.count)) : s }

/// Names that mean the list that isn't tied to a store.
func meansPlainList(_ name: String) -> Bool {
    let said = dropSuffix(dropSuffix(spokenKey(name), " lists"), " list").trimmed
    return ["shopping", "grocery", "groceries", "plain", ""].contains(said)
}

/// The saved store someone meant: the same name first, then one name containing the other ("Joe's" → "Trader Joe's").
func findStore(_ stores: [Store], name: String) -> Store? {
    let said = dropSuffix(spokenKey(name), " list").trimmed
    guard !said.isEmpty else { return nil }
    return stores.first { spokenKey($0.name) == said }
        ?? stores.first { spokenKey($0.name).contains(said) }
        ?? stores.first { !spokenKey($0.name).isEmpty && said.contains(spokenKey($0.name)) }
}

/// The open list for a store ("" = the list that isn't tied to one).
func openListFor(_ activeLists: [GroceryList], storeId: String) -> GroceryList? {
    activeLists.first { $0.storeId == storeId }
}

/// Which list to use when nobody named one.
enum ListPick: Equatable {
    case one(GroceryList)
    case noneOpen
    case several([GroceryList])
}

func pickList(_ activeLists: [GroceryList]) -> ListPick {
    switch activeLists.count {
    case 0: .noneOpen
    case 1: .one(activeLists[0])
    default: .several(activeLists)
    }
}

/// The list item someone meant: the same name, then singular/plural ("eggs" for "egg"), then a partial match.
func findItem(_ items: [ListItem], name: String) -> ListItem? {
    let said = spokenKey(name)
    guard !said.isEmpty else { return nil }
    let ordered = items.filter { !$0.checked } + items.filter(\.checked) // unchecked first
    func singular(_ s: String) -> String { s.hasSuffix("oes") ? dropSuffix(s, "es") : dropSuffix(s, "s") }
    return ordered.first { spokenKey($0.name) == said }
        ?? ordered.first { singular(spokenKey($0.name)) == singular(said) }
        ?? ordered.first { spokenKey($0.name).contains(said) }
}

/// Recipes whose title has every word said, closest title first.
func findRecipes(_ recipes: [Recipe], name: String) -> [Recipe] {
    let said = dropSuffix(spokenKey(name), " recipe").trimmed
    guard !said.isEmpty else { return [] }
    let words = said.split(separator: " ")
    return recipes
        .filter { recipe in let title = spokenKey(recipe.title); return words.allSatisfy { title.contains($0) } }
        .sorted { a, b in
            let aExact = spokenKey(a.title) == said, bExact = spokenKey(b.title) == said
            return aExact != bExact ? aExact : a.title.count < b.title.count
        }
}
