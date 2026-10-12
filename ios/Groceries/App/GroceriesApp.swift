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
                .onOpenURL { GIDSignIn.sharedInstance.handle($0) }
                .task { session.start() }
        }
    }
}
