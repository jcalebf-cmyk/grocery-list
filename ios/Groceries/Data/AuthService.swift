import AuthenticationServices
import CryptoKit
import FirebaseAuth
import FirebaseCore
import GoogleSignIn
import UIKit

/// Email, Google, and Apple sign-in on top of Firebase Auth, plus the re-authentication that
/// account deletion requires.
@MainActor
enum AuthService {
    static var auth: Auth { Auth.auth() }

    static func signIn(email: String, password: String) async throws {
        try await auth.signIn(withEmail: email.trimmed, password: password)
    }

    static func createAccount(name: String, email: String, password: String) async throws {
        let result = try await auth.createUser(withEmail: email.trimmed, password: password)
        let change = result.user.createProfileChangeRequest()
        change.displayName = name.trimmed
        try await change.commitChanges()
        // The profile doc is first written before the name is set, so write it again with the name.
        HouseholdService.ensureProfile(result.user)
        // Confirming is optional, so a failed send mustn't fail sign-up; Settings offers to resend.
        try? await result.user.sendEmailVerification()
    }

    static func sendPasswordReset(email: String) async throws {
        try await auth.sendPasswordReset(withEmail: email.trimmed)
    }

    /// True if the account signs in with a password and hasn't confirmed its email yet. Refreshes from
    /// Firebase first, since the app's copy only updates on sign-in (not when the link is tapped).
    static func needsEmailConfirmation() async -> Bool {
        guard let user = auth.currentUser, provider == .password else { return false }
        try? await user.reload()
        return auth.currentUser?.isEmailVerified == false
    }

    static func resendEmailConfirmation() async throws {
        try await auth.currentUser?.sendEmailVerification()
    }

    // MARK: Google

    static func signInWithGoogle() async throws {
        try await auth.signIn(with: googleCredential())
    }

    private static func googleCredential() async throws -> AuthCredential {
        guard let clientID = FirebaseApp.app()?.options.clientID else { throw SignInError.googleNotConfigured }
        GIDSignIn.sharedInstance.configuration = GIDConfiguration(clientID: clientID)
        guard let presenter = UIApplication.shared.topViewController else { throw SignInError.noWindow }
        let result = try await GIDSignIn.sharedInstance.signIn(withPresenting: presenter)
        guard let idToken = result.user.idToken?.tokenString else { throw SignInError.googleNotConfigured }
        return GoogleAuthProvider.credential(withIDToken: idToken, accessToken: result.user.accessToken.tokenString)
    }

    // MARK: Apple

    /// A fresh random nonce for a Sign in with Apple request; Apple gets its SHA-256 hash, Firebase the raw value.
    static func makeNonce() -> String {
        let charset = Array("0123456789ABCDEFGHIJKLMNOPQRSTUVXYZabcdefghijklmnopqrstuvwxyz-._")
        var generator = SystemRandomNumberGenerator()
        return String((0..<32).map { _ in charset.randomElement(using: &generator)! })
    }

    static func sha256(_ text: String) -> String {
        SHA256.hash(data: Data(text.utf8)).map { String(format: "%02x", $0) }.joined()
    }

    /// Completes sign-in from the system Sign in with Apple button.
    static func signInWithApple(_ apple: ASAuthorizationAppleIDCredential, rawNonce: String) async throws {
        guard let tokenData = apple.identityToken, let idToken = String(data: tokenData, encoding: .utf8)
        else { throw SignInError.appleFailed }
        let credential = OAuthProvider.appleCredential(withIDToken: idToken, rawNonce: rawNonce, fullName: apple.fullName)
        let result = try await auth.signIn(with: credential)
        // Apple only shares the name on the very first sign-in, so save it while we have it.
        if let name = apple.fullName.flatMap({ PersonNameComponentsFormatter().string(from: $0) }), !name.isEmpty,
           result.user.displayName?.isEmpty ?? true {
            let change = result.user.createProfileChangeRequest()
            change.displayName = name
            try? await change.commitChanges()
            HouseholdService.ensureProfile(result.user)
        }
    }

    // MARK: Account deletion

    enum Provider { case password, google, apple }

    static var provider: Provider {
        let ids = auth.currentUser?.providerData.map(\.providerID) ?? []
        if ids.contains("apple.com") { return .apple }
        if ids.contains(EmailAuthProviderID) { return .password }
        return .google
    }

    /// Firebase only deletes accounts right after sign-in, so deletion confirms identity first.
    /// For Apple accounts this also revokes the app's Sign in with Apple authorization, as Apple requires.
    static func reauthenticate(password: String?) async throws {
        guard let user = auth.currentUser else { throw SignInError.notSignedIn }
        switch provider {
        case .password:
            let credential = EmailAuthProvider.credential(withEmail: user.email ?? "", password: password ?? "")
            try await user.reauthenticate(with: credential)
        case .google:
            try await user.reauthenticate(with: googleCredential())
        case .apple:
            let apple = try await AppleSignIn.request()
            let credential = OAuthProvider.appleCredential(withIDToken: apple.idToken, rawNonce: apple.nonce, fullName: nil)
            try await user.reauthenticate(with: credential)
            if let code = apple.authorizationCode { try await auth.revokeToken(withAuthorizationCode: code) }
        }
    }

    static func deleteUser() async throws { try await auth.currentUser?.delete() }

    static func signOut() {
        try? auth.signOut()
        GIDSignIn.sharedInstance.signOut()
    }
}

enum SignInError: LocalizedError {
    case googleNotConfigured, noWindow, notSignedIn, appleFailed

    var errorDescription: String? {
        switch self {
        case .googleNotConfigured: "Google sign-in isn't set up yet. Use email for now."
        case .noWindow, .appleFailed: "Something went wrong. Try again."
        case .notSignedIn: "You're not signed in."
        }
    }
}

/// Runs the system Sign in with Apple sheet with a hashed nonce, as Firebase requires.
@MainActor
private final class AppleSignIn: NSObject, ASAuthorizationControllerDelegate, ASAuthorizationControllerPresentationContextProviding {
    struct Result { let idToken: String; let nonce: String; let fullName: PersonNameComponents?; let authorizationCode: String? }

    private var continuation: CheckedContinuation<Result, Error>?
    private let nonce = AuthService.makeNonce()
    private static var current: AppleSignIn?

    static func request() async throws -> Result {
        let flow = AppleSignIn()
        current = flow // kept alive until the sheet finishes
        defer { current = nil }
        return try await withCheckedThrowingContinuation { continuation in
            flow.continuation = continuation
            let request = ASAuthorizationAppleIDProvider().createRequest()
            request.requestedScopes = [.fullName, .email]
            request.nonce = AuthService.sha256(flow.nonce)
            let controller = ASAuthorizationController(authorizationRequests: [request])
            controller.delegate = flow
            controller.presentationContextProvider = flow
            controller.performRequests()
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithAuthorization authorization: ASAuthorization) {
        MainActor.assumeIsolated {
            guard let credential = authorization.credential as? ASAuthorizationAppleIDCredential,
                  let tokenData = credential.identityToken, let token = String(data: tokenData, encoding: .utf8)
            else { continuation?.resume(throwing: SignInError.appleFailed); return }
            let code = credential.authorizationCode.flatMap { String(data: $0, encoding: .utf8) }
            continuation?.resume(returning: Result(idToken: token, nonce: nonce, fullName: credential.fullName, authorizationCode: code))
        }
    }

    nonisolated func authorizationController(controller: ASAuthorizationController, didCompleteWithError error: Error) {
        MainActor.assumeIsolated { continuation?.resume(throwing: error) }
    }

    nonisolated func presentationAnchor(for controller: ASAuthorizationController) -> ASPresentationAnchor {
        MainActor.assumeIsolated { UIApplication.shared.keyWindow ?? ASPresentationAnchor() }
    }
}

extension UIApplication {
    var keyWindow: UIWindow? {
        connectedScenes.compactMap { $0 as? UIWindowScene }.flatMap(\.windows).first(where: \.isKeyWindow)
    }

    var topViewController: UIViewController? {
        var top = keyWindow?.rootViewController
        while let presented = top?.presentedViewController { top = presented }
        return top
    }
}
