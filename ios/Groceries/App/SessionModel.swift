import FirebaseAuth
import FirebaseCore
import FirebaseFirestore
import Observation

enum Session: Equatable {
    case loading
    case notConfigured
    case signedOut
    case needsHousehold(UserProfile)
    case ready(UserProfile, householdId: String)
}

/// Set when someone starts or joins a household, so Home walks them through the app first.
/// Kept in memory: if the app is closed partway, a new household still gets the cuisine picker.
enum OnboardingStart { case created, joined }

/// Follows Firebase Auth, the user's profile, and their household membership, the same way the
/// Android app's SessionViewModel does.
@MainActor
@Observable
final class SessionModel {
    private(set) var session: Session = .loading
    var onboarding: OnboardingStart?
    private var authHandle: AuthStateDidChangeListenerHandle?
    private var watch: Task<Void, Never>?

    func start() {
        guard FirebaseApp.app() != nil else { session = .notConfigured; return }
        guard authHandle == nil else { return }
        authHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in self?.follow(user) }
        }
    }

    private func follow(_ user: User?) {
        watch?.cancel()
        guard let user else { session = .signedOut; onboarding = nil; return }
        HouseholdService.ensureProfile(user)
        watch = Task {
            do {
                for try await (profile, saving) in HouseholdService.profileSaving(user.uid) {
                    guard let profile else { continue }
                    guard let householdId = profile.householdId else { session = .needsHousehold(profile); continue }
                    await followMembership(profile, householdId, saving: saving)
                }
            } catch {
                session = .signedOut
            }
        }
    }

    /// Ready while still a member. Once removed (or after leaving), security rules deny reading the
    /// household, so the user is sent to setup and the stale link is cleared.
    /// `saving`: the profile's household link isn't on the server yet (they're joining right now).
    private func followMembership(_ profile: UserProfile, _ householdId: String, saving: Bool) async {
        do {
            // Waits for the server: Home's listeners start on ready, and any the server refuses stop for good.
            for try await household in HouseholdService.confirmedHousehold(householdId) {
                if let household, household.memberIds.contains(profile.id) {
                    let ready = Session.ready(profile, householdId: householdId)
                    if session != ready { session = ready }
                } else {
                    leaveToSetup(profile)
                    return
                }
            }
        } catch let error as NSError where error.domain == FirestoreErrorDomain && error.code == FirestoreErrorCode.permissionDenied.rawValue {
            // Asked before the join was saved. The profile updates once it is, which tries again.
            if saving { return }
            leaveToSetup(profile)
        } catch {}
    }

    private func leaveToSetup(_ profile: UserProfile) {
        HouseholdService.clearHousehold(uid: profile.id)
        var cleared = profile
        cleared.householdId = nil
        session = .needsHousehold(cleared)
    }
}
