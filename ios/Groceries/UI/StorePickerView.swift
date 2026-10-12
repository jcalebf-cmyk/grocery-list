import CoreLocation
import SwiftUI

/// Full-screen grid for choosing a store: the 8 closest grocery stores, then the household's other
/// saved stores, then "Add store". Asks for location the first time it opens.
struct StorePickerView: View {
    let data: HouseholdData
    let onPick: (Store) -> Void
    let onPickNew: (String) -> Void
    let onSkip: () -> Void
    let onCancel: () -> Void
    var title = "Where are you shopping?"

    @State private var nearby: [NearbyStore]? = NearbyStores.shared.peek()
    @State private var nearbyFailed = false
    @State private var authorization = NearbyStores.shared.authorization
    @State private var addingStore = false
    @State private var newStoreName = ""
    @State private var removing: Store?
    @Environment(\.scenePhase) private var scenePhase

    private let nearbyShown = 8
    private let columns = Array(repeating: GridItem(.flexible(), spacing: 10, alignment: .top), count: 4)
    private var locationAllowed: Bool { authorization == .authorizedWhenInUse || authorization == .authorizedAlways }
    private var shownNearby: [NearbyStore] { Array((nearby ?? []).prefix(nearbyShown)) }
    private var otherSaved: [Store] {
        data.yourStores.filter { s in !shownNearby.contains { $0.name.caseInsensitiveCompare(s.name) == .orderedSame } }
    }
    private var openStoreIds: Set<String> { Set(data.activeLists.map(\.storeId)) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    sectionTitle("Near you")
                    nearbyContent
                    sectionTitle(otherSaved.isEmpty ? "Somewhere else?" : "Your stores")
                    LazyVGrid(columns: columns, spacing: 16) {
                        ForEach(otherSaved) { store in
                            StoreTile(name: store.name, badge: openStoreIds.contains(store.id) ? "List open" : nil) { onPick(store) }
                                .contextMenu {
                                    Button("Remove from Your stores", systemImage: "minus.circle", role: .destructive) { removing = store }
                                }
                        }
                        Button { addingStore = true } label: {
                            VStack(spacing: 6) {
                                Image(systemName: "plus").font(.title2.weight(.semibold))
                                    .frame(width: 54, height: 54)
                                    .glassEffect(.regular.tint(.primary.opacity(0.85)).interactive(), in: .circle)
                                    .foregroundStyle(Color.warmBackground)
                                    .frame(height: 64)
                                Text("Add store").font(.footnote.weight(.medium))
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 24)
            }
            .background(Color.warmBackground)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { Button("Cancel", systemImage: "xmark", action: onCancel) }
                ToolbarItem(placement: .bottomBar) {
                    Button("Skip, start a list without a store", action: onSkip).buttonStyle(.glass)
                }
            }
            .alert("Add a store", isPresented: $addingStore) {
                TextField("Store name", text: $newStoreName).textInputAutocapitalization(.words)
                Button("Add") { if !newStoreName.trimmed.isEmpty { onPickNew(newStoreName) }; newStoreName = "" }
                Button("Cancel", role: .cancel) { newStoreName = "" }
            }
            .alert("Remove \(removing?.name ?? "")?", isPresented: Binding(get: { removing != nil }, set: { if !$0 { removing = nil } })) {
                Button("Remove", role: .destructive) { if let removing { data.groceries.removeStore(removing) }; removing = nil }
                Button("Cancel", role: .cancel) { removing = nil }
            } message: {
                Text("It won't show under Your stores. Its lists stay as they are.")
            }
        }
        .task {
            if authorization == .notDetermined { authorization = await NearbyStores.shared.requestPermission() }
            await loadNearby()
        }
        .onChange(of: scenePhase) {
            // Back from Settings with location turned on.
            if scenePhase == .active {
                authorization = NearbyStores.shared.authorization
                Task { await loadNearby() }
            }
        }
    }

    @ViewBuilder private var nearbyContent: some View {
        if !locationAllowed {
            VStack(alignment: .leading, spacing: 8) {
                Label("See grocery stores near you", systemImage: "location.fill").font(.headline)
                Text("Location is off for Groceries. Turn it on in Settings to see the \(nearbyShown) closest grocery stores here. Only your approximate area is used.")
                    .font(.subheadline).foregroundStyle(.secondary)
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(url) }
                }
                .buttonStyle(.glassProminent)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        } else if nearbyFailed && nearby == nil {
            Button("Couldn't load nearby stores. Tap to try again.") { Task { await loadNearby() } }
        } else if let nearby, nearby.isEmpty {
            Text("No grocery stores within 10 miles").foregroundStyle(.secondary)
        } else {
            LazyVGrid(columns: columns, spacing: 16) {
                if nearby == nil {
                    ForEach(0..<nearbyShown, id: \.self) { _ in
                        VStack(spacing: 8) {
                            RoundedRectangle(cornerRadius: 14).fill(Color.card).frame(height: 64)
                            RoundedRectangle(cornerRadius: 4).fill(Color.card).frame(width: 56, height: 10)
                        }
                    }
                } else {
                    ForEach(shownNearby, id: \.name) { store in
                        let saved = data.yourStores.first { $0.name.caseInsensitiveCompare(store.name) == .orderedSame }
                        StoreTile(name: store.name, subtitle: distance(store.distanceMeters),
                                  badge: saved.map { openStoreIds.contains($0.id) } == true ? "List open" : nil) {
                            if let saved { onPick(saved) } else { onPickNew(store.name) }
                        }
                    }
                }
            }
        }
    }

    private func loadNearby() async {
        guard locationAllowed else { return }
        nearbyFailed = false
        do { nearby = try await NearbyStores.shared.find() } catch { nearbyFailed = true }
    }

    private func sectionTitle(_ text: String) -> some View {
        Text(text).font(.brand(20)).padding(.top, 12)
    }

    private func distance(_ meters: Int) -> String {
        Measurement(value: Double(meters), unit: UnitLength.meters)
            .formatted(.measurement(width: .abbreviated, usage: .road, numberFormatStyle: .number.precision(.fractionLength(1))))
    }
}

private struct StoreTile: View {
    let name: String
    var subtitle: String?
    var badge: String?
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(spacing: 6) {
                StoreLogo(name: name, cornerRadius: 14, shadow: true).frame(height: 64)
                Text(name).font(.footnote.weight(.medium)).lineLimit(1)
                if let badge {
                    Text(badge).font(.caption2.weight(.semibold)).padding(.horizontal, 6).padding(.vertical, 1)
                        .background(Color.citrusContainer, in: RoundedRectangle(cornerRadius: 4))
                } else if let subtitle {
                    Text(subtitle).font(.caption2).foregroundStyle(.secondary)
                }
            }
        }
        .buttonStyle(.plain)
    }
}
