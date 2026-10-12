import SwiftUI

struct RootView: View {
    @Environment(SessionModel.self) private var model

    var body: some View {
        switch model.session {
        case .loading:
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity).background(Color.warmBackground)
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
