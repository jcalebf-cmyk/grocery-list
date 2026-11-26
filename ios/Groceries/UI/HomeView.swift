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
    /// The "Ask for a recipe" conversation, kept on the phone until Start over.
    let recipeChat: RecipeChat

    var household: Household?
    var stores: [Store] = []
    var lists: [GroceryList] = []
    var stats: [ItemStat] = []
    /// Saved recipes, newest first.
    var recipes: [Recipe] = []
    var members: [UserProfile] = []
    /// Every household they're in, by name. Home's title switches between them once there's more than one.
    var households: [Household] = []

    init(user: UserProfile, householdId: String) {
        self.user = user
        self.householdId = householdId
        groceries = GroceryService(householdId: householdId)
        recipeService = RecipeService(householdId: householdId)
        recipeChat = RecipeChat(uid: user.id, householdId: householdId, service: recipeService)
    }

    var activeLists: [GroceryList] { lists.filter(\.isActive) }
    /// Closed-out lists, newest first. Home shows only how many; Past lists shows them.
    var pastLists: [GroceryList] { lists.filter { !$0.isActive } }
    var yourStores: [Store] { storesInUse(stores, lists: lists) }
    var otherHouseholds: [Household] { households.filter { $0.id != householdId } }
    var isOwner: Bool { household?.ownerId == user.id }
    var otherMembers: [String: String] {
        members.count > 1 ? Dictionary(uniqueKeysWithValues: members.filter { $0.id != user.id }.map { ($0.id, $0.displayName) }) : [:]
    }

    func openList(for store: Store?) -> String { groceries.openList(for: store, uid: user.id, activeLists: activeLists).id }
    func openList(forNewStore name: String) -> String { openList(for: groceries.addStore(name, existing: stores)) }

    func switchHousehold(to id: String) { HouseholdService.switchHousehold(uid: user.id, to: id) }

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
    @State private var width: CGFloat = 375
    @Namespace private var zoom

    init(user: UserProfile, householdId: String) {
        _data = State(initialValue: HouseholdData(user: user, householdId: householdId))
    }

    var body: some View {
        NavigationStack(path: $path) {
            Group {
                if twoPane {
                    // Wide screen (an opened iPhone Duo): lists on one side, recipes on the other.
                    HStack(alignment: .top, spacing: 0) {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 0) {
                                listsSection
                                pastListsRow
                            }
                            .padding(.bottom, 24)
                            .animation(.snappy, value: listsShown)
                        }
                        Divider().ignoresSafeArea(edges: .bottom)
                        ScrollView { recipesSection(top: 0).padding(.bottom, 24) }
                    }
                } else {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 0) {
                            // Lists come first: they're what people open the app for.
                            listsSection
                            pastListsRow
                            recipesSection(top: 16)
                        }
                        .padding(.bottom, 24)
                        // New lists slide in, and closed-out ones fold away.
                        .animation(.snappy, value: listsShown)
                        .readableWidth()
                    }
                }
            }
            .onTwoPaneChange { twoPane = $0 }
            .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
            .background(Color.warmBackground)
            .navigationTitle(data.household?.name ?? "Groceries")
            .toolbar {
                if data.households.count > 1 {
                    ToolbarItem(placement: .largeTitle) { HouseholdTitle(data: data, maxWidth: max(width - 32, 100)) }
                    // The small title, once scrolled.
                    ToolbarTitleMenu { HouseholdPicker(data: data) }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink(value: Route.settings) { Image(systemName: "gearshape") }
                        .accessibilityLabel("Settings")
                }
            }
            .navigationDestination(for: Route.self) { route in
                switch route {
                case .list(let id):
                    // Opened from Home, the list grows out of its row.
                    ListView(data: data, listId: id)
                        .navigationTransition(.zoom(sourceID: ListRowView.zoomID(id), in: zoom))
                case .recipe(let id):
                    RecipeView(data: data, recipeId: id, onOpenList: { path = [.list($0)] })
                        .navigationTransition(.zoom(sourceID: id, in: zoom))
                case .recipes: AllRecipesView(data: data, onPaste: { pasting = true })
                case .recipeChat: RecipeChatView(data: data, onOpenList: { path = [.list($0)] })
                case .pastLists: PastListsView(data: data, zoom: zoom)
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
                        .glassEffect(in: .capsule).padding(.bottom, 16)
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
        .task { await consume(HouseholdService.householdsStream(data.user.id)) { v in data.households = v.byName } }
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

    /// The recipe box, in its own pane on a wide screen (where it starts level with Shopping lists).
    private func recipesSection(top: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            sectionTitle("Recipes", top: top)
            VStack(alignment: .leading, spacing: 12) {
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
                } else {
                    // Looks like a search field; opens the recipes screen ready to type in.
                    NavigationLink(value: Route.recipes) {
                        Label("Search recipes", systemImage: "magnifyingglass")
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 16)
                            .frame(maxWidth: .infinity, minHeight: 44, alignment: .leading)
                            .background(Color.card, in: .capsule)
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 16)
                    RecipeGrid(matches: data.recipes.map { RecipeMatch(recipe: $0) }, zoom: zoom)
                }
            }
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
        sectionTitle("Shopping lists") {
            // Sits with the lists, not floating over the recipes, so it's clear what it starts.
            // With no list going, the Start a list card does this job instead.
            if !data.activeLists.isEmpty {
                Button { pickingStore = true } label: {
                    Label("New list", systemImage: "plus").font(.subheadline.weight(.semibold)).frame(minHeight: 30)
                }
                .prominentGlassButton(foreground: .onCitrus)
                .tint(.citrus)
            }
        }
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
                        .contentTransition(.numericText(value: Double(data.pastLists.count)))
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

    /// A section's title, with its button at the end of the line (or under it, when large text leaves no room). The
    /// same height with or without one, so two panes line up.
    private func sectionTitle(_ text: String, top: CGFloat = 0, @ViewBuilder action: () -> some View = { EmptyView() }) -> some View {
        let title = Text(text).font(.brand(22)).accessibilityAddTraits(.isHeader)
        return ViewThatFits(in: .horizontal) {
            HStack {
                title.lineLimit(1)
                Spacer()
                action()
            }
            VStack(alignment: .leading, spacing: 8) {
                title
                action()
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.bottom, 4)
        }
        .frame(minHeight: 52)
        .padding(.leading, 20)
        .padding(.trailing, 16)
        .padding(.top, top)
    }

    private func listRow(_ list: GroceryList) -> some View { ListRowView(list: list, zoom: zoom) }

    /// What Home shows of the lists, so adding, closing out, or deleting one animates.
    private var listsShown: [String] { data.activeLists.map(\.id) + ["past \(data.pastLists.count)"] }
}

/// The open household's name, which switches between households when tapped. Android: HouseholdTitle.
struct HouseholdTitle: View {
    let data: HouseholdData
    let maxWidth: CGFloat

    var body: some View {
        Menu {
            HouseholdPicker(data: data)
        } label: {
            HStack(spacing: 8) {
                Text(data.household?.name ?? "Groceries").font(.largeTitle.bold()).lineLimit(1)
                Image(systemName: "chevron.down.circle.fill")
                    .font(.title3.weight(.semibold))
                    .symbolRenderingMode(.hierarchical)
                    .foregroundStyle(.secondary)
            }
            // Told how wide it can be, since the bar lays it out at full length: a long name would be shrunk.
            .frame(maxWidth: maxWidth, alignment: .leading)
            .contentShape(.rect)
        }
        .tint(.primary)
        .accessibilityHint("Switches household")
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct HouseholdPicker: View {
    let data: HouseholdData

    var body: some View {
        Picker("Household", selection: Binding(
            get: { data.householdId },
            set: { if $0 != data.householdId { data.switchHousehold(to: $0) } }
        )) {
            ForEach(data.households) { Text($0.name).tag($0.id) }
        }
        .pickerStyle(.inline)
        .labelsHidden()
    }
}

/// A shopping list on Home or in Past lists: store logo, name, and when it started or was closed out.
struct ListRowView: View {
    let list: GroceryList
    /// Opening the list zooms out of its row.
    var zoom: Namespace.ID?

    static func zoomID(_ listId: String) -> String { "list-\(listId)" }

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
            .modifier(ZoomSource(id: Self.zoomID(list.id), namespace: zoom))
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .padding(.vertical, 4)
    }
}

/// Every closed-out list, newest first. Swipe left to delete one. Android: PastListsScreen.
struct PastListsView: View {
    let data: HouseholdData
    var zoom: Namespace.ID?
    @State private var deletingList: GroceryList?
    @State private var failed = false

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                ForEach(data.pastLists) { list in
                    SwipeAction(label: "Delete", systemImage: "trash", tint: .red, action: { deletingList = list }) {
                        ListRowView(list: list, zoom: zoom)
                    }
                }
                if data.pastLists.isEmpty {
                    Text("Lists you close out show up here.").foregroundStyle(.secondary).padding(20)
                }
            }
            .padding(.vertical, 8)
            .readableWidth()
            .animation(.snappy, value: data.pastLists.map(\.id))
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
