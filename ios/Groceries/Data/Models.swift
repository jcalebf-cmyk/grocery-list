import FirebaseFirestore
import Foundation

// These mirror the Android app's models field for field: both apps read and write the same Firestore
// documents. Timestamps are device milliseconds (Int64), matching Android.

struct UserProfile: Codable, Identifiable, Hashable {
    @DocumentID var uid: String?
    var displayName: String = ""
    var email: String = ""
    var householdId: String?

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

    var id: String { docId ?? "" }
}

/// How often a household adds an item; drives suggestions. Document ID is the item key.
struct ItemStat: Codable, Identifiable {
    @DocumentID var docId: String?
    var displayName: String = ""
    var count: Int64 = 0
    var lastAdded: Int64 = 0
    var storeCounts: [String: Int64]?

    var key: String { docId ?? "" }
    var id: String { key }
}

/// Something the household already has. Pantry items (seasonings and sauces) flavor recipes;
/// the rest are what recipes get built around. Same document as Android's OnHandItem.
struct OnHandItem: Codable, Identifiable, Hashable {
    static let pantryKind = "pantry"

    @DocumentID var docId: String?
    var name: String = ""
    var key: String = ""
    var addedBy: String = ""
    var createdAt: Int64 = 0
    var kind: String?

    var id: String { docId ?? "" }
    var isPantry: Bool { kind == Self.pantryKind }
}

struct Ingredient: Codable, Hashable {
    var name: String
    var quantity: String
}

struct Recipe: Codable, Identifiable, Hashable {
    var videoId: String
    var title: String
    // Recipes from before 0.10 don't have a description, servings, or tip.
    var description: String?
    var servings: Int?
    var totalMinutes: Int
    var ingredients: [Ingredient]
    var steps: [String]
    var tip: String?
    var usesHouseholdItems: [String]
    var videoUrl: String
    var channel: String
    var thumbnailUrl: String

    var id: String { videoId }
}

/// `onHandKeys` and `cuisines` record the kitchen and favorite cuisines these were generated from, so
/// changes trigger new ideas. `mood` is what the household picked for tonight, if anything.
struct RecipeSet: Codable {
    var generatedAt: Int64 = 0
    var onHandKeys: [String]?
    var cuisines: [String]?
    var mood: [String]?
    var recipes: [Recipe] = []
}

/// The household's favorite cuisines. `chosen` is false until someone picks (or skips) them.
struct CuisinePrefs: Equatable {
    var cuisines: [String]
    var chosen: Bool
}

func nowMillis() -> Int64 { Int64(Date().timeIntervalSince1970 * 1000) }
