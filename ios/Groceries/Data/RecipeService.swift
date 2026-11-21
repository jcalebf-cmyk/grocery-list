import FirebaseFirestore
import Foundation

/// The household's saved recipes (`households/{hid}/savedRecipes`), plus the server calls that make them: the
/// AI chat and reading a pasted recipe or YouTube link. Chats aren't stored; only recipes someone saves are.
/// Same documents and calls as Android's RecipeRepository.
@MainActor
final class RecipeService {
    private let recipes: CollectionReference

    init(householdId: String) {
        recipes = Firestore.firestore().collection("households").document(householdId).collection("savedRecipes")
    }

    /// Newest first.
    func stream() -> AsyncThrowingStream<[Recipe], Error> {
        AsyncThrowingStream { continuation in
            let registration = recipes.addSnapshotListener { snapshot, error in
                if let error { continuation.finish(throwing: error); return }
                let all = (snapshot?.documents ?? []).compactMap { doc -> Recipe? in
                    guard var recipe = try? doc.data(as: Recipe.self) else { return nil }
                    recipe.id = doc.documentID
                    return recipe
                }
                continuation.yield(all.sorted { $0.createdAt > $1.createdAt })
            }
            continuation.onTermination = { _ in registration.remove() }
        }
    }

    /// A one-time read, for voice actions.
    func all() async throws -> [Recipe] {
        try await recipes.getDocuments().documents.compactMap { doc in
            guard var recipe = try? doc.data(as: Recipe.self) else { return nil }
            recipe.id = doc.documentID
            return recipe
        }
    }

    /// Saves a new recipe and returns its ID. Works offline: Firestore syncs it later.
    @discardableResult
    func add(_ recipe: Recipe, uid: String) -> String {
        let doc = recipes.document()
        var saved = recipe
        saved.createdBy = uid
        saved.createdAt = nowMillis()
        saved.updatedAt = saved.createdAt
        try? doc.setData(from: saved)
        return doc.documentID
    }

    func update(_ recipe: Recipe) {
        var saved = recipe
        saved.updatedAt = nowMillis()
        try? recipes.document(recipe.id).setData(from: saved)
    }

    func delete(_ id: String) {
        recipes.document(id).delete()
    }

    // MARK: Server

    /// The card fields only, as the server sends and takes them.
    private struct Card: Codable {
        var title: String
        var description: String
        var servings: Int
        var totalMinutes: Int
        var ingredients: [Ingredient]
        var steps: [String]
        var notes: String

        init(_ r: Recipe) {
            title = r.title; description = r.description; servings = r.servings; totalMinutes = r.totalMinutes
            ingredients = r.ingredients; steps = r.steps; notes = r.notes
        }

        func recipe(source: RecipeSource) -> Recipe {
            Recipe(title: title, description: description, servings: servings, totalMinutes: totalMinutes,
                   ingredients: ingredients, steps: steps, notes: notes, source: source)
        }
    }

    private struct Turn: Encodable {
        let role: String
        let text: String
        let recipes: [Card]?
    }
    private struct ChatRequest: Encodable {
        let messages: [Turn]
    }
    private struct ChatResponse: Decodable {
        let reply: String
        let recipes: [Card]
    }

    /// Sends the conversation (ending with the user's new message) and returns the AI's reply.
    func chat(_ messages: [ChatMessage]) async throws -> ChatMessage {
        // The server takes up to 40 turns, starting with the user's.
        let recent = messages.suffix(40).drop { !$0.fromUser }
        let turns = recent.map { m in
            Turn(role: m.fromUser ? "user" : "assistant", text: m.text, recipes: m.fromUser ? nil : m.recipes.map(Card.init))
        }
        let response: ChatResponse = try await ServerAPI.post(
            "/api/recipe-chat",
            body: ChatRequest(messages: turns),
            timeout: 60)
        return ChatMessage(fromUser: false, text: response.reply, recipes: response.recipes.map { $0.recipe(source: .ai) })
    }

    private struct ImportRequest: Encodable { let text: String }
    private struct ImportResponse: Decodable {
        struct Video: Decodable { let url: String; let channel: String }
        let recipe: Card
        let source: String
        let video: Video?
    }

    /// Reads a pasted recipe, or the recipe from a pasted YouTube link, into a card. Pasted text keeps the
    /// author's wording. Throws `ServerError` (422 if there's no recipe in it).
    func importRecipe(_ text: String) async throws -> Recipe {
        let response: ImportResponse = try await ServerAPI.post("/api/recipe-import", body: ImportRequest(text: text), timeout: 60)
        var recipe = response.recipe.recipe(source: RecipeSource(rawValue: response.source) ?? .pasted)
        if let video = response.video {
            recipe.sourceUrl = video.url
            recipe.sourceName = video.channel
        } else {
            recipe.originalText = text
        }
        return recipe
    }
}
