import FirebaseAuth
import FirebaseFirestore

/// Profiles and households. Same documents and rules as the Android app's HouseholdRepository.
enum HouseholdService {
    private static var db: Firestore { Firestore.firestore() }
    private static var users: CollectionReference { db.collection("users") }
    private static var households: CollectionReference { db.collection("households") }
    private static var inviteCodes: CollectionReference { db.collection("inviteCodes") }

    static func profile(_ uid: String) -> AsyncThrowingStream<UserProfile?, Error> { users.document(uid).stream(UserProfile.self) }
    static func household(_ id: String) -> AsyncThrowingStream<Household?, Error> { households.document(id).stream(Household.self) }

    static func members(_ ids: [String]) async -> [UserProfile] {
        var result: [UserProfile] = []
        for id in ids {
            if let p = try? await users.document(id).getDocument(as: UserProfile.self) { result.append(p) }
        }
        return result.sorted { $0.displayName < $1.displayName }
    }

    /// Creates or refreshes the user's profile after sign-in. Leaves householdId alone.
    static func ensureProfile(_ user: User) {
        let name = user.displayName ?? user.email?.components(separatedBy: "@").first ?? "Me"
        users.document(user.uid).setData(["displayName": name, "email": user.email ?? ""], merge: true)
    }

    static func createHousehold(uid: String, name: String) async throws {
        let code = try await unusedInviteCode()
        let household = households.document()
        let batch = db.batch()
        batch.setData(["name": name.trimmed, "inviteCode": code, "memberIds": [uid]], forDocument: household)
        batch.setData(["householdId": household.documentID], forDocument: inviteCodes.document(code))
        batch.setData(["householdId": household.documentID], forDocument: users.document(uid), merge: true)
        try await batch.commit()
    }

    /// Returns false if no household uses this code.
    static func joinHousehold(uid: String, code: String) async throws -> Bool {
        let normalized = code.trimmed.uppercased()
        guard let householdId = try await inviteCodes.document(normalized).getDocument().get("householdId") as? String
        else { return false }
        let batch = db.batch()
        // Security rules only let a non-member add themselves when they present the matching code.
        batch.updateData(["memberIds": FieldValue.arrayUnion([uid]), "joinCode": normalized], forDocument: households.document(householdId))
        batch.setData(["householdId": householdId], forDocument: users.document(uid), merge: true)
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

    /// Not allowed for the owner, who has to hand ownership over first.
    static func leave(_ householdId: String, uid: String) async throws {
        let batch = db.batch()
        batch.updateData(["memberIds": FieldValue.arrayRemove([uid])], forDocument: households.document(householdId))
        batch.updateData(["householdId": FieldValue.delete()], forDocument: users.document(uid))
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

    private static func unusedInviteCode() async throws -> String {
        // No 0/O or 1/I/L, so codes are easy to read aloud.
        let alphabet = Array("ABCDEFGHJKMNPQRSTUVWXYZ23456789")
        while true {
            let code = String((0..<6).map { _ in alphabet.randomElement()! })
            if try await !inviteCodes.document(code).getDocument().exists { return code }
        }
    }
}

extension String {
    var trimmed: String { trimmingCharacters(in: .whitespacesAndNewlines) }
}
