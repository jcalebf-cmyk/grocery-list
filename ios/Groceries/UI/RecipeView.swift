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
    @State private var unchecked: Set<Int>?
    @State private var pickingStore = false
    @State private var showingOriginal = false
    /// How many people to cook for; amounts scale from what the recipe serves.
    @State private var people = 0

    private var kitchenNames: [String] { data.onHand.map(\.name) }
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

                    if !recipe.ingredients.isEmpty { sectionTitle("Ingredients") }
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
                .padding(.bottom, 100)
            }
        }
        .background(Color.warmBackground)
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            if let recipe, !recipe.ingredients.isEmpty {
                let count = recipe.ingredients.count - (unchecked ?? []).count
                Button { pickingStore = true } label: {
                    Text(count == 1 ? "Add 1 item to a list" : "Add \(count) items to a list")
                        .font(.headline).frame(maxWidth: .infinity, minHeight: 36)
                }
                .prominentGlassButton()
                .disabled(count == 0)
                .padding(.horizontal, 16).padding(.bottom, 8)
            }
        }
        // Everything starts checked except what's already in the kitchen; edits to the recipe start over.
        .task(id: recipe?.servings) { people = recipe?.servings ?? 0 }
        .task(id: recipe?.ingredients) {
            if let recipe {
                unchecked = Set(recipe.ingredients.indices.filter { isCoveredBy(recipe.ingredients[$0].name, onHand: kitchenNames) })
            }
        }
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
        let isChecked = !(unchecked ?? []).contains(index)
        let inKitchen = isCoveredBy(ingredient.name, onHand: kitchenNames)
        return Button {
            var set = unchecked ?? []
            if isChecked { set.insert(index) } else { set.remove(index) }
            unchecked = set
        } label: {
            HStack(spacing: 12) {
                Image(systemName: isChecked ? "checkmark.circle.fill" : "circle")
                    .font(.title3).foregroundStyle(isChecked ? Color.accentColor : .secondary)
                VStack(alignment: .leading, spacing: 1) {
                    Text(ingredient.name.prefix(1).uppercased() + ingredient.name.dropFirst())
                    if inKitchen { Text("In your kitchen").font(.caption).foregroundStyle(Color.accentColor) }
                }
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
        for (index, ingredient) in recipe.ingredients.enumerated() where !(unchecked ?? []).contains(index) {
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

/// "My kitchen": ingredients the household already has, in two tabs: ingredients, and pantry seasonings and
/// sauces. List suggestions skip both, and recipes leave them unchecked when adding ingredients to a list.
struct KitchenView: View {
    let data: HouseholdData
    @State private var pantry = false

    private var items: [OnHandItem] { data.onHand.filter { $0.isPantry == pantry } }
    private var quickAdds: [String] {
        let inKitchen = Set(data.onHand.map(\.key))
        return pantry
            ? pantryQuickAdds(inKitchen: inKitchen)
            : kitchenQuickAdds(data.stats, inKitchen: inKitchen, now: nowMillis())
    }

    var body: some View {
        List {
            Picker("Kind", selection: $pantry) {
                Text("Ingredients").tag(false)
                Text("Pantry & seasonings").tag(true)
            }
            .pickerStyle(.segmented)
            .listRowBackground(Color.clear)
            .listRowSeparator(.hidden)
            .listRowInsets(EdgeInsets(top: 8, leading: 16, bottom: 0, trailing: 16))
            Text(pantry
                 ? "Spices, sauces, and other flavor-makers, like soy sauce or mirin. Recipes won't add them to your list."
                 : "Things you already have, like a stocked freezer or a ButcherBox delivery. Lists won't suggest them, and recipes won't add them to your list.")
                .foregroundStyle(.secondary)
                .listRowBackground(Color.clear)
            if !quickAdds.isEmpty {
                Section("Quick add") {
                    FlowLayout {
                        ForEach(quickAdds, id: \.self) { name in
                            Button { data.groceries.addOnHand(name, uid: data.user.id, pantry: pantry) } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: "plus")
                                    Text(name)
                                }
                                .fixedSize()
                            }
                            .buttonStyle(.glass)
                        }
                    }
                    .padding(.vertical, 4)
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                }
            }
            if !items.isEmpty {
                Section(pantry ? "In your pantry" : "In your kitchen") {
                    ForEach(items) { item in
                        Text(item.name)
                            .swipeActions { Button("Remove", systemImage: "trash", role: .destructive) { data.groceries.removeOnHand(item) } }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.warmBackground)
        .animation(.snappy, value: data.onHand)
        .animation(.snappy, value: pantry)
        .navigationTitle("My kitchen")
        .safeAreaInset(edge: .bottom) {
            AddItemBar(placeholder: pantry ? "Add a seasoning or sauce" : "Add an ingredient") {
                data.groceries.addOnHand($0, uid: data.user.id, pantry: pantry)
            }
        }
    }
}
