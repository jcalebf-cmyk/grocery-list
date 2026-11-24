import SwiftUI

struct RootView: View {
    @Environment(SessionModel.self) private var model

    var body: some View {
        switch model.session {
        case .loading:
            LoadingView()
        case .notConfigured:
            ContentUnavailableView("Almost ready", systemImage: "gearshape",
                                   description: Text("Add GoogleService-Info.plist from Firebase to finish setup."))
        case .signedOut:
            SignInView()
        case .needsHousehold(let user):
            HouseholdSetupView(user: user)
        case .ready(let user, let householdId):
            HomeView(user: user, householdId: householdId)
                .id(householdId) // fresh state if the household changes
        }
    }
}

/// After a while it offers a way out: without a connection, or with an account that's gone, it can't finish.
private struct LoadingView: View {
    @State private var slow = false

    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
            if slow {
                Text("Still connecting. Check your internet connection.")
                    .foregroundStyle(.secondary).multilineTextAlignment(.center)
                Button("Sign out") { AuthService.signOut() }
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color.warmBackground)
        .task {
            try? await Task.sleep(for: .seconds(8))
            withAnimation { slow = true }
        }
    }
}
