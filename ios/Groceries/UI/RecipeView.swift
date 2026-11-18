import SwiftUI

/// A saved recipe from the household's recipe box, with Edit and Delete. Android: RecipeScreen.
struct RecipeView: View {
    let data: HouseholdData
    let recipeId: String
    let onOpenList: (String) -> Void
    @State private var editing = false
    @State private var deleting = false
    @Environment(\.dismiss) private var dismiss

    private var recipe: Recipe? { data.recipes.first { $0.id == recipeId } }

    var body: some View {
        RecipeDetail(data: data, recipe: recipe, onOpenList: onOpenList)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Edit", systemImage: "pencil") { editing = true }.disabled(recipe == nil)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Menu("More", systemImage: "ellipsis") {
                        Button("Delete recipe", systemImage: "trash", role: .destructive) { deleting = true }
                    }
                }
            }
            .sheet(isPresented: $editing) {
                RecipeEditView(data: data, recipeId: recipeId, onSaved: { _ in editing = false })
            }
            .alert("Delete this recipe?", isPresented: $deleting) {
                Button("Delete", role: .destructive) {
                    data.recipeService.delete(recipeId)
                    dismiss()
                }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("It's removed from your household's recipes for everyone.")
            }
    }
}

/// A recipe in full: ingredients to tick off and add to a list, then the steps. Used for saved recipes and
/// for ones the chat suggests before they're saved; callers add their own toolbar buttons.
struct RecipeDetail: View {
    let data: HouseholdData
    let recipe: Recipe?
    let onOpenList: (String) -> Void
    /// Ingredients ticked to add to a list. Nothing starts ticked.
    @State private var checked: Set<Int> = []
    @State private var pickingStore = false
    @State private var showingOriginal = false
    /// How many people to cook for; amounts scale from what the recipe serves.
    @State private var people = 0

    private var factor: Double { servingsFactor(servings: recipe?.servings ?? 0, people: people) }

    var body: some View {
        ScrollView {
            if let recipe {
                VStack(alignment: .leading, spacing: 0) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(recipe.title).font(.brand(28))
                        if !recipe.description.isEmpty { Text(recipe.description).padding(.bottom, 2) }
                        // The servings control below says how many it serves, so the summary leaves that out.
                        let summary = { var r = recipe; r.servings = 0; return r.summary }()
                        if !summary.isEmpty {
                            Text(summary).font(.subheadline).foregroundStyle(.secondary)
                        }
                        if recipe.servings > 0 { servingsControl }
                        if let note = recipe.sourceNote {
                            Text(note).font(.footnote).foregroundStyle(.secondary)
                        }
                        if let url = URL(string: recipe.sourceUrl), !recipe.sourceUrl.isEmpty {
                            Link(destination: url) {
                                Label("Watch the video", systemImage: "play.fill").font(.subheadline.weight(.medium))
                            }
                            .buttonStyle(.glass)
                            .padding(.top, 4)
                        }
                    }
                    .padding(20)

                    if !recipe.ingredients.isEmpty {
                        // Sits with the ingredients rather than pinned to the screen, so it's there when you're
                        // shopping and out of the way while you read or cook.
                        HStack(alignment: .firstTextBaseline) {
                            sectionTitle("Ingredients")
                            Spacer()
                            Button { pickingStore = true } label: {
                                Label(checked.isEmpty ? "Add to list" : "Add \(checked.count) to list", systemImage: "cart")
                                    .font(.subheadline.weight(.medium))
                            }
                            .buttonStyle(.glass)
                            .disabled(checked.isEmpty)
                            .padding(.trailing, 16)
                        }
                    }
                    ForEach(Array(recipe.ingredients.enumerated()), id: \.offset) { index, ingredient in
                        ingredientRow(index, ingredient)
                    }
                    if !recipe.steps.isEmpty {
                        sectionTitle("Steps")
                        if factor != 1 {
                            Text("Amounts in the steps are for \(recipe.servings).")
                                .font(.footnote).foregroundStyle(.secondary).padding(.horizontal, 20)
                        }
                    }
                    ForEach(Array(recipe.steps.enumerated()), id: \.offset) { index, step in
                        HStack(alignment: .top, spacing: 12) {
                            Text("\(index + 1)").font(.subheadline.weight(.semibold))
                                .frame(width: 28, height: 28).background(Color.accentColor.opacity(0.18), in: .circle)
                            Text(step).padding(.top, 3)
                        }
                        .padding(.horizontal, 20).padding(.vertical, 6)
                    }
                    if !recipe.notes.isEmpty {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Notes").font(.subheadline.weight(.semibold))
                            Text(recipe.notes).font(.subheadline)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                        .background(Color.citrusContainer, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                        .padding(.horizontal, 16).padding(.vertical, 16)
                    }
                    if !recipe.originalText.isEmpty {
                        Button(showingOriginal ? "Hide what was pasted" : "Show what was pasted") {
                            withAnimation(.snappy) { showingOriginal.toggle() }
                        }
                        .font(.subheadline.weight(.medium))
                        .padding(.horizontal, 20).padding(.vertical, 8)
                        if showingOriginal {
                            Text(recipe.originalText).font(.subheadline).foregroundStyle(.secondary)
                                .textSelection(.enabled)
                                .padding(.horizontal, 20)
                        }
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.bottom, 32)
            }
        }
        .background(Color.warmBackground)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: recipe?.servings) { people = recipe?.servings ?? 0 }
        // Nothing starts ticked: the shopper ticks what they need. Edits to the recipe start over.
        .task(id: recipe?.ingredients) { checked = [] }
        .fullScreenCover(isPresented: $pickingStore) {
            StorePickerView(
                data: data,
                onPick: { store in addAndOpen(data.groceries.openList(for: store, uid: data.user.id, activeLists: data.activeLists)) },
                onPickNew: { name in
                    let store = data.groceries.addStore(name, existing: data.stores)
                    addAndOpen(data.groceries.openList(for: store, uid: data.user.id, activeLists: data.activeLists))
                },
                onSkip: { addAndOpen(data.groceries.openList(for: nil, uid: data.user.id, activeLists: data.activeLists)) },
                onCancel: { pickingStore = false },
                title: "Add to which list?"
            )
        }
    }

    /// "Serves  − 4 +": scales the ingredient amounts.
    private var servingsControl: some View {
        HStack(spacing: 12) {
            Text("Serves")
            Button { people -= 1 } label: { Image(systemName: "minus").frame(width: 22, height: 22) }
                .buttonStyle(.glass).buttonBorderShape(.circle).disabled(people <= 1)
                .accessibilityLabel("Fewer servings")
            Text("\(people)").font(.headline).monospacedDigit().frame(minWidth: 24)
                .accessibilityLabel("Serves \(people)")
            Button { people += 1 } label: { Image(systemName: "plus").frame(width: 22, height: 22) }
                .buttonStyle(.glass).buttonBorderShape(.circle).disabled(people >= 24)
                .accessibilityLabel("More servings")
        }
        .padding(.top, 4)
        .sensoryFeedback(.selection, trigger: people)
    }

    private func ingredientRow(_ index: Int, _ ingredient: Ingredient) -> some View {
        let isChecked = checked.contains(index)
        return Button {
            if isChecked { checked.remove(index) } else { checked.insert(index) }
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isChecked ? "checkmark.circle.fill" : "circle")
                    .font(.title3).foregroundStyle(isChecked ? Color.accentColor : .secondary)
                Text(ingredient.name.prefix(1).uppercased() + ingredient.name.dropFirst())
                Spacer()
                Text(scaleQuantity(ingredient.quantity, factor: factor)).foregroundStyle(.secondary).multilineTextAlignment(.trailing)
            }
            .padding(.horizontal, 20).padding(.vertical, 8)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .sensoryFeedback(.selection, trigger: isChecked)
    }

    private func addAndOpen(_ list: GroceryList) {
        guard let recipe else { return }
        for (index, ingredient) in recipe.ingredients.enumerated() where checked.contains(index) {
            data.groceries.addItem(to: list, name: ingredient.name.prefix(1).uppercased() + ingredient.name.dropFirst(), uid: data.user.id)
        }
        pickingStore = false
        onOpenList(list.id)
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text).font(.brand(22)).padding(.horizontal, 20).padding(.top, 16).padding(.bottom, 4)
    }
}

/// Every saved recipe, newest first, with search by title or ingredient. Android: AllRecipesScreen.
struct AllRecipesView: View {
    let data: HouseholdData
    let onPaste: () -> Void
    @State private var query = ""

    private var shown: [Recipe] { data.recipes.filter { matchesRecipe($0, query: query) } }

    var body: some View {
        List {
            ForEach(shown) { recipe in
                NavigationLink(value: Route.recipe(recipe.id)) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(recipe.title)
                        if !recipe.summary.isEmpty { Text(recipe.summary).font(.footnote).foregroundStyle(.secondary) }
                    }
                }
            }
        }
        .overlay {
            if shown.isEmpty && !query.isEmpty { ContentUnavailableView.search(text: query) }
        }
        .scrollContentBackground(.hidden)
        .background(Color.warmBackground)
        .navigationTitle("Recipes")
        .searchable(text: $query, prompt: "Search by name or ingredient")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: Route.recipeChat) { Image(systemName: "sparkles") }.accessibilityLabel("Ask for a recipe")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Paste a recipe", systemImage: "doc.on.clipboard", action: onPaste)
            }
        }
    }
}
