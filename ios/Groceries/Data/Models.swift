import FirebaseFirestore
import Foundation

// These mirror the Android app's models field for field: both apps read and write the same Firestore
// documents. Timestamps are device milliseconds (Int64), matching Android.

struct UserProfile: Codable, Identifiable, Hashable {
    @DocumentID var uid: String?
    var displayName: String = ""
    var email: String = ""
    /// The household they have open. Someone can be in several and switch between them.
    var householdId: String?
    /// Every household they're in, so security rules let people in any of them read this profile. The
    /// households' own member lists are what counts; this copy is brought up to date on launch.
    var householdIds: [String]?

    var id: String { uid ?? "" }
}

struct Household: Codable, Identifiable {
    @DocumentID var docId: String?
    var name: String = ""
    var inviteCode: String = ""
    var memberIds: [String] = []

    var id: String { docId ?? "" }
    /// The household's creator, i.e. its earliest member. Only the owner can remove people.
    var ownerId: String? { memberIds.first }
}

struct Store: Codable, Identifiable, Hashable {
    @DocumentID var docId: String?
    var name: String = ""
    var createdAt: Int64 = 0

    var id: String { docId ?? "" }
}

struct GroceryList: Codable, Identifiable, Hashable {
    @DocumentID var docId: String?
    var storeId: String = ""
    var storeName: String = ""
    var status: String = GroceryList.statusActive
    var createdBy: String = ""
    var createdAt: Int64 = 0
    var completedAt: Int64?

    static let statusActive = "active"
    static let statusDone = "done"

    var id: String { docId ?? "" }
    var isActive: Bool { status == Self.statusActive }
    /// Lists started with "Skip" have no store.
    var displayName: String { storeName.isEmpty ? "Shopping list" : storeName }
}

struct ListItem: Codable, Identifiable, Hashable {
    @DocumentID var docId: String?
    var name: String = ""
    var key: String = ""
    var checked: Bool = false
    var addedBy: String = ""
    var createdAt: Int64 = 0
    /// Extra context for whoever's shopping ("the low-sodium one"). Most items have none, so it has to stay optional:
    /// an item without the field couldn't be read otherwise.
    var note: String?

    var id: String { docId ?? "" }
}

/// How often a household adds an item; drives suggestions. Document ID is the item key.
struct ItemStat: Codable, Identifiable {
    @DocumentID var docId: String?
    var displayName: String = ""
    var count: Int64 = 0
    var lastAdded: Int64 = 0
    var storeCounts: [String: Int64]?
    /// When someone chose "Don't suggest"; adding the item again brings it back.
    var dismissedAt: Int64?

    var key: String { docId ?? "" }
    var id: String { key }
}

struct Ingredient: Codable, Hashable {
    var name: String
    var quantity: String
}

/// Where a saved recipe came from: written by AI (in the chat, or for a YouTube video that didn't come with
/// the recipe), pasted text, or copied from a YouTube video's description or the creator's comment. Same as Android's RecipeSource.
enum RecipeSource: String, Codable, Hashable {
    case ai, pasted, youtube
}

/// A recipe in the household's recipe box (`households/{hid}/savedRecipes`), or one the AI chat just suggested
/// (`id` empty until saved). `servings` and `totalMinutes` are 0 when the source didn't say. `originalText` keeps
/// a pasted recipe's text as it was. A recipe from a YouTube link has the video in `sourceUrl` and its channel
/// in `sourceName`. Same document as Android's Recipe.
struct Recipe: Codable, Identifiable, Hashable {
    var id = "" // the document ID, set when read; not a field
    var title = ""
    var description = ""
    var servings = 0
    var totalMinutes = 0
    var ingredients: [Ingredient] = []
    var steps: [String] = []
    var notes = ""
    var source: RecipeSource = .pasted
    var originalText = ""
    var sourceUrl = ""
    var sourceName = ""
    var createdBy = ""
    var createdAt: Int64 = 0
    var updatedAt: Int64 = 0

    enum CodingKeys: String, CodingKey {
        case title, description, servings, totalMinutes, ingredients, steps, notes, source, originalText, sourceUrl,
             sourceName, createdBy, createdAt, updatedAt
    }
}

extension Recipe {
    /// Missing fields fall back to their defaults, and an unknown source reads as pasted.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        description = try c.decodeIfPresent(String.self, forKey: .description) ?? ""
        servings = try c.decodeIfPresent(Int.self, forKey: .servings) ?? 0
        totalMinutes = try c.decodeIfPresent(Int.self, forKey: .totalMinutes) ?? 0
        ingredients = try c.decodeIfPresent([Ingredient].self, forKey: .ingredients) ?? []
        steps = try c.decodeIfPresent([String].self, forKey: .steps) ?? []
        notes = try c.decodeIfPresent(String.self, forKey: .notes) ?? ""
        source = (try? c.decodeIfPresent(String.self, forKey: .source)).flatMap { $0 }.flatMap(RecipeSource.init) ?? .pasted
        originalText = try c.decodeIfPresent(String.self, forKey: .originalText) ?? ""
        sourceUrl = try c.decodeIfPresent(String.self, forKey: .sourceUrl) ?? ""
        sourceName = try c.decodeIfPresent(String.self, forKey: .sourceName) ?? ""
        createdBy = try c.decodeIfPresent(String.self, forKey: .createdBy) ?? ""
        createdAt = try c.decodeIfPresent(Int64.self, forKey: .createdAt) ?? 0
        updatedAt = try c.decodeIfPresent(Int64.self, forKey: .updatedAt) ?? 0
    }

    /// "45 min · Serves 4 · 8 ingredients", leaving out what the recipe doesn't say.
    var summary: String {
        [totalMinutes > 0 ? "\(totalMinutes) min" : nil,
         servings > 0 ? "Serves \(servings)" : nil,
         ingredients.isEmpty ? nil : (ingredients.count == 1 ? "1 ingredient" : "\(ingredients.count) ingredients")]
            .compactMap { $0 }.joined(separator: " · ")
    }

    /// "45 min · Serves 4": as much of `summary` as fits on a card, or how many ingredients if it says neither.
    var cardSummary: String {
        guard totalMinutes > 0 || servings > 0 else { return summary }
        var short = self
        short.ingredients = []
        return short.summary
    }

    /// Where the recipe came from, when it matters. Same wording as `sourceNote` in RecipeScreen.kt.
    var sourceNote: String? {
        let channel = sourceName.isEmpty ? "" : " by \(sourceName)"
        switch source {
        case .youtube: return "From the creator's notes on a video\(channel)"
        case .ai where !sourceUrl.isEmpty:
            return "The video\(channel) didn't come with the recipe written out, so AI wrote this one for the same dish. It may differ from the video."
        case .ai where !id.isEmpty: return "Suggested by AI"
        default: return nil
        }
    }
}

/// One message in the recipe chat. Assistant messages can carry recipe cards.
struct ChatMessage: Identifiable, Hashable {
    let id = UUID()
    var fromUser: Bool
    var text: String
    var recipes: [Recipe] = []
}

func nowMillis() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
