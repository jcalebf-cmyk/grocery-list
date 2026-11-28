import FirebaseFirestore

/// A household account deletion affects. `others` are the members who could take it over.
struct AffectedHousehold: Identifiable {
    let household: Household
    let isOwner: Bool
    let others: [UserProfile]

    var id: String { household.id }
    /// The user is its only member, so it goes with them.
    var solo: Bool { household.memberIds.count <= 1 }
    /// The user picks who takes over. (If no profile could be read, the next member does.)
    var needsNewOwner: Bool { !solo && isOwner && !others.isEmpty }
}

/// Deletes the signed-in user's data and login; the caller must have just re-authenticated. For each household
/// they're in: only member, the household and everything in it go too; shared, the user leaves it, handing
/// ownership to the chosen member first if they're the owner. Mirrors Android's AccountDeletion.
enum AccountDeletion {
    static func affectedHouseholds(uid: String) async throws -> [AffectedHousehold] {
        var result: [AffectedHousehold] = []
        for household in try await HouseholdService.householdsOf(uid) {
            let isOwner = household.ownerId == uid
            // Only an owner has anyone to choose.
            let others = isOwner ? await HouseholdService.members(household.memberIds.filter { $0 != uid }) : []
            result.append(AffectedHousehold(household: household, isOwner: isOwner, others: others))
        }
        return result
    }

    /// `newOwners` maps a household's ID to the member who takes it over.
    static func deleteAccount(uid: String, currentHouseholdId: String, newOwners: [String: String]) async throws {
        // Recipe links this account shared. Best effort: they'd expire within 30 days anyway.
        try? await ServerAPI.delete("/api/recipe-share")
        let households = try await HouseholdService.householdsOf(uid)
        // The open household goes last: once it's gone, the session looks for another to open.
        for household in households.filter({ $0.id != currentHouseholdId }) + households.filter({ $0.id == currentHouseholdId }) {
            if household.memberIds.count <= 1 {
                try await HouseholdService.deleteHousehold(household.id, uid: uid)
            } else {
                if household.ownerId == uid, let newOwner = newOwners[household.id] ?? household.memberIds.first(where: { $0 != uid }) {
                    try await HouseholdService.transferOwnership(household, to: newOwner)
                }
                try await HouseholdService.removeMember(household.id, member: uid)
            }
        }
        try await Firestore.firestore().collection("users").document(uid).delete()
        try await AuthService.deleteUser()
        await NearbyStores.shared.clear()
    }
}
