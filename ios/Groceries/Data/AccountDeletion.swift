import FirebaseFirestore

/// Deletes the signed-in user's data and login; the caller must have just re-authenticated.
/// Only member: the household and everything in it go too. Shared household: the user leaves it,
/// handing ownership to `newOwner` first if they're the owner. Mirrors Android's AccountDeletion.
enum AccountDeletion {
    static func deleteAccount(uid: String, householdId: String?, newOwner: String?) async throws {
        let db = Firestore.firestore()
        if let householdId,
           let household = try? await db.collection("households").document(householdId).getDocument(as: Household.self) {
            if household.memberIds.count <= 1 {
                try await deleteHousehold(household, db: db)
            } else {
                if household.ownerId == uid, let newOwner {
                    try await HouseholdService.transferOwnership(household, to: newOwner)
                }
                try await HouseholdService.leave(householdId, uid: uid)
            }
        }
        try await db.collection("users").document(uid).delete()
        try await AuthService.deleteUser()
        await NearbyStores.shared.clear()
    }

    /// Subcollections first: the rules check membership on the household doc, so it must go last.
    private static func deleteHousehold(_ household: Household, db: Firestore) async throws {
        let ref = db.collection("households").document(household.id)
        var docs: [DocumentReference] = []
        for name in ["stores", "itemStats", "onHand", "recipes"] {
            docs += try await ref.collection(name).getDocuments().documents.map(\.reference)
        }
        for list in try await ref.collection("lists").getDocuments().documents {
            docs += try await list.reference.collection("items").getDocuments().documents.map(\.reference)
            docs.append(list.reference)
        }
        for start in stride(from: 0, to: docs.count, by: 450) {
            let batch = db.batch()
            docs[start..<min(start + 450, docs.count)].forEach { batch.deleteDocument($0) }
            try await batch.commit()
        }
        if !household.inviteCode.isEmpty { try await db.collection("inviteCodes").document(household.inviteCode).delete() }
        try await ref.delete()
    }
}
