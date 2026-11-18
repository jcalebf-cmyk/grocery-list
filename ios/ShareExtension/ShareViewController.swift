import FirebaseAuth
import FirebaseCore
import SwiftUI
import UIKit
import UniformTypeIdentifiers

/// "Share to Groceries": takes the text or link shared from another app (a recipe from Claude or Gemini, a
/// YouTube video), has the server read it, and saves it to the household's recipe box. A share extension
/// can't open the app to check the recipe first, so it's saved as read; it can be edited in Groceries after.
/// Android opens its paste screen instead (MainActivity.receiveShare).
final class ShareViewController: UIViewController {
    private let model = ShareModel()

    override func viewDidLoad() {
        super.viewDidLoad()
        let host = UIHostingController(rootView: ShareView(model: model, onDone: { [weak self] in
            self?.extensionContext?.completeRequest(returningItems: nil)
        }))
        addChild(host)
        host.view.frame = view.bounds
        host.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(host.view)
        host.didMove(toParent: self)

        let items = (extensionContext?.inputItems as? [NSExtensionItem]) ?? []
        Task { await model.run(items) }
    }
}

@MainActor
@Observable
final class ShareModel {
    enum State {
        case reading
        case saved(title: String)
        case failed(String)
    }

    private(set) var state = State.reading

    private struct Request: Encodable { let text: String; let save: Bool }
    private struct Response: Decodable {
        struct Recipe: Decodable { let title: String }
        let recipe: Recipe
    }

    func run(_ items: [NSExtensionItem]) async {
        if FirebaseApp.app() == nil {
            guard Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil else {
                state = .failed("Groceries isn't set up on this phone yet.")
                return
            }
            FirebaseApp.configure()
        }
        await SharedSignIn.use()
        guard Auth.auth().currentUser != nil else {
            state = .failed("Open Groceries and sign in first, then share the recipe again.")
            return
        }
        guard let text = await Self.sharedText(items), !text.isEmpty else {
            state = .failed("There's no text or link to read in what was shared.")
            return
        }
        do {
            let response: Response = try await ServerAPI.post("/api/recipe-import", body: Request(text: text, save: true), timeout: 60)
            state = .saved(title: response.recipe.title)
        } catch let error as ServerError {
            let message = switch error.status {
            case 422: "Couldn't find a recipe in that. Share the recipe's text, or a link to a cooking video."
            case 429: "That's all the recipe saving for today. Check back tomorrow."
            case 409: "Open Groceries and join or start a household first."
            default: "Couldn't save that recipe. Try again in a bit."
            }
            state = .failed(message)
        } catch {
            state = .failed("Couldn't save that recipe. Check your connection and try again.")
        }
    }

    /// The shared text, plus the link if one came with it (Claude and Gemini share text; YouTube shares a link).
    private static func sharedText(_ items: [NSExtensionItem]) async -> String? {
        var parts: [String] = []
        for provider in items.flatMap({ $0.attachments ?? [] }) {
            if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier),
               let url = try? await provider.loadItem(forTypeIdentifier: UTType.url.identifier) as? URL {
                parts.append(url.absoluteString)
            } else if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier),
                      let text = try? await provider.loadItem(forTypeIdentifier: UTType.plainText.identifier) as? String {
                parts.append(text)
            }
        }
        if parts.isEmpty, let text = items.first?.attributedContentText?.string { parts.append(text) }
        var seen = Set<String>()
        return parts.map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && seen.insert($0).inserted }
            .joined(separator: "\n")
    }
}

struct ShareView: View {
    let model: ShareModel
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                switch model.state {
                case .reading:
                    ProgressView().controlSize(.large)
                    Text("Reading the recipe…").font(.headline)
                    Text("It's saved to your household's recipes when it's done.")
                        .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                case .saved(let title):
                    Image(systemName: "checkmark.circle.fill").font(.system(size: 48)).foregroundStyle(Color.accentColor)
                    Text("Saved to Groceries").font(.headline)
                    Text(title).font(.title3.weight(.semibold)).multilineTextAlignment(.center)
                    Text("Find it under Recipes. You can check and edit it there.")
                        .font(.subheadline).foregroundStyle(.secondary).multilineTextAlignment(.center)
                case .failed(let message):
                    Image(systemName: "exclamationmark.circle").font(.system(size: 48)).foregroundStyle(.secondary)
                    Text(message).multilineTextAlignment(.center)
                }
            }
            .padding(32)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color("WarmBackground"))
            .navigationTitle("Save recipe")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    // Closing while it reads may still save the recipe: the server finishes what it started.
                    Button("Done", action: onDone)
                }
            }
        }
    }
}
