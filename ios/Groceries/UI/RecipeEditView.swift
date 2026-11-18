import SwiftUI

/// An ingredient or step row being edited. Each has its own ID so rows keep their text as others are removed.
struct IngredientRow: Identifiable, Equatable {
    let id = UUID()
    var quantity: String
    var name: String
}

struct StepRow: Identifiable, Equatable {
    let id = UUID()
    var text: String
}

/// Paste a recipe or YouTube link (`recipeId` nil: paste, read, then check and save) or edit a saved one.
/// Saving drops blank rows; a recipe needs a title. Android: RecipeEditViewModel + RecipeEditScreen.
struct RecipeEditView: View {
    let data: HouseholdData
    let recipeId: String?
    let onSaved: (String) -> Void

    private enum Phase { case paste, reading, edit }

    @State private var phase: Phase
    @State private var pasted = ""
    @State private var error: String?
    @State private var original: Recipe?
    @State private var title = ""
    @State private var description = ""
    @State private var servings = ""
    @State private var minutes = ""
    @State private var ingredients: [IngredientRow] = []
    @State private var steps: [StepRow] = []
    @State private var notes = ""
    @State private var confirmingDiscard = false
    @Environment(\.dismiss) private var dismiss

    /// The server's minimum for pasted text.
    private static let minPaste = 20

    init(data: HouseholdData, recipeId: String?, onSaved: @escaping (String) -> Void) {
        self.data = data
        self.recipeId = recipeId
        self.onSaved = onSaved
        _phase = State(initialValue: recipeId == nil ? .paste : .edit)
    }

    var body: some View {
        NavigationStack {
            Group {
                switch phase {
                case .paste, .reading: pasteStep
                case .edit: editor
                }
            }
            .scrollContentBackground(.hidden)
            .background(Color.warmBackground)
            .navigationTitle(recipeId != nil ? "Edit recipe" : phase == .edit ? "Check the recipe" : "Paste a recipe")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { if hasChanges { confirmingDiscard = true } else { dismiss() } }
                }
                if phase == .edit {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Save", action: save).disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
                    }
                }
            }
            .confirmationDialog(recipeId == nil ? "Discard this recipe?" : "Discard your changes?",
                                isPresented: $confirmingDiscard, titleVisibility: .visible) {
                Button("Discard", role: .destructive) { dismiss() }
                Button("Keep editing", role: .cancel) {}
            }
        }
        .interactiveDismissDisabled(hasChanges)
        .onAppear {
            if let recipeId, original == nil, let recipe = data.recipes.first(where: { $0.id == recipeId }) { fill(recipe) }
        }
    }

    // MARK: Paste

    private var pasteStep: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Copy a recipe from a website, a message, or a note and paste it here. It's sorted into ingredients and steps, keeping the wording as written. You can paste a YouTube link to a cooking video, too.")
                    .foregroundStyle(.secondary)
                TextEditor(text: $pasted)
                    .frame(minHeight: 220)
                    .padding(8)
                    .scrollContentBackground(.hidden)
                    .background(Color.card, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .overlay(alignment: .topLeading) {
                        if pasted.isEmpty {
                            Text("Paste a recipe or a YouTube link").foregroundStyle(.tertiary).padding(16).allowsHitTesting(false)
                        }
                    }
                    .disabled(phase == .reading)
                if pasted.isEmpty {
                    // Pastes without the "allow paste" prompt.
                    PasteButton(payloadType: String.self) { strings in
                        if let text = strings.first { pasted = text }
                    }
                    .buttonBorderShape(.capsule)
                }
                if let error { Text(error).foregroundStyle(.red) }
                Button { Task { await read() } } label: {
                    HStack(spacing: 8) {
                        if phase == .reading { ProgressView().tint(.onAccent) }
                        Text(phase == .reading ? "Reading the recipe…" : "Read recipe").font(.headline)
                    }
                    .frame(maxWidth: .infinity, minHeight: 36)
                }
                .prominentGlassButton()
                .disabled(phase == .reading || pasted.trimmingCharacters(in: .whitespacesAndNewlines).count < Self.minPaste)
            }
            .padding(20)
        }
    }

    private func read() async {
        let text = pasted.trimmingCharacters(in: .whitespacesAndNewlines)
        guard text.count >= Self.minPaste, phase != .reading else { return }
        error = nil
        phase = .reading
        do {
            fill(try await data.recipeService.importRecipe(text))
            phase = .edit
        } catch {
            phase = .paste
            self.error = switch (error as? ServerError)?.status {
            case 422: "Couldn't find a recipe in that. Make sure you copied the ingredients and steps, or a link to a cooking video."
            case 429: "That's all the recipe pasting for today. Check back tomorrow."
            default: "Couldn't read that recipe. Check your connection and try again."
            }
        }
    }

    // MARK: Edit

    private var editor: some View {
        List {
            Section {
                TextField("Title", text: $title, axis: .vertical).font(.headline)
                TextField("Description (optional)", text: $description, axis: .vertical)
                LabeledContent("Serves") {
                    TextField("–", text: $servings).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                }
                LabeledContent("Total minutes") {
                    TextField("–", text: $minutes).keyboardType(.numberPad).multilineTextAlignment(.trailing)
                }
            }
            Section("Ingredients") {
                ForEach($ingredients) { $row in
                    HStack {
                        TextField("Amount", text: $row.quantity).frame(maxWidth: 110)
                        Divider()
                        TextField("Ingredient", text: $row.name)
                    }
                }
                .onDelete { ingredients.remove(atOffsets: $0) }
                Button("Add ingredient", systemImage: "plus") { ingredients.append(IngredientRow(quantity: "", name: "")) }
            }
            Section("Steps") {
                ForEach(Array($steps.enumerated()), id: \.element.id) { index, $row in
                    HStack(alignment: .top, spacing: 10) {
                        Text("\(index + 1)").font(.subheadline.weight(.semibold)).foregroundStyle(.secondary).padding(.top, 2)
                        TextField("Step", text: $row.text, axis: .vertical)
                    }
                }
                .onDelete { steps.remove(atOffsets: $0) }
                Button("Add step", systemImage: "plus") { steps.append(StepRow(text: "")) }
            }
            Section("Notes") {
                TextField("Tips, swaps, what you'd change next time", text: $notes, axis: .vertical).lineLimit(3...)
            }
        }
    }

    private func fill(_ recipe: Recipe) {
        original = recipe
        title = recipe.title
        description = recipe.description
        servings = recipe.servings > 0 ? String(recipe.servings) : ""
        minutes = recipe.totalMinutes > 0 ? String(recipe.totalMinutes) : ""
        ingredients = recipe.ingredients.map { IngredientRow(quantity: $0.quantity, name: $0.name) }
        steps = recipe.steps.map { StepRow(text: $0) }
        notes = recipe.notes
    }

    private var edited: Recipe {
        var recipe = original ?? Recipe()
        let trim = { (s: String) in s.trimmingCharacters(in: .whitespacesAndNewlines) }
        recipe.title = trim(title)
        recipe.description = trim(description)
        recipe.servings = Int(trim(servings)) ?? 0
        recipe.totalMinutes = Int(trim(minutes)) ?? 0
        recipe.ingredients = ingredients.filter { !trim($0.name).isEmpty }.map { Ingredient(name: trim($0.name), quantity: trim($0.quantity)) }
        recipe.steps = steps.map { trim($0.text) }.filter { !$0.isEmpty }
        recipe.notes = trim(notes)
        return recipe
    }

    /// True once there's something that backing out would lose.
    private var hasChanges: Bool {
        switch phase {
        case .paste: false
        case .reading: true
        case .edit: recipeId == nil || edited != original
        }
    }

    private func save() {
        if let recipeId {
            data.recipeService.update(edited)
            onSaved(recipeId)
        } else {
            onSaved(data.recipeService.add(edited, uid: data.user.id))
        }
    }
}
