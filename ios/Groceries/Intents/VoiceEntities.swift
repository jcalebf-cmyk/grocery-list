import AppIntents

/// A store whose list Siri can add to or read, or the list that isn't tied to a store. A name the household
/// hasn't saved still resolves (as a new store), so "Start a list for Aldi" works the first time.
struct StoreEntity: AppEntity {
    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Store")
    static let defaultQuery = StoreQuery()

    /// "plain", "store:<id>", or "new:<name>".
    let id: String
    let name: String

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name.isEmpty ? "Shopping list" : name)") }

    static let plain = StoreEntity(id: "plain", name: "")
    init(id: String, name: String) { self.id = id; self.name = name }
    init(store: Store) { self.init(id: "store:\(store.id)", name: store.name) }
    init(newName: String) { self.init(id: "new:\(newName.trimmed)", name: newName.trimmed) }
    init(list: GroceryList) { self = list.storeId.isEmpty ? .plain : StoreEntity(id: "store:\(list.storeId)", name: list.storeName) }

    var choice: StoreChoice {
        if id == "plain" { return .plain }
        if id.hasPrefix("store:") { return .saved(id: String(id.dropFirst(6)), name: name) }
        return .new(name: name)
    }
}

struct StoreQuery: EntityStringQuery {
    @MainActor
    func entities(for identifiers: [String]) async throws -> [StoreEntity] {
        let known = await all()
        return identifiers.compactMap { id in
            if id == "plain" { return .plain }
            if id.hasPrefix("new:") { return StoreEntity(newName: String(id.dropFirst(4))) }
            return known.first { $0.id == id }
        }
    }

    @MainActor
    func entities(matching string: String) async throws -> [StoreEntity] {
        if meansPlainList(string) { return [.plain] }
        let known = await all()
        if let match = findStore(known.map { Store(docId: String($0.id.dropFirst(6)), name: $0.name) }, name: string) {
            return [StoreEntity(store: match)]
        }
        return [StoreEntity(newName: string)]
    }

    @MainActor
    func suggestedEntities() async throws -> [StoreEntity] { await all() }

    /// The household's saved stores, plus stores of open lists whose store was removed. Empty when signed out.
    @MainActor
    private func all() async -> [StoreEntity] {
        guard let s = try? await VoiceActionRunner.signedIn() else { return [] }
        let saved = (try? await s.groceries.stores()) ?? []
        let active = (try? await s.groceries.activeLists()) ?? []
        var seen = Set<String>()
        return (saved + VoiceActionRunner.storesOf(active)).filter { seen.insert($0.id).inserted }.map(StoreEntity.init(store:))
    }
}

/// A recipe in the household's recipe box.
struct RecipeEntity: AppEntity {
    static let typeDisplayRepresentation = TypeDisplayRepresentation(name: "Recipe")
    static let defaultQuery = RecipeQuery()

    let id: String
    let title: String

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(title)") }

    init(recipe: Recipe) { id = recipe.id; title = recipe.title }
}

struct RecipeQuery: EntityStringQuery {
    @MainActor
    func entities(for identifiers: [String]) async throws -> [RecipeEntity] {
        let recipes = await all()
        return identifiers.compactMap { id in recipes.first { $0.id == id }.map(RecipeEntity.init(recipe:)) }
    }

    @MainActor
    func entities(matching string: String) async throws -> [RecipeEntity] {
        findRecipes(await all(), name: string).map(RecipeEntity.init(recipe:))
    }

    @MainActor
    func suggestedEntities() async throws -> [RecipeEntity] {
        await all().sorted { $0.createdAt > $1.createdAt }.map(RecipeEntity.init(recipe:))
    }

    /// Empty when signed out.
    @MainActor
    private func all() async -> [Recipe] {
        guard let s = try? await VoiceActionRunner.signedIn() else { return [] }
        return (try? await RecipeService(householdId: s.householdId).all()) ?? []
    }
}
