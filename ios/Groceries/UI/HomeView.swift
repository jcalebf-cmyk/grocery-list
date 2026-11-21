import SwiftUI

enum Route: Hashable {
    case list(String)
    case recipe(String)
    case recipes
    case recipeChat
    case pastLists
    case settings
}

/// Live household data shared by the screens, fed by Firestore listeners attached in HomeView.
@MainActor
@Observable
final class HouseholdData {
    let user: UserProfile
    let householdId: String
    let groceries: GroceryService
    let recipeService: RecipeService

    var household: Household?
    var stores: [Store] = []
    var lists: [GroceryList] = []
    var stats: [ItemStat] = []
    /// Saved recipes, newest first.
    var recipes: [Recipe] = []
    var members: [UserProfile] = []

    init(user: UserProfile, householdId: String) {
        self.user = user
        self.householdId = householdId
        groceries = GroceryService(householdId: householdId)
        recipeService = RecipeService(householdId: householdId)
    }

    var activeLists: [GroceryList] { lists.filter(\.isActive) }
    /// Closed-out lists, newest first. Home shows only how many; Past lists shows them.
    var pastLists: [GroceryList] { lists.filter { !$0.isActive } }
    var yourStores: [Store] { storesInUse(stores, lists: lists) }
    var isOwner: Bool { household?.ownerId == user.id }
    var otherMembers: [String: String] {
        members.count > 1 ? Dictionary(uniqueKeysWithValues: members.filter { $0.id != user.id }.map { ($0.id, $0.displayName) }) : [:]
    }

    func openList(for store: Store?) -> String { groceries.openList(for: store, uid: user.id, activeLists: activeLists).id }
    func openList(forNewStore name: String) -> String { openList(for: groceries.addStore(name, existing: stores)) }

    func refreshMembers() async {
        members = await HouseholdService.members(household?.memberIds ?? [])
    }
}

struct HomeView: View {
    @Environment(SessionModel.self) private var session
    @State private var data: HouseholdData
    @State private var path: [Route] = []
    @State private var pickingStore = false
    @State private var pasting = false
    @State private var deletingList: GroceryList?
    @State private var closingList: GroceryList?
    @State private var message: String?
    @State private var twoPane = false
    @Namespace private var zoom

    init(user: UserProfile, householdId: String) {
        _data = State(initialValue: HouseholdData(user: user, householdId: householdId))
    }

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if twoPane {
                    // Wide screen (an opened iPhone Duo): lists on one side, recipes as a grid on the other.
                    HStack(alignment: .top, spacing: 0) {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 0) {
                                listsSection
                                pastListsRow
                            }
                            .padding(.bottom, 24)
                        }
                        Divider().ignoresSafeArea(edges: .bottom)
                        ScrollView { recipesSection(grid: true).padding(.bottom, 24) }
                    }
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            // Lists come first: they're what people open the app for.
                            listsSection
                            pastListsRow
                            recipesSection(grid: false)
                        }
                        .padding(.bottom, 24)
                        .readableWidth()
                    }
                }
            }
            .onTwoPaneChange { twoPane = $0 }
            .background(Color.warmBackground)
            .navigationTitle(data.household?.name ?? "Groceries")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink(value: Route.settings) { Image(systemName: "gearshape") }
                        .accessibilityLabel("Settings")
                }
            }
            // Floats over the content rather than sitting in the bottom toolbar, which would shrink it
            // to an icon-only circle; it should read "New list".
            // With no list going, the Start a list card does this job instead.
            .safeAreaInset(edge: .bottom) {
                if !data.activeLists.isEmpty {
                    // Side by side, it sits under the lists rather than across both panes.
                    HStack(spacing: 0) {
                        Button { pickingStore = true } label: {
                            Label("New list", systemImage: "plus").font(.headline).padding(.horizontal, 12).padding(.vertical, 6)
                        }
                        .prominentGlassButton(foreground: .onCitrus)
                        .tint(.citrus)
                        .frame(maxWidth: .infinity)
                        if twoPane { Color.clear.frame(maxWidth: .infinity, maxHeight: 0) }
                    }
                    .padding(.bottom, 4)
                }
            }
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .list(let id): ListView(data: data, listId: id)
                case .recipe(let id):
                    RecipeView(data: data, recipeId: id, onOpenList: { path = [.list($0)] })
                        .navigationTransition(.zoom(sourceID: id, in: zoom))
                case .recipes: AllRecipesView(data: data, onPaste: { pasting = true })
                case .recipeChat: RecipeChatView(data: data, onOpenList: { path = [.list($0)] })
                case .pastLists: PastListsView(data: data)
                case .settings: SettingsView(data: data)
                }
            }
            .sheet(isPresented: $pasting) {
                // A pasted recipe opens once it's saved.
                RecipeEditView(data: data, recipeId: nil, onSaved: { id in pasting = false; path.append(.recipe(id)) })
            }
            .fullScreenCover(isPresented: $pickingStore) {
                StorePickerView(
                    data: data,
                    onPick: { store in pickingStore = false; path.append(.list(data.openList(for: store))) },
                    onPickNew: { name in pickingStore = false; path.append(.list(data.openList(forNewStore: name))) },
                    onSkip: { pickingStore = false; path.append(.list(data.openList(for: nil))) },
                    onCancel: { pickingStore = false }
                )
            }
            .overlay(alignment: .bottom) {
                if let message {
                    Text(message).font(.callout).padding(.horizontal, 16).padding(.vertical, 12)
                        .glassEffect(in: .capsule).padding(.bottom, 80)
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
            }
        }
        .task { await consume(data.groceries.storesStream()) { v in data.stores = v } }
        .task { await consume(data.groceries.listsStream()) { v in data.lists = v } }
        .task { await consume(data.groceries.statsStream()) { v in data.stats = v } }
        .task { await consume(data.recipeService.stream()) { v in data.recipes = v } }
        // Siri: a recipe someone asked to open, and the store and recipe names it listens for.
        .onChange(of: VoiceRouter.shared.recipeId, initial: true) { _, id in
            guard let id else { return }
            VoiceRouter.shared.recipeId = nil
            path = [.recipe(id)]
        }
        .task(id: data.stores.map(\.name) + data.recipes.map(\.title)) { GroceriesShortcuts.updateAppShortcutParameters() }
        .task {
            await consume(HouseholdService.household(data.householdId)) { v in
                data.household = v
                await data.refreshMembers()
            }
        }
        // New members get the walkthrough first.
        .fullScreenCover(isPresented: .constant(session.onboarding != nil)) {
            if let start = session.onboarding {
                OnboardingView(data: data, start: start) { session.onboarding = nil }
            }
        }
        .deleteListAlert(isPresented: Binding(get: { deletingList != nil }, set: { if !$0 { deletingList = nil } })) {
            guard let list = deletingList else { return }
            Task {
                do { try await data.groceries.deleteList(list.id) } catch { show("Couldn't delete that list. Try again.") }
            }
        }
        .closeOutListAlert(isPresented: Binding(get: { closingList != nil }, set: { if !$0 { closingList = nil } })) {
            if let list = closingList { data.groceries.closeOut(list.id) }
        }
    }

    // MARK: Recipes

    /// The recipes row; `grid` lays the cards out in rows instead, for the recipes pane on a wide screen.
    private func recipesSection(grid: Bool) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text("Recipes").font(.brand(24))
                Spacer()
                if !data.recipes.isEmpty {
                    NavigationLink("See all", value: Route.recipes).font(.subheadline.weight(.medium))
                }
            }
            .padding(.horizontal, 20)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    NavigationLink(value: Route.recipeChat) { Label("Ask for a recipe", systemImage: "sparkles") }
                        .buttonStyle(.glass)
                    Button { pasting = true } label: { Label("Paste a recipe", systemImage: "doc.on.clipboard") }
                        .buttonStyle(.glass)
                }
                .font(.subheadline.weight(.medium))
                .padding(.horizontal, 20)
                .padding(.vertical, 2)
            }
            .scrollClipDisabled()

            if data.recipes.isEmpty {
                Text("Save recipes you like here: ask for one, or paste one from anywhere.")
                    .foregroundStyle(.secondary)
                    .padding(.horizontal, 20)
            } else if grid {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 180), spacing: 14)], spacing: 14) {
                    ForEach(data.recipes.prefix(10)) { recipe in
                        NavigationLink(value: Route.recipe(recipe.id)) { RecipeCard(recipe: recipe, width: nil) }
                            .buttonStyle(.plain)
                            .matchedTransitionSource(id: recipe.id, in: zoom)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 4)
            } else {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: 14) {
                        // Home shows the newest few; the rest are under See all.
                        ForEach(data.recipes.prefix(10)) { recipe in
                            NavigationLink(value: Route.recipe(recipe.id)) { RecipeCard(recipe: recipe) }
                                .buttonStyle(.plain)
                                .matchedTransitionSource(id: recipe.id, in: zoom)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 4)
                }
            }
        }
        // In its own pane it starts level with Shopping lists.
        .padding(.top, grid ? 16 : 28)
    }

    private func show(_ text: String) {
        withAnimation { message = text }
        Task {
            try? await Task.sleep(for: .seconds(3))
            withAnimation { message = nil }
        }
    }

    // MARK: Lists

    @ViewBuilder private var listsSection: some View {
        sectionTitle("Shopping lists", top: 8)
        if data.activeLists.isEmpty {
            startListCard
        }
        ForEach(data.activeLists) { list in
            SwipeAction(label: "Close out", systemImage: "checkmark.circle", tint: .accentColor, iconColor: .onAccent, action: { closingList = list }) {
                listRow(list)
            }
        }
    }

    /// Shown under Shopping lists when none is going, so starting one is the obvious next step.
    private var startListCard: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 14) {
                Image(systemName: "cart.fill").font(.title3).foregroundStyle(Color.onCitrus)
                    .frame(width: 48, height: 48).background(Color.citrusContainer, in: .circle)
                VStack(alignment: .leading, spacing: 2) {
                    Text("No lists going").font(.headline)
                    Text("Pick a store and start adding what you need.").font(.subheadline).foregroundStyle(.secondary)
                }
            }
            Button { pickingStore = true } label: {
                Label("Start a list", systemImage: "plus").font(.headline).frame(maxWidth: .infinity, minHeight: 36)
            }
            .prominentGlassButton()
        }
        .padding(16)
        .background(Color.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }

    /// Past lists stay out of the way: one row under the lists that opens the full history.
    @ViewBuilder private var pastListsRow: some View {
        if !data.pastLists.isEmpty {
            NavigationLink(value: Route.pastLists) {
                HStack(spacing: 14) {
                    Image(systemName: "clock.arrow.circlepath").font(.title3).frame(width: 52, height: 40)
                    Text("Past lists").font(.body.weight(.semibold)).foregroundStyle(.primary)
                    Spacer()
                    Text("\(data.pastLists.count)").foregroundStyle(.secondary)
                    Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
                }
                .padding(12)
                .background(Color.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.horizontal, 16)
            .padding(.vertical, 4)
        }
    }

    private func sectionTitle(_ text: String, top: CGFloat = 28) -> some View {
        Text(text).font(.brand(22)).padding(.horizontal, 20).padding(.top, top).padding(.bottom, 8)
    }

    private func listRow(_ list: GroceryList) -> some View { ListRowView(list: list) }
}

/// A shopping list on Home or in Past lists: store logo, name, and when it started or was closed out.
struct ListRowView: View {
    let list: GroceryList

    var body: some View {
        NavigationLink(value: Route.list(list.id)) {
            HStack(spacing: 14) {
                Group {
                    if list.storeName.isEmpty {
                        Image(systemName: "cart").font(.title3).frame(maxWidth: .infinity, maxHeight: .infinity)
                    } else {
                        StoreLogo(name: list.storeName, cornerRadius: 10)
                    }
                }
                .frame(width: 52, height: 40)
                VStack(alignment: .leading, spacing: 2) {
                    Text(list.displayName).font(.body.weight(.semibold)).foregroundStyle(.primary)
                    let time = Date(timeIntervalSince1970: TimeInterval(list.completedAt ?? list.createdAt) / 1000)
                    Text((list.isActive ? "Started " : "Closed out ") + time.formatted(.relative(presentation: .named)))
                        .font(.footnote).foregroundStyle(.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .padding(12)
            .background(Color.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }
}

/// A saved recipe on Home: its title and the basics, with an icon for where it came from.
struct RecipeCard: View {
    let recipe: Recipe
    /// Fixed in Home's row; nil fills a grid column.
    var width: CGFloat? = 220

    private var icon: String {
        if !recipe.sourceUrl.isEmpty { return "play.rectangle" }
        return recipe.source == .ai ? "sparkles" : "fork.knife"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Image(systemName: icon).font(.subheadline).foregroundStyle(Color.accentColor)
            Text(recipe.title).font(.headline).lineLimit(2).multilineTextAlignment(.leading)
            Spacer(minLength: 0)
            Text(recipe.summary).font(.caption).foregroundStyle(.secondary).lineLimit(1)
        }
        .padding(14)
        .frame(width: width, height: 128, alignment: .topLeading)
        .frame(maxWidth: width == nil ? .infinity : nil, alignment: .leading)
        .background(Color.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }
}

/// Every closed-out list, newest first. Swipe left to delete one. Android: PastListsScreen.
struct PastListsView: View {
    let data: HouseholdData
    @State private var deletingList: GroceryList?
    @State private var failed = false

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                ForEach(data.pastLists) { list in
                    SwipeAction(label: "Delete", systemImage: "trash", tint: .red, action: { deletingList = list }) {
                        ListRowView(list: list)
                    }
                }
                if data.pastLists.isEmpty {
                    Text("Lists you close out show up here.").foregroundStyle(.secondary).padding(20)
                }
            }
            .padding(.vertical, 8)
            .readableWidth()
        }
        .background(Color.warmBackground)
        .navigationTitle("Past lists")
        .deleteListAlert(isPresented: Binding(get: { deletingList != nil }, set: { if !$0 { deletingList = nil } })) {
            guard let list = deletingList else { return }
            Task {
                do { try await data.groceries.deleteList(list.id) } catch { failed = true }
            }
        }
        .alert("Couldn't delete that list. Try again.", isPresented: $failed) { Button("OK", role: .cancel) {} }
    }
}
