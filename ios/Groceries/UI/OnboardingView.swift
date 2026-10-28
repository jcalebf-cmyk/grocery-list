import SwiftUI

/// The walkthrough after someone starts or joins a household: invite (new households only), recipes,
/// then My kitchen's ingredients and pantry. Each step teaches one thing the app does and can be skipped.
/// Android: OnboardingScreen.kt.
struct OnboardingView: View {
    let data: HouseholdData
    let start: OnboardingStart
    let onDone: () -> Void

    private enum Step { case invite, recipes, kitchen, pantry }

    @State private var index = 0
    @State private var cuisines: [String] = []
    @State private var kitchenOptions: [String] = []

    private var steps: [Step] { start == .created ? [.invite, .recipes, .kitchen, .pantry] : [.recipes, .kitchen, .pantry] }
    private var step: Step { steps[index] }
    /// A household someone just started always picks cuisines; one they joined only does if nobody has yet.
    private var picksCuisines: Bool { start == .created || data.needsCuisines }
    /// Seasonings for the cuisines picked a step earlier, or the household's if someone else already chose.
    private var pantryOptions: [String] {
        pantryQuickAdds(cuisines: picksCuisines ? cuisines : data.cuisinePrefs?.cuisines ?? [], inKitchen: [])
    }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    switch step {
                    case .invite: invite
                    case .recipes: recipes
                    case .kitchen: kitchen
                    case .pantry: pantry
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .id(step) // each step slides in fresh
                .transition(.asymmetric(insertion: .move(edge: .trailing), removal: .move(edge: .leading)).combined(with: .opacity))
            }
            .scrollBounceBehavior(.basedOnSize)
            footer
        }
        .background(Color.warmBackground)
        .onAppear {
            kitchenOptions = kitchenQuickAdds(data.stats, inKitchen: [], now: nowMillis())
        }
    }

    // MARK: Chrome

    /// Back button and progress dots. The household step that came before counts as the first dot.
    private var header: some View {
        HStack {
            Button { withAnimation(.snappy) { index -= 1 } } label: {
                Image(systemName: "chevron.left").font(.body.weight(.semibold)).frame(width: 36, height: 36)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .opacity(index > 0 ? 1 : 0)
            .disabled(index == 0)
            .accessibilityLabel("Back")
            Spacer()
            HStack(spacing: 6) {
                ForEach(0...steps.count, id: \.self) { dot in
                    Capsule()
                        .fill(dot == index + 1 ? Color.accentColor : Color.secondary.opacity(0.3))
                        .frame(width: dot == index + 1 ? 18 : 7, height: 7)
                }
            }
            .animation(.snappy, value: index)
            .accessibilityElement()
            .accessibilityLabel("Step \(index + 2) of \(steps.count + 1)")
            Spacer()
            Color.clear.frame(width: 36, height: 36)
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private var footer: some View {
        VStack(spacing: 4) {
            Button(action: next) {
                Text(primaryLabel).font(.headline).frame(maxWidth: .infinity, minHeight: 36)
            }
            .prominentGlassButton()
            if let secondary = secondaryLabel {
                Button(secondary, action: skip).padding(.vertical, 8)
            }
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 8)
    }

    private var primaryLabel: String {
        step == steps.last ? "Done" : "Continue"
    }

    private var secondaryLabel: String? {
        switch step {
        case .recipes: picksCuisines ? "Skip" : nil
        case .invite, .kitchen, .pantry: nil
        }
    }

    private func next() {
        if step == .recipes && picksCuisines { data.recipeService.savePreferences(cuisines) }
        advance()
    }

    private func skip() {
        // Skipping cuisines still counts as choosing, so the picker doesn't come back on Home.
        if step == .recipes && picksCuisines { data.recipeService.savePreferences([]) }
        advance()
    }

    private func advance() {
        if index + 1 < steps.count { withAnimation(.snappy) { index += 1 } } else { onDone() }
    }

    // MARK: Steps

    private func intro(_ systemImage: String, _ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: systemImage)
                .font(.title2)
                .foregroundStyle(Color.accentColor)
                .frame(width: 52, height: 52)
                .background(Color.accentColor.opacity(0.15), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .accessibilityHidden(true)
            Text(title).font(.brand(30))
            Text(text).foregroundStyle(.secondary)
        }
    }

    @ViewBuilder private var invite: some View {
        intro("person.2.fill", "Invite your household",
              "Everyone who joins sees and edits the same lists, live. Send them this code now, or find it in Settings later.")
        VStack(spacing: 14) {
            if let household = data.household {
                Text(household.inviteCode)
                    .font(.system(size: 40, weight: .semibold, design: .monospaced))
                    .tracking(4)
                    .textSelection(.enabled)
                ShareLink(item: "Join our grocery list household \"\(household.name)\" with code \(household.inviteCode)") {
                    Label("Share code", systemImage: "square.and.arrow.up").font(.headline)
                }
                .buttonStyle(.glass)
            } else {
                ProgressView().frame(height: 90)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .background(Color.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.top, 8)
    }

    @ViewBuilder private var recipes: some View {
        intro("fork.knife", "Recipes from what you buy",
              picksCuisines
                  ? "As your household adds groceries, you'll get recipe ideas built around them. Pick a few cuisines you like and the ideas will lean that way."
                  : "As your household adds groceries, you'll get recipe ideas built around them, right on the home screen.")
        if picksCuisines {
            CuisineChips(selected: $cuisines).padding(.top, 4)
            Text("You can change these in Settings.").font(.footnote).foregroundStyle(.secondary)
        }
    }

    @ViewBuilder private var kitchen: some View {
        intro("refrigerator.fill", "Already have it at home?",
              "Add things you keep stocked or get delivered, like a ButcherBox. Lists won't suggest them, and recipes use them first.")
        chips(options: kitchenOptions, pantry: false)
    }

    @ViewBuilder private var pantry: some View {
        intro("leaf.fill", "What's in your spice rack?",
              "Add the seasonings and sauces you keep around, like soy sauce or mirin. Recipes use them for flavor, so you won't have to buy them again.")
        chips(options: pantryOptions, pantry: true)
    }

    /// Toggleable suggestions plus anything typed in, so added items stay visible as checked chips.
    /// Adding an item that's already on the other tab of My kitchen moves it here.
    @ViewBuilder private func chips(options: [String], pantry: Bool) -> some View {
        let added = data.onHand.filter { $0.isPantry == pantry }
        let suggested = Set(options.map(itemKey))
        FlowLayout {
            ForEach(options + added.filter { !suggested.contains($0.key) }.map(\.name), id: \.self) { name in
                let item = added.first { $0.key == itemKey(name) }
                Button {
                    if let item { data.groceries.removeOnHand(item) } else { data.groceries.addOnHand(name, uid: data.user.id, pantry: pantry) }
                } label: {
                    HStack(spacing: 6) {
                        Image(systemName: item == nil ? "plus" : "checkmark")
                        Text(name)
                    }
                    .fixedSize()
                }
                .modifier(KitchenChipStyle(selected: item != nil))
            }
        }
        .padding(.top, 4)
        .animation(.snappy, value: data.onHand)
        AddItemBar(placeholder: "Add something else") { data.groceries.addOnHand($0, uid: data.user.id, pantry: pantry) }
        Text("You can change these anytime in My kitchen.").font(.footnote).foregroundStyle(.secondary)
    }
}

private struct KitchenChipStyle: ViewModifier {
    let selected: Bool

    func body(content: Content) -> some View {
        if selected { content.prominentGlassButton() } else { content.buttonStyle(.glass) }
    }
}
