import SwiftUI

enum Route: Hashable {
    case list(String)
    case recipe(String)
    case kitchen
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
    var onHand: [OnHandItem] = []
    var recipeSet: RecipeSet?
    var members: [UserProfile] = []
    private var inputsLoaded = false

    init(user: UserProfile, householdId: String) {
        self.user = user
        self.householdId = householdId
        groceries = GroceryService(householdId: householdId)
        recipeService = RecipeService(householdId: householdId)
    }

    var activeLists: [GroceryList] { lists.filter(\.isActive) }
    var pastLists: [GroceryList] { Array(lists.filter { !$0.isActive }.prefix(10)) }
    var yourStores: [Store] { storesInUse(stores, lists: lists) }
    var recipes: [Recipe] { recipeSet?.recipes ?? [] }
    var isOwner: Bool { household?.ownerId == user.id }
    var otherMembers: [String: String] {
        members.count > 1 ? Dictionary(uniqueKeysWithValues: members.filter { $0.id != user.id }.map { ($0.id, $0.displayName) }) : [:]
    }

    func openList(for store: Store?) -> String { groceries.openList(for: store, uid: user.id, activeLists: activeLists).id }
    func openList(forNewStore name: String) -> String { openList(for: groceries.addStore(name, existing: stores)) }

    /// Called once the first snapshots settle, and whenever Home reappears (e.g. after editing the kitchen).
    func refreshRecipesIfStale() async {
        guard inputsLoaded else { return }
        _ = await recipeService.refresh(stats: stats, onHand: onHand, force: false)
    }

    func markInputsLoaded() async {
        try? await Task.sleep(for: .seconds(2)) // first snapshots can come from an empty local cache
        inputsLoaded = true
        await refreshRecipesIfStale()
    }

    func refreshMembers() async {
        members = await HouseholdService.members(household?.memberIds ?? [])
    }
}

struct HomeView: View {
    @State private var data: HouseholdData
    @State private var path: [Route] = []
    @State private var pickingStore = false
    @State private var message: String?
    @Namespace private var zoom

    init(user: UserProfile, householdId: String) {
        _data = State(initialValue: HouseholdData(user: user, householdId: householdId))
    }

    var body: some View {
        NavigationStack(path: $path) {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    recipesSection
                    listsSection
                }
                .padding(.bottom, 24)
            }
            .background(Color.warmBackground)
            .navigationTitle(data.household?.name ?? "Groceries")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink(value: Route.settings) { Image(systemName: "gearshape") }
                        .accessibilityLabel("Settings")
                }
                ToolbarItem(placement: .bottomBar) {
                    Button { pickingStore = true } label: {
                        Label("New list", systemImage: "plus").fontWeight(.semibold).padding(.horizontal, 8)
                    }
                    .buttonStyle(.glassProminent)
                    .tint(.citrus)
                }
            }
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .list(let id): ListView(data: data, listId: id)
                case .recipe(let id):
                    RecipeView(data: data, videoId: id, onOpenList: { path = [.list($0)] })
                        .navigationTransition(.zoom(sourceID: id, in: zoom))
                case .kitchen: KitchenView(data: data)
                case .settings: SettingsView(data: data)
                }
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
        .task { await consume(data.groceries.onHandStream()) { v in data.onHand = v } }
        .task { await consume(data.recipeService.stream()) { v in data.recipeSet = v } }
        .task {
            await consume(HouseholdService.household(data.householdId)) { v in
                data.household = v
                await data.refreshMembers()
            }
        }
        .task { await data.markInputsLoaded() }
        .onChange(of: path) { if path.isEmpty { Task { await data.refreshRecipesIfStale() } } }
    }

    // MARK: Recipes

    private var recipesSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Recipes for you").font(.brand(24))
                if let status = recipeStatus {
                    Text(status).font(.footnote).foregroundStyle(.secondary)
                }
            }
            .padding(.horizontal, 20)
            HStack(spacing: 8) {
                NavigationLink(value: Route.kitchen) {
                    Label(data.onHand.isEmpty ? "My kitchen" : "My kitchen · \(data.onHand.count)", systemImage: "refrigerator")
                }
                .buttonStyle(.glass)
                Button { Task { await newIdeas() } } label: {
                    if data.recipeService.refreshing {
                        HStack(spacing: 6) { ProgressView().controlSize(.small); Text("New ideas") }
                    } else {
                        Label("New ideas", systemImage: "arrow.clockwise")
                    }
                }
                .buttonStyle(.glass)
                .disabled(data.recipeService.refreshing)
            }
            .font(.subheadline.weight(.medium))
            .padding(.horizontal, 20)

            if !data.recipes.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    LazyHStack(spacing: 14) {
                        ForEach(data.recipes) { recipe in
                            NavigationLink(value: Route.recipe(recipe.videoId)) { RecipeCard(recipe: recipe) }
                                .buttonStyle(.plain)
                                .matchedTransitionSource(id: recipe.videoId, in: zoom)
                        }
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 4)
                }
            } else if !data.recipeService.refreshing {
                VStack(alignment: .leading, spacing: 10) {
                    Text("What's in your kitchen?").font(.headline)
                    Text("Add a few ingredients you already have and you'll get simple recipes that use them.")
                        .font(.subheadline).foregroundStyle(.secondary)
                    NavigationLink("Add ingredients", value: Route.kitchen).buttonStyle(.glassProminent)
                }
                .padding(16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
                .padding(.horizontal, 20)
            }
        }
        .padding(.top, 8)
    }

    private var recipeStatus: String? {
        if data.recipeService.refreshing { return "Finding new recipes…" }
        guard !data.recipes.isEmpty, let generated = data.recipeSet?.generatedAt else { return nil }
        let date = Date(timeIntervalSince1970: TimeInterval(generated) / 1000)
        return "Updated " + date.formatted(.relative(presentation: .named))
    }

    private func newIdeas() async {
        switch await data.recipeService.refresh(stats: data.stats, onHand: data.onHand, force: true) {
        case .notEnoughHistory: show("Add a few ingredients to My kitchen first")
        case .rateLimited: show("That's all the new recipe ideas for today. Check back tomorrow.")
        case .failed: show("Couldn't get new recipes. Try again in a bit.")
        default: break
        }
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
        sectionTitle("Shopping lists")
        if data.activeLists.isEmpty {
            Text("Tap New list and pick a store to start").foregroundStyle(.secondary).padding(.horizontal, 20)
        }
        ForEach(data.activeLists) { list in listRow(list) }
        if !data.pastLists.isEmpty {
            sectionTitle("Past lists")
            ForEach(data.pastLists) { list in listRow(list) }
        }
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text).font(.brand(22)).padding(.horizontal, 20).padding(.top, 28).padding(.bottom, 8)
    }

    private func listRow(_ list: GroceryList) -> some View {
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

/// Full-bleed photo with the title on frosted glass, so the food does the talking.
struct RecipeCard: View {
    let recipe: Recipe

    var body: some View {
        AsyncImage(url: URL(string: recipe.thumbnailUrl)) { image in
            image.resizable().scaledToFill()
        } placeholder: {
            Color.card
        }
        .frame(width: 280, height: 210)
        .overlay(alignment: .topLeading) {
            Text("\(recipe.totalMinutes) min").font(.caption.weight(.semibold))
                .padding(.horizontal, 10).padding(.vertical, 5)
                .glassEffect(in: .capsule)
                .padding(10)
        }
        .overlay(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 2) {
                Text(recipe.title).font(.headline).lineLimit(2)
                Text("\(recipe.ingredients.count) ingredients · \(recipe.channel)").font(.caption).foregroundStyle(.secondary).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14).padding(.vertical, 10)
            .glassEffect(.regular, in: UnevenRoundedRectangle(bottomLeadingRadius: 24, bottomTrailingRadius: 24, style: .continuous))
        }
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
    }
}
