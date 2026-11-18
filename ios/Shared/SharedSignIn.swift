import FirebaseAuth

/// Keeps the Firebase sign-in in a keychain group the app and its share extension both read, so recipes can
/// be shared to Groceries from other apps. Both targets list the group in their entitlements.
enum SharedSignIn {
    static let accessGroup = "6759US4CJM.com.householdgroceries.shared"

    /// Call once, after `FirebaseApp.configure()` and before reading the signed-in user. Someone already
    /// signed in from before sharing existed is moved into the group rather than signed out.
    static func use() async {
        let previous = Auth.auth().currentUser
        do {
            try Auth.auth().useUserAccessGroup(accessGroup)
        } catch {
            // Locally signed simulator builds can't use the group; the app works, sharing just can't see the sign-in.
            print("Shared sign-in unavailable: \(error)")
            return
        }
        if let previous, Auth.auth().currentUser == nil {
            try? await Auth.auth().updateCurrentUser(previous)
        }
    }
}
