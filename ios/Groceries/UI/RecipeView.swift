import SwiftUI

/// Full-bleed photo hero (the home card zooms into it), ingredients to tick off, and simple steps.
struct RecipeView: View {
    let data: HouseholdData
    let videoId: String
    let onOpenList: (String) -> Void
    @State private var unchecked: Set<Int>?
    @State private var pickingStore = false
    @Environment(\.openURL) private var openURL

    private var recipe: Recipe? { data.recipes.first { $0.videoId == videoId } }
    private var kitchenNames: [String] { data.onHand.map(\.name) }

    var body: some View {
        ScrollView {
            if let recipe {
                VStack(alignment: .leading, spacing: 0) {
                    hero(recipe)
                    VStack(alignment: .leading, spacing: 6) {
                        Text(recipe.title).font(.brand(28))
                        Text("\(recipe.totalMinutes) min · \(recipe.ingredients.count) ingredients")
                            .font(.subheadline).foregroundStyle(.secondary)
                        if !recipe.usesHouseholdItems.isEmpty {
                            Text("Uses your usual \(recipe.usesHouseholdItems.joined(separator: ", "))")
                                .font(.subheadline).foregroundStyle(Color.accentColor)
                        }
                    }
                    .padding(20)

                    sectionTitle("Ingredients")
                    ForEach(Array(recipe.ingredients.enumerated()), id: \.offset) { index, ingredient in
                        ingredientRow(index, ingredient)
                    }
                    sectionTitle("Steps")
                    ForEach(Array(recipe.steps.enumerated()), id: \.offset) { index, step in
                        HStack(alignment: .top, spacing: 12) {
                            Text("\(index + 1)").font(.subheadline.weight(.semibold))
                                .frame(width: 28, height: 28).background(Color.accentColor.opacity(0.18), in: .circle)
                            Text(step).padding(.top, 3)
                        }
                        .padding(.horizontal, 20).padding(.vertical, 6)
                    }
                }
                .padding(.bottom, 100)
            }
        }
        .ignoresSafeArea(edges: .top)
        .background(Color.warmBackground)
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            if let recipe {
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
        .onAppear {
            // Everything starts checked except what's already in the kitchen.
            if unchecked == nil, let recipe {
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

    private func hero(_ recipe: Recipe) -> some View {
        Button { if let url = URL(string: recipe.videoUrl) { openURL(url) } } label: {
            // The width comes from the screen; the photo fills and crops inside it. (Letting the image
            // set the width made the whole page wider than the phone.)
            Color.card
                .frame(height: 340)
                .frame(maxWidth: .infinity)
                .overlay { RecipePhoto(recipe: recipe) }
                .clipped()
                .overlay {
                Image(systemName: "play.fill").font(.title).foregroundStyle(.white)
                    .frame(width: 68, height: 68)
                    .glassEffect(.regular.tint(.black.opacity(0.35)), in: .circle)
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Watch a similar recipe on YouTube")
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
                Text(ingredient.quantity).foregroundStyle(.secondary)
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

/// "My kitchen": ingredients the household already has. Recipe ideas use them first.
struct KitchenView: View {
    let data: HouseholdData

    private var quickAdds: [String] {
        kitchenQuickAdds(data.stats, inKitchen: Set(data.onHand.map(\.key)), now: nowMillis())
    }

    var body: some View {
        List {
            Text("Things you already have, like a stocked freezer or a ButcherBox delivery. Lists won't suggest them, and recipes for you put them to use first.")
                .foregroundStyle(.secondary)
                .listRowBackground(Color.clear)
            if !quickAdds.isEmpty {
                Section("Quick add") {
                    FlowLayout {
                        ForEach(quickAdds, id: \.self) { name in
                            Button { data.groceries.addOnHand(name, uid: data.user.id) } label: {
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
            if !data.onHand.isEmpty {
                Section("In your kitchen") {
                    ForEach(data.onHand) { item in
                        Text(item.name)
                            .swipeActions { Button("Remove", systemImage: "trash", role: .destructive) { data.groceries.removeOnHand(item) } }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.warmBackground)
        .animation(.snappy, value: data.onHand)
        .navigationTitle("My kitchen")
        .safeAreaInset(edge: .bottom) {
            AddItemBar(placeholder: "Add an ingredient") { data.groceries.addOnHand($0, uid: data.user.id) }
        }
    }
}
