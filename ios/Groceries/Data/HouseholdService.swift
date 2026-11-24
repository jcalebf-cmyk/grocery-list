import FirebaseAuth
import FirebaseFirestore

/// Profiles and households. Same documents and rules as the Android app's HouseholdRepository.
enum HouseholdService {
    /// As many as the security rules check when someone reads a profile.
    static let maxHouseholds = 8

    private static var db: Firestore { Firestore.firestore() }
    private static var users: CollectionReference { db.collection("users") }
    private static var households: CollectionReference { db.collection("households") }
    private static var inviteCodes: CollectionReference { db.collection("inviteCodes") }

    static func profileSaving(_ uid: String) -> AsyncThrowingStream<(value: UserProfile?, saving: Bool), Error> {
        users.document(uid).streamSaving(UserProfile.self)
    }
    static func household(_ id: String) -> AsyncThrowingStream<Household?, Error> { households.document(id).stream(Household.self) }
    /// Only the server's copy. A household created on this phone is in its cache straight away, but
    /// security rules deny reading its lists and kitchen until the server has saved it.
    static func confirmedHousehold(_ id: String) -> AsyncThrowingStream<Household?, Error> {
        households.document(id).stream(Household.self, confirmedOnly: true)
    }

    /// Every household `uid` is in. Asked of the households themselves, so it's right however someone was
    /// added or removed. Sort with `byName`.
    static func householdsStream(_ uid: String) -> AsyncThrowingStream<[Household], Error> { mine(uid).stream(Household.self) }

    /// Every household `uid` is in, by name, read once. From the server: this phone's copy may be missing some.
    static func householdsOf(_ uid: String) async throws -> [Household] {
        try await mine(uid).getDocuments(source: .server).documents.compactMap { try? $0.data(as: Household.self) }.byName
    }

    private static func mine(_ uid: String) -> Query { households.whereField("memberIds", arrayContains: uid) }

    static func members(_ ids: [String]) async -> [UserProfile] {
        var result: [UserProfile] = []
        for id in ids {
            if let p = try? await users.document(id).getDocument(as: UserProfile.self) { result.append(p) }
        }
        return result.sorted { $0.displayName < $1.displayName }
    }

    /// The household `uid` has open, read once (for voice actions). Nil without one, or after being removed
    /// from it (security rules then refuse reading it).
    static func currentHouseholdId(uid: String) async throws -> String? {
        let profile = try await users.document(uid).getDocument()
        guard let householdId = (try? profile.data(as: UserProfile.self))?.householdId else { return nil }
        do {
            let household = try await households.document(householdId).getDocument()
            guard household.exists, let members = try? household.data(as: Household.self).memberIds,
                  members.contains(uid) else { return nil }
            return householdId
        } catch let error as NSError where error.domain == FirestoreErrorDomain && error.code == FirestoreErrorCode.permissionDenied.rawValue {
            return nil
        }
    }

    /// Creates or refreshes the user's profile after sign-in. Leaves householdId alone.
    static func ensureProfile(_ user: User) {
        let name = user.displayName ?? user.email?.components(separatedBy: "@").first ?? "Me"
        users.document(user.uid).setData(["displayName": name, "email": user.email ?? ""], merge: true)
    }

    /// Brings the profile's list of households up to date with the households themselves.
    static func syncHouseholdIds(_ uid: String) async throws {
        let actual = try await mine(uid).getDocuments(source: .server).documents.map(\.documentID)
        let profile = try await users.document(uid).getDocument(source: .server)
        guard profile.exists else { return }
        let listed = profile.get("householdIds") as? [String] ?? []
        // Adds and removes rather than replacing, so a household joined meanwhile isn't dropped.
        let missing = actual.filter { !listed.contains($0) }
        let stale = listed.filter { !actual.contains($0) }
        if !missing.isEmpty { try await users.document(uid).updateData(["householdIds": FieldValue.arrayUnion(missing)]) }
        if !stale.isEmpty { try await users.document(uid).updateData(["householdIds": FieldValue.arrayRemove(stale)]) }
    }

    /// Opens another household the user is in, on all their devices. Works offline.
    static func switchHousehold(uid: String, to householdId: String) {
        users.document(uid).updateData(["householdId": householdId])
    }

    /// The profile change that goes with joining or starting a household: it opens, alongside any others.
    private static func opening(_ householdId: String, for user: UserProfile) -> [String: Any] {
        ["householdId": householdId,
         "householdIds": FieldValue.arrayUnion([user.householdId, householdId].compactMap { $0 })]
    }

    static func createHousehold(user: UserProfile, name: String) async throws {
        let code = try await unusedInviteCode()
        let household = households.document()
        let batch = db.batch()
        batch.setData(["name": name.trimmed, "inviteCode": code, "memberIds": [user.id]], forDocument: household)
        batch.setData(["householdId": household.documentID], forDocument: inviteCodes.document(code))
        batch.setData(opening(household.documentID, for: user), forDocument: users.document(user.id), merge: true)
        try await batch.commit()
    }

    /// Returns false if no household uses this code.
    static func joinHousehold(user: UserProfile, code: String) async throws -> Bool {
        let normalized = code.trimmed.uppercased()
        guard let householdId = try await inviteCodes.document(normalized).getDocument().get("householdId") as? String
        else { return false }
        // Already in it: the code just opens it.
        if try await householdsOf(user.id).contains(where: { $0.id == householdId }) {
            switchHousehold(uid: user.id, to: householdId)
            return true
        }
        let batch = db.batch()
        // Security rules only let a non-member add themselves when they present the matching code.
        batch.updateData(["memberIds": FieldValue.arrayUnion([user.id]), "joinCode": normalized], forDocument: households.document(householdId))
        batch.setData(opening(householdId, for: user), forDocument: users.document(user.id), merge: true)
        try await batch.commit()
        return true
    }

    static func rename(_ householdId: String, to name: String) async throws {
        try await households.document(householdId).updateData(["name": name.trimmed])
    }

    static func updateDisplayName(uid: String, name: String) async throws {
        try await users.document(uid).updateData(["displayName": name.trimmed])
        let change = Auth.auth().currentUser?.createProfileChangeRequest()
        change?.displayName = name.trimmed
        try await change?.commitChanges()
    }

    /// Owner only: moves the new owner to the front of the member list.
    static func transferOwnership(_ household: Household, to newOwner: String) async throws {
        let reordered = [newOwner] + household.memberIds.filter { $0 != newOwner }
        try await households.document(household.id).updateData(["memberIds": reordered])
    }

    /// Takes you out of the household you have open, and opens `next`; without another, you'll be asked to
    /// create or join one. Not allowed for the owner, who has to hand ownership over first.
    /// `next` opens, and is saved, before leaving: by then nothing on screen is still following this household.
    static func leave(_ householdId: String, uid: String, next: String?) async throws {
        if let next { try await users.document(uid).updateData(["householdId": next]) }
        let batch = db.batch()
        batch.updateData(["memberIds": FieldValue.arrayRemove([uid])], forDocument: households.document(householdId))
        var profile: [String: Any] = ["householdIds": FieldValue.arrayRemove([householdId])]
        if next == nil { profile["householdId"] = FieldValue.delete() }
        batch.updateData(profile, forDocument: users.document(uid))
        try await batch.commit()
    }

    /// Owner only (enforced by security rules).
    static func removeMember(_ householdId: String, member: String) async throws {
        try await households.document(householdId).updateData(["memberIds": FieldValue.arrayRemove([member])])
    }

    /// Clears a stale household link, e.g. after being removed by the owner.
    static func clearHousehold(uid: String) {
        users.document(uid).updateData(["householdId": FieldValue.delete()])
    }

    /// Deletes a household `uid` is the only member of, with everything in it. Opens `next` first, if given.
    /// Subcollections go first: the rules check membership on the household doc, so it must go last. Read from
    /// the server, since anything missed (say, with a bad connection) could never be reached again.
    static func deleteHousehold(_ householdId: String, uid: String, next: String? = nil) async throws {
        let ref = households.document(householdId)
        // Asks the server, in case someone has just joined.
        let snapshot = try await ref.getDocument(source: .server)
        guard snapshot.exists else { return }
        let household = try snapshot.data(as: Household.self)
        guard household.memberIds == [uid] else { throw NotAlone() }
        if let next { switchHousehold(uid: uid, to: next) }
        var docs: [DocumentReference] = []
        for name in ["stores", "itemStats", "onHand", "savedRecipes", "recipes"] {
            docs += try await ref.collection(name).getDocuments(source: .server).documents.map(\.reference)
        }
        for list in try await ref.collection("lists").getDocuments(source: .server).documents {
            docs += try await list.reference.collection("items").getDocuments(source: .server).documents.map(\.reference)
            docs.append(list.reference)
        }
        for start in stride(from: 0, to: docs.count, by: 450) {
            let batch = db.batch()
            docs[start..<min(start + 450, docs.count)].forEach { batch.deleteDocument($0) }
            try await batch.commit()
        }
        if !household.inviteCode.isEmpty { try await inviteCodes.document(household.inviteCode).delete() }
        try await ref.delete()
        if next != nil { try await users.document(uid).updateData(["householdIds": FieldValue.arrayRemove([householdId])]) }
    }

    struct NotAlone: LocalizedError {
        var errorDescription: String? { "Other people are in this household." }
    }

    private static func unusedInviteCode() async throws -> String {
        // No 0/O or 1/I/L, so codes are easy to read aloud.
        let alphabet = Array("ABCDEFGHJKMNPQRSTUVWXYZ23456789")
        while true {
            let code = String((0..<6).map { _ in alphabet.randomElement()! })
            if try await !inviteCodes.document(code).getDocument().exists { return code }
        }
    }
}

extension [Household] {
    var byName: [Household] { sorted { $0.name.lowercased() < $1.name.lowercased() } }
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
