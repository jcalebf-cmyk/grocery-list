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
/// Kept in memory: if the app is closed partway, the walkthrough doesn't come back.
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
    private var membership: Task<Void, Never>?

    func start() async {
        guard FirebaseApp.app() != nil else { session = .notConfigured; return }
        guard authHandle == nil else { return }
        await SharedSignIn.use() // before reading the user, so the share extension sees the same sign-in
        guard authHandle == nil else { return }
        authHandle = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in self?.follow(user) }
        }
    }

    private func follow(_ user: User?) {
        watch?.cancel()
        membership?.cancel()
        guard let user else { session = .signedOut; onboarding = nil; return }
        HouseholdService.ensureProfile(user)
        Task { try? await HouseholdService.syncHouseholdIds(user.uid) }
        watch = Task {
            do {
                for try await (profile, saving) in HouseholdService.profileSaving(user.uid) {
                    guard !Task.isCancelled else { return }
                    // Each profile change starts over, since another household may have been opened.
                    membership?.cancel()
                    guard let profile else { continue }
                    // Signing in writes the name and email first, so until the server's copy is in, having no
                    // household only means it isn't known yet.
                    if profile.householdId == nil && saving { session = .loading; continue }
                    membership = Task {
                        if let householdId = profile.householdId {
                            await followMembership(profile, householdId, saving: saving)
                        } else {
                            await openAnother(profile)
                        }
                    }
                }
            } catch {
                session = .signedOut
            }
        }
    }

    /// Ready while still a member of the household they have open. Once removed (or after leaving), security
    /// rules deny reading it, so another of their households opens instead.
    /// `saving`: the profile's household link isn't on the server yet (they're joining right now).
    private func followMembership(_ profile: UserProfile, _ householdId: String, saving: Bool) async {
        do {
            // Waits for the server: Home's listeners start on ready, and any the server refuses stop for good.
            for try await household in HouseholdService.confirmedHousehold(householdId) {
                guard !Task.isCancelled else { return }
                if let household, household.memberIds.contains(profile.id) {
                    let ready = Session.ready(profile, householdId: householdId)
                    if session != ready { session = ready }
                } else {
                    await openAnother(profile)
                    return
                }
            }
        } catch let error as NSError where error.domain == FirestoreErrorDomain && error.code == FirestoreErrorCode.permissionDenied.rawValue {
            // Asked before the join was saved. The profile updates once it is, which tries again.
            if saving { return }
            await openAnother(profile)
        } catch {}
    }

    /// Opens another household the user is in, or household setup if there isn't one (clearing the stale link).
    private func openAnother(_ profile: UserProfile) async {
        // Tries until the server answers, so a bad connection doesn't look like having no household.
        var found: [Household]?
        while found == nil {
            found = try? await HouseholdService.householdsOf(profile.id)
            if found == nil { try? await Task.sleep(for: .seconds(3)) }
            guard !Task.isCancelled else { return }
        }
        if let next = found?.first(where: { $0.id != profile.householdId }) {
            // The profile updates, which starts this again on that household.
            HouseholdService.switchHousehold(uid: profile.id, to: next.id)
            return
        }
        if profile.householdId != nil { HouseholdService.clearHousehold(uid: profile.id) }
        var cleared = profile
        cleared.householdId = nil
        session = .needsHousehold(cleared)
    }
}
