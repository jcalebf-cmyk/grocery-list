import AppIntents

// Siri and Shortcuts actions. The work is in VoiceActionRunner; Android's Gemini functions
// (voice/GroceriesAppFunctions.kt) do the same things.

struct AddToListIntent: AppIntent {
    static let title: LocalizedStringResource = "Add to List"
    static let description = IntentDescription("Adds items to one of your household's shopping lists. Separate several items with commas.")

    @Parameter(title: "Items", requestValueDialog: "What should I add?")
    var items: String

    @Parameter(title: "Store", description: "Leave empty to use the only open list.")
    var store: StoreEntity?

    static var parameterSummary: some ParameterSummary { Summary("Add \(\.$items) to \(\.$store)") }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let message = try await VoiceActionRunner.addItems(items, store: store?.choice, ask: askWhichList($store))
        return .result(dialog: "\(message)")
    }
}

struct ReadListIntent: AppIntent {
    static let title: LocalizedStringResource = "Read List"
    static let description = IntentDescription("Reads out what's still to get on one of your household's shopping lists.")

    @Parameter(title: "Store", description: "Leave empty to use the only open list.")
    var store: StoreEntity?

    static var parameterSummary: some ParameterSummary { Summary("Read \(\.$store)") }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let message = try await VoiceActionRunner.readList(store: store?.choice, ask: askWhichList($store))
        return .result(dialog: "\(message)")
    }
}

struct StartListIntent: AppIntent {
    static let title: LocalizedStringResource = "Start a List"
    static let description = IntentDescription("Starts a shopping list for a store, or keeps the one it already has.")

    @Parameter(title: "Store", requestValueDialog: "Which store is the list for?")
    var store: StoreEntity

    static var parameterSummary: some ParameterSummary { Summary("Start a list for \(\.$store)") }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let message = try await VoiceActionRunner.startList(store: store.choice)
        return .result(dialog: "\(message)")
    }
}

struct CheckOffIntent: AppIntent {
    static let title: LocalizedStringResource = "Check Off Item"
    static let description = IntentDescription("Checks an item off one of your household's shopping lists.")

    @Parameter(title: "Item", requestValueDialog: "What should I check off?")
    var item: String

    @Parameter(title: "Store", description: "Leave empty to use the only open list.")
    var store: StoreEntity?

    static var parameterSummary: some ParameterSummary { Summary("Check off \(\.$item) on \(\.$store)") }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let message = try await VoiceActionRunner.checkOff(item, store: store?.choice, ask: askWhichList($store))
        return .result(dialog: "\(message)")
    }
}

struct OpenRecipeIntent: OpenIntent {
    static let title: LocalizedStringResource = "Open Recipe"
    static let description = IntentDescription("Opens a recipe from your household's recipe box.")

    @Parameter(title: "Recipe", requestValueDialog: "Which recipe?")
    var target: RecipeEntity

    @MainActor
    func perform() async throws -> some IntentResult {
        VoiceRouter.shared.recipeId = target.id
        return .result()
    }
}

/// Several lists are open and nobody said which: Siri asks, offering the open lists.
@MainActor
private func askWhichList(_ store: IntentParameter<StoreEntity?>) -> VoiceActionRunner.AskWhichList {
    { lists in
        let options = lists.map(StoreEntity.init(list:))
        let chosen = try await store.requestDisambiguation(among: options, dialog: "\(VoiceText.whichListPrompt)")
        return lists.first { StoreEntity(list: $0).id == chosen.id } ?? lists[0]
    }
}

struct GroceriesShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: AddToListIntent(),
            phrases: [
                "Add to my \(.applicationName) list",
                "Add to my \(\.$store) list in \(.applicationName)",
                "Add something to \(.applicationName)",
                "Add to \(.applicationName)",
            ],
            shortTitle: "Add to List",
            systemImageName: "plus.circle"
        )
        AppShortcut(
            intent: ReadListIntent(),
            phrases: [
                "What's on my \(.applicationName) list",
                "What's on my \(\.$store) list in \(.applicationName)",
                "Read my \(.applicationName) list",
                "Read my \(\.$store) list in \(.applicationName)",
            ],
            shortTitle: "Read List",
            systemImageName: "list.bullet"
        )
        AppShortcut(
            intent: StartListIntent(),
            phrases: [
                "Start a \(.applicationName) list",
                "Start a list in \(.applicationName)",
                "Start a list for \(\.$store) in \(.applicationName)",
                "Start a \(\.$store) list in \(.applicationName)",
            ],
            shortTitle: "Start a List",
            systemImageName: "cart.badge.plus"
        )
        AppShortcut(
            intent: CheckOffIntent(),
            phrases: [
                "Check something off in \(.applicationName)",
                "Check off an item in \(.applicationName)",
                "Check off on my \(\.$store) list in \(.applicationName)",
            ],
            shortTitle: "Check Off",
            systemImageName: "checkmark.circle"
        )
        AppShortcut(
            intent: OpenRecipeIntent(),
            phrases: [
                "Open a recipe in \(.applicationName)",
                "Open \(\.$target) in \(.applicationName)",
                "Show my \(\.$target) recipe in \(.applicationName)",
            ],
            shortTitle: "Open Recipe",
            systemImageName: "book"
        )
    }
}
