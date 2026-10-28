import FirebaseFirestore

/// A household's stores, lists, items, stats, and kitchen. Same documents as Android's GroceryRepository.
/// Writes don't wait for the server: Firestore applies them locally at once and syncs when it can.
struct GroceryService {
    let householdId: String
    private var db: Firestore { Firestore.firestore() }
    private var household: DocumentReference { db.collection("households").document(householdId) }
    private var stores: CollectionReference { household.collection("stores") }
    private var lists: CollectionReference { household.collection("lists") }
    private var stats: CollectionReference { household.collection("itemStats") }
    private var onHand: CollectionReference { household.collection("onHand") }

    func storesStream() -> AsyncThrowingStream<[Store], Error> { stores.order(by: "createdAt").stream(Store.self) }
    func listsStream() -> AsyncThrowingStream<[GroceryList], Error> { lists.order(by: "createdAt", descending: true).stream(GroceryList.self) }
    func listStream(_ id: String) -> AsyncThrowingStream<GroceryList?, Error> { lists.document(id).stream(GroceryList.self) }
    func itemsStream(_ listId: String) -> AsyncThrowingStream<[ListItem], Error> { lists.document(listId).collection("items").stream(ListItem.self) }
    func statsStream() -> AsyncThrowingStream<[ItemStat], Error> { stats.stream(ItemStat.self) }
    func onHandStream() -> AsyncThrowingStream<[OnHandItem], Error> { onHand.order(by: "createdAt").stream(OnHandItem.self) }

    /// Reuses an existing store with the same name instead of adding a duplicate.
    func addStore(_ name: String, existing: [Store]) -> Store {
        if let match = existing.first(where: { $0.name.caseInsensitiveCompare(name.trimmed) == .orderedSame }) { return match }
        let doc = stores.document()
        let created = nowMillis()
        doc.setData(["name": name.trimmed, "createdAt": created])
        return Store(docId: doc.documentID, name: name.trimmed, createdAt: created)
    }

    func removeStore(_ store: Store) { stores.document(store.id).delete() }

    /// Reuses the store's open list if there is one. A nil store means the list that isn't tied to a store.
    func openList(for store: Store?, uid: String, activeLists: [GroceryList]) -> GroceryList {
        let storeId = store?.id ?? ""
        if let open = activeLists.first(where: { $0.storeId == storeId }) { return open }
        let doc = lists.document()
        let list = GroceryList(docId: doc.documentID, storeId: storeId, storeName: store?.name ?? "", createdBy: uid, createdAt: nowMillis())
        doc.setData(["storeId": list.storeId, "storeName": list.storeName, "status": GroceryList.statusActive,
                     "createdBy": uid, "createdAt": list.createdAt])
        return list
    }

    func addItem(to list: GroceryList, name: String, uid: String) {
        let trimmed = name.trimmed
        guard !trimmed.isEmpty else { return }
        let key = itemKey(trimmed)
        let now = nowMillis()
        let batch = db.batch()
        batch.setData(["name": trimmed, "key": key, "checked": false, "addedBy": uid, "createdAt": now],
                      forDocument: lists.document(list.id).collection("items").document())
        batch.setData(statDelta(displayName: trimmed, storeId: list.storeId, by: 1, now: now), forDocument: stats.document(key), merge: true)
        batch.commit()
    }

    func setChecked(_ item: ListItem, in listId: String, _ checked: Bool) {
        lists.document(listId).collection("items").document(item.id).updateData(["checked": checked])
    }

    /// Deleting an unchecked item usually means it was a mistake, so it stops counting toward suggestions.
    func deleteItem(_ item: ListItem, from list: GroceryList) {
        let batch = db.batch()
        batch.deleteDocument(lists.document(list.id).collection("items").document(item.id))
        if !item.checked {
            batch.setData(statDelta(displayName: nil, storeId: list.storeId, by: -1, now: nil), forDocument: stats.document(item.key), merge: true)
        }
        batch.commit()
    }

    /// Fixing a typo moves the item's suggestion count to the new name, so the misspelling stops being suggested.
    func renameItem(_ item: ListItem, in list: GroceryList, to name: String) {
        let trimmed = name.trimmed
        guard !trimmed.isEmpty, trimmed != item.name else { return }
        let key = itemKey(trimmed)
        let batch = db.batch()
        batch.updateData(["name": trimmed, "key": key], forDocument: lists.document(list.id).collection("items").document(item.id))
        if key != item.key {
            batch.setData(statDelta(displayName: nil, storeId: list.storeId, by: -1, now: nil), forDocument: stats.document(item.key), merge: true)
            batch.setData(statDelta(displayName: trimmed, storeId: list.storeId, by: 1, now: nowMillis()), forDocument: stats.document(key), merge: true)
        } else {
            batch.setData(["displayName": trimmed], forDocument: stats.document(key), merge: true)
        }
        batch.commit()
    }

    func clearChecked(in listId: String, items: [ListItem]) {
        let batch = db.batch()
        for item in items where item.checked { batch.deleteDocument(lists.document(listId).collection("items").document(item.id)) }
        batch.commit()
    }

    /// Closes out a trip; [carryOver] items move to a fresh list for the same store.
    /// Moves the list to Past lists. Unchecked items stay on it, and it can be reopened.
    func closeOut(_ listId: String) {
        lists.document(listId).updateData(["status": GroceryList.statusDone, "completedAt": nowMillis()])
    }

    /// Deletes a list and its items. Suggestions keep what the list taught them, so habits don't reset.
    /// The list document goes last, so an interrupted delete leaves it visible to retry.
    func deleteList(_ listId: String) async throws {
        let ref = lists.document(listId)
        let refs = try await ref.collection("items").getDocuments().documents.map(\.reference) + [ref]
        // Batches are capped at 500 writes.
        for start in stride(from: 0, to: refs.count, by: 450) {
            let batch = db.batch()
            for doc in refs[start..<min(start + 450, refs.count)] { batch.deleteDocument(doc) }
            try await batch.commit()
        }
    }

    func reopen(_ listId: String) {
        lists.document(listId).updateData(["status": GroceryList.statusActive, "completedAt": FieldValue.delete()])
    }

    /// Not counted in item stats: kitchen items feed recipe ideas, not shopping suggestions.
    func addOnHand(_ name: String, uid: String, pantry: Bool = false) {
        let trimmed = name.trimmed
        guard !trimmed.isEmpty else { return }
        var data: [String: Any] = ["name": trimmed, "key": itemKey(trimmed), "addedBy": uid, "createdAt": nowMillis()]
        if pantry { data["kind"] = OnHandItem.pantryKind }
        onHand.document(itemKey(trimmed)).setData(data)
    }

    func removeOnHand(_ item: OnHandItem) { onHand.document(item.id).delete() }

    private func statDelta(displayName: String?, storeId: String, by: Int64, now: Int64?) -> [String: Any] {
        var data: [String: Any] = ["count": FieldValue.increment(by)]
        if let displayName { data["displayName"] = displayName }
        if let now { data["lastAdded"] = now }
        // Lists without a store still count toward overall habits, just not any store's.
        if !storeId.isEmpty { data["storeCounts"] = [storeId: FieldValue.increment(by)] }
        return data
    }
}
