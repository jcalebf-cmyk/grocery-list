import FirebaseCore
import GoogleSignIn
import SwiftUI

@main
struct GroceriesApp: App {
    @State private var session = SessionModel()

    init() {
        // Without GoogleService-Info.plist the app still launches, showing a setup message instead.
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .environment(session)
                .tint(.accentColor)
                .onOpenURL { url in
                    // A recipe share link waits for Home (after sign-in, if needed); anything else is Google sign-in.
                    if let code = shareCode(from: url) {
                        LinkRouter.shared.shareCode = code
                    } else {
                        GIDSignIn.sharedInstance.handle(url)
                    }
                }
                .task { await session.start() }
        }
    }
}
