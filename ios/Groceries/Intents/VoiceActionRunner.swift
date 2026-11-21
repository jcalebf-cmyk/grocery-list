import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import Foundation
import Observation

/// Why a voice action couldn't run; Siri says the message.
enum VoiceProblem: Error, CustomLocalizedStringResourceConvertible {
    case signedOut
    case noHousehold
    case notFound(String)

    var message: String {
        switch self {
        case .signedOut: VoiceText.signedOut
        case .noHousehold: VoiceText.noHousehold
        case .notFound(let message): message
        }
    }

    var localizedStringResource: LocalizedStringResource { "\(message)" }
}

/// A recipe a voice action asked to show, waiting for Home to open it.
@MainActor
@Observable
final class VoiceRouter {
    static let shared = VoiceRouter()
    var recipeId: String?
}

/// Which list a voice action is about, as Siri resolved it (see `StoreEntity`).
enum StoreChoice: Equatable {
    /// The list that isn't tied to a store.
    case plain
    case saved(id: String, name: String)
    /// A store name the household hasn't saved.
    case new(name: String)
}

/// The voice actions themselves. They run without a screen (Siri can start the app in the background), so they
/// read the signed-in user and household directly. Same behavior and wording as Android's
/// voice/VoiceActionRunner.kt: change both together.
@MainActor
enum VoiceActionRunner {
    struct Signed {
        let uid: String
        let householdId: String
        var groceries: GroceryService { GroceryService(householdId: householdId) }
    }

    /// Asks which of several open lists someone meant.
    typealias AskWhichList = ([GroceryList]) async throws -> GroceryList

    private static var prepared: Task<Void, Never>?

    /// Siri may start the app without its window, so Firebase and the shared sign-in are set up here too.
    static func prepare() async {
        if prepared == nil {
            prepared = Task {
                if FirebaseApp.app() == nil, Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
                    FirebaseApp.configure()
                }
                if FirebaseApp.app() != nil { await SharedSignIn.use() }
            }
        }
        await prepared?.value
    }

    static func signedIn() async throws -> Signed {
        await prepare()
        guard FirebaseApp.app() != nil, let uid = Auth.auth().currentUser?.uid else { throw VoiceProblem.signedOut }
        guard let householdId = try await HouseholdService.currentHouseholdId(uid: uid) else { throw VoiceProblem.noHousehold }
        return Signed(uid: uid, householdId: householdId)
    }

    /// Writes apply on this phone at once; this gives them a moment to reach the server before the app is suspended.
    static func sync() async {
        await withCheckedContinuation { (done: CheckedContinuation<Void, Never>) in
            let once = Once(done)
            Firestore.firestore().waitForPendingWrites { _ in Task { @MainActor in once.fire() } }
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(5))
                once.fire()
            }
        }
    }

    @MainActor
    private final class Once {
        private var continuation: CheckedContinuation<Void, Never>?
        init(_ continuation: CheckedContinuation<Void, Never>) { self.continuation = continuation }
        func fire() { continuation?.resume(); continuation = nil }
    }

    static func addItems(_ text: String, store: StoreChoice?, ask: AskWhichList) async throws -> String {
        let s = try await signedIn()
        let names = spokenItems(text)
        guard !names.isEmpty else { throw VoiceProblem.notFound(VoiceText.nothingToAdd) }
        let active = try await s.groceries.activeLists()
        let list: GroceryList
        if let store {
            list = s.groceries.openList(for: try await storeToUse(s, store, active), uid: s.uid, activeLists: active)
        } else {
            switch pickList(active) {
            case .one(let only): list = only
            case .noneOpen: list = s.groceries.openList(for: nil, uid: s.uid, activeLists: active)
            case .several(let lists): list = try await ask(lists)
            }
        }
        let onList = Set(try await s.groceries.items(list.id).filter { !$0.checked }.map { itemKey($0.name) })
        let alreadyThere = names.filter { onList.contains(itemKey($0)) }
        let toAdd = names.filter { !onList.contains(itemKey($0)) }
        for name in toAdd { s.groceries.addItem(to: list, name: name, uid: s.uid) }
        await sync()
        return VoiceText.added(toAdd, alreadyThere: alreadyThere, storeName: list.storeName)
    }

    static func readList(store: StoreChoice?, ask: AskWhichList) async throws -> String {
        let s = try await signedIn()
        let list = try await existingList(s, store, ask: ask)
        return VoiceText.readOut(try await s.groceries.items(list.id), storeName: list.storeName)
    }

    static func startList(store: StoreChoice) async throws -> String {
        let s = try await signedIn()
        let active = try await s.groceries.activeLists()
        let target = try await storeToUse(s, store, active)
        let alreadyGoing = openListFor(active, storeId: target?.id ?? "") != nil
        let list = s.groceries.openList(for: target, uid: s.uid, activeLists: active)
        await sync()
        return VoiceText.started(list.storeName, alreadyGoing: alreadyGoing)
    }

    static func checkOff(_ item: String, store: StoreChoice?, ask: AskWhichList) async throws -> String {
        let s = try await signedIn()
        let list = try await existingList(s, store, ask: ask)
        guard let match = findItem(try await s.groceries.items(list.id), name: item) else {
            throw VoiceProblem.notFound(VoiceText.notOnList(item, storeName: list.storeName))
        }
        if match.checked { return VoiceText.alreadyChecked(match.name) }
        s.groceries.setChecked(match, in: list.id, true)
        await sync()
        return VoiceText.checkedOff(match.name, storeName: list.storeName)
    }

    /// The store a list is for; nil for the list that isn't tied to one. Adds a store that isn't saved yet.
    private static func storeToUse(_ s: Signed, _ choice: StoreChoice, _ active: [GroceryList]) async throws -> Store? {
        switch choice {
        case .plain: return nil
        case .saved(let id, let name): return Store(docId: id, name: name)
        case .new(let name):
            if meansPlainList(name) { return nil }
            let saved = try await s.groceries.stores()
            if let match = findStore(saved + storesOf(active), name: name) { return match }
            let added = s.groceries.addStore(name, existing: saved)
            GroceriesShortcuts.updateAppShortcutParameters()
            return added
        }
    }

    /// An open list someone named, or the only one open. Never creates one.
    private static func existingList(_ s: Signed, _ choice: StoreChoice?, ask: AskWhichList) async throws -> GroceryList {
        let active = try await s.groceries.activeLists()
        switch choice {
        case nil:
            switch pickList(active) {
            case .one(let only): return only
            case .noneOpen: throw VoiceProblem.notFound(VoiceText.noOpenLists)
            case .several(let lists): return try await ask(lists)
            }
        case .plain:
            guard let list = openListFor(active, storeId: "") else { throw VoiceProblem.notFound(VoiceText.noOpenList("")) }
            return list
        case .saved(let id, let name):
            guard let list = openListFor(active, storeId: id) else { throw VoiceProblem.notFound(VoiceText.noOpenList(name)) }
            return list
        case .new(let name):
            if meansPlainList(name) { return try await existingList(s, .plain, ask: ask) }
            // A list can outlive its store being removed from the picker, so open lists are matched too.
            let match = findStore(storesOf(active) + (try await s.groceries.stores()), name: name)
            guard let match, let list = openListFor(active, storeId: match.id) else {
                throw VoiceProblem.notFound(VoiceText.noOpenList(match?.name ?? name.trimmed))
            }
            return list
        }
    }

    static func storesOf(_ lists: [GroceryList]) -> [Store] {
        lists.filter { !$0.storeId.isEmpty }.map { Store(docId: $0.storeId, name: $0.storeName) }
    }
}
