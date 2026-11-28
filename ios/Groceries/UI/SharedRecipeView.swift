import SwiftUI
import UIKit

/// A recipe share link someone opened (https://www.householdgroceries.app/r/<code>), waiting for Home to show it.
/// Kept until someone signed in to a household can see it. Android: SharedRecipeLink.
@MainActor
@Observable
final class LinkRouter {
    static let shared = LinkRouter()
    var shareCode: String?
}

/// Why a shared recipe couldn't be opened.
enum SharedRecipeProblem: Error {
    case expired, notFound, offline
}

/// A recipe someone shared by link, to look at and save a copy of in the open household. Android: SharedRecipeScreen.
struct SharedRecipeView: View {
    let data: HouseholdData
    let code: String
    let onSaved: (String) -> Void
    let onOpenList: (String) -> Void
    /// Swapped out for sample data when checking the screen.
    var fetch: (String) async throws(SharedRecipeProblem) -> Recipe = { code throws(SharedRecipeProblem) in
        try await RecipeService.fetchShared(code: code)
    }

    private enum Load { case loading, loaded(Recipe), failed(SharedRecipeProblem) }
    @State private var load = Load.loading
    @State private var attempt = 0
    @State private var saved = false

    var body: some View {
        Group {
            switch load {
            case .loaded(let recipe):
                RecipeDetail(data: data, recipe: recipe, onOpenList: onOpenList)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) {
                            Button(saveTitle) {
                                saved = true
                                onSaved(data.recipeService.add(recipe, uid: data.user.id))
                            }
                            .buttonStyle(.glass)
                            .lineLimit(1)
                        }
                    }
                    .sensoryFeedback(.success, trigger: saved) { _, now in now }
            case .loading:
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            case .failed(let problem):
                failure(problem)
            }
        }
        .background(Color.warmBackground)
        .navigationBarTitleDisplayMode(.inline)
        .task(id: attempt) {
            load = .loading
            do {
                load = .loaded(try await fetch(code))
            } catch {
                load = .failed((error as? SharedRecipeProblem) ?? .offline)
            }
        }
    }

    /// "Save", or which household it goes to when they're in several.
    private var saveTitle: String {
        if data.households.count > 1, let name = data.household?.name, !name.isEmpty { return "Save to \(name)" }
        return "Save"
    }

    private func failure(_ problem: SharedRecipeProblem) -> some View {
        let (title, text): (String, String) = switch problem {
        case .expired: ("This link has expired", "Recipe links last 30 days. Ask whoever sent it to share it again.")
        case .notFound: ("Couldn't find that recipe", "Check the link, or ask whoever sent it to share it again.")
        case .offline: ("Couldn't open that recipe", "Check your internet connection and try again.")
        }
        return ContentUnavailableView {
            Label(title, systemImage: problem == .offline ? "wifi.exclamationmark" : "link")
        } description: {
            Text(text)
        } actions: {
            if problem == .offline {
                Button("Try again") { attempt += 1 }.prominentGlassButton()
            }
        }
    }
}

/// Opens the share sheet with `text`; `subject` becomes the email subject. For a link made on tap, which
/// SwiftUI's ShareLink can't wait for.
@MainActor
func presentShareSheet(text: String, subject: String) {
    let scene = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        .first { $0.activationState == .foregroundActive }
    guard var top = scene?.keyWindow?.rootViewController else { return }
    while let presented = top.presentedViewController { top = presented }
    let sheet = UIActivityViewController(activityItems: [ShareItem(text: text, subject: subject)], applicationActivities: nil)
    // iPad: anchored to the top right, where the Share button is.
    sheet.popoverPresentationController?.sourceView = top.view
    sheet.popoverPresentationController?.sourceRect = CGRect(x: top.view.bounds.maxX - 60, y: top.view.safeAreaInsets.top, width: 1, height: 1)
    top.present(sheet, animated: true)
}

private final class ShareItem: NSObject, UIActivityItemSource {
    let text: String
    let subject: String
    init(text: String, subject: String) { self.text = text; self.subject = subject }

    func activityViewControllerPlaceholderItem(_: UIActivityViewController) -> Any { text }
    func activityViewController(_: UIActivityViewController, itemForActivityType _: UIActivity.ActivityType?) -> Any? { text }
    func activityViewController(_: UIActivityViewController, subjectForActivityType _: UIActivity.ActivityType?) -> String { subject }
}
