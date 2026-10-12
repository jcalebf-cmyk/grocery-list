import AuthenticationServices
import SwiftUI

/// Email, Google, and Apple sign-in, and account creation with password confirmation.
/// Validation matches the Android app: 6-character minimum, and a mismatch shows once it can't become a match.
struct SignInView: View {
    @State private var creatingAccount = false
    @State private var name = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var submittedMismatch = false
    @State private var showPassword = false
    @State private var busy = false
    @State private var error: String?
    @State private var appleNonce = ""

    private let minLength = 6
    private var passwordTooShort: Bool { creatingAccount && !password.isEmpty && password.count < minLength }
    private var showMismatch: Bool {
        creatingAccount && !confirmPassword.isEmpty && password != confirmPassword &&
            (submittedMismatch || confirmPassword.count >= password.count || !password.hasPrefix(confirmPassword))
    }
    private var canSubmit: Bool {
        !busy && !email.trimmed.isEmpty && !password.isEmpty &&
            (!creatingAccount || (!name.trimmed.isEmpty && password.count >= minLength && password == confirmPassword))
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                HStack(spacing: 14) {
                    Image("AppLogo").resizable().frame(width: 56, height: 56)
                        .clipShape(RoundedRectangle(cornerRadius: 13, style: .continuous))
                    Text("Groceries").font(.brand(40))
                }
                .padding(.top, 48)
                Text(creatingAccount ? "Create your account" : "Sign in to your household's lists")
                    .foregroundStyle(.secondary)
                    .padding(.bottom, 12)

                SignInWithAppleButton(creatingAccount ? .signUp : .signIn) { request in
                    appleNonce = AuthService.makeNonce()
                    request.requestedScopes = [.fullName, .email]
                    request.nonce = AuthService.sha256(appleNonce)
                } onCompletion: { result in
                    guard case .success(let authorization) = result,
                          let credential = authorization.credential as? ASAuthorizationAppleIDCredential else { return }
                    run { try await AuthService.signInWithApple(credential, rawNonce: appleNonce) }
                }
                .frame(height: 50)
                .clipShape(Capsule())

                Button { run { try await AuthService.signInWithGoogle() } } label: {
                    HStack(spacing: 10) {
                        Image("GoogleG").resizable().frame(width: 20, height: 20)
                        Text("Continue with Google").fontWeight(.medium)
                    }
                    .frame(maxWidth: .infinity, minHeight: 34)
                }
                .buttonStyle(.glass)
                .disabled(busy)

                Divider().padding(.vertical, 8)

                VStack(spacing: 12) {
                    if creatingAccount {
                        field { TextField("Your name", text: $name).textContentType(.name).textInputAutocapitalization(.words) }
                    }
                    field {
                        TextField("Email", text: $email)
                            .textContentType(.emailAddress).keyboardType(.emailAddress)
                            .textInputAutocapitalization(.never).autocorrectionDisabled()
                    }
                    VStack(alignment: .leading, spacing: 4) {
                        field(isError: passwordTooShort) {
                            HStack {
                                secureField("Password", text: $password)
                                    .textContentType(creatingAccount ? .newPassword : .password)
                                    .onChange(of: password) { submittedMismatch = false }
                                Button { showPassword.toggle() } label: {
                                    Image(systemName: showPassword ? "eye" : "eye.slash").foregroundStyle(.secondary)
                                }
                                .accessibilityLabel(showPassword ? "Hide password" : "Show password")
                            }
                        }
                        if creatingAccount {
                            hint(passwordTooShort ? "Too short. Use at least \(minLength) characters." : "At least \(minLength) characters",
                                 isError: passwordTooShort)
                        }
                    }
                    if creatingAccount {
                        VStack(alignment: .leading, spacing: 4) {
                            field(isError: showMismatch) {
                                secureField("Confirm password", text: $confirmPassword)
                                    .textContentType(.newPassword)
                                    .onChange(of: confirmPassword) { submittedMismatch = false }
                            }
                            if showMismatch {
                                hint("Passwords don't match. Type the same password in both fields.", isError: true)
                            }
                        }
                    }
                }

                if let error { Text(error).foregroundStyle(.red).font(.callout).multilineTextAlignment(.center) }

                Button(action: submit) {
                    Text(creatingAccount ? "Create account" : "Sign in").fontWeight(.semibold).frame(maxWidth: .infinity, minHeight: 34)
                }
                .buttonStyle(.glassProminent)
                .disabled(!canSubmit)

                Button(creatingAccount ? "I already have an account" : "New here? Create an account") {
                    creatingAccount.toggle()
                    confirmPassword = ""
                    submittedMismatch = false
                    error = nil
                }
                .disabled(busy)
            }
            .padding(.horizontal, 24)
        }
        .scrollDismissesKeyboard(.interactively)
        .background(Color.warmBackground)
    }

    private func submit() {
        if creatingAccount && password != confirmPassword { submittedMismatch = true; return }
        guard canSubmit else { return }
        run {
            if creatingAccount {
                try await AuthService.createAccount(name: name, email: email, password: password)
            } else {
                try await AuthService.signIn(email: email, password: password)
            }
        }
    }

    /// On success the session switches screens; this view (and what was typed) goes away with it.
    private func run(_ work: @escaping () async throws -> Void) {
        busy = true
        error = nil
        Task {
            do {
                try await work()
            } catch {
                let message = friendlyMessage(error)
                self.error = message.isEmpty ? nil : message
            }
            busy = false
        }
    }

    private func friendlyMessage(_ error: Error) -> String {
        if let e = error as? SignInError { return e.localizedDescription }
        let code = (error as NSError).code
        switch code {
        case 17007: return "There's already an account with that email. Try signing in."
        case 17026: return "Use a password with at least 6 characters."
        case 17004, 17009, 17011: return "That email and password don't match an account."
        case 1001, -5: return "" // user cancelled Apple or Google
        default: return error.localizedDescription
        }
    }

    @ViewBuilder private func secureField(_ title: String, text: Binding<String>) -> some View {
        if showPassword { TextField(title, text: text).textInputAutocapitalization(.never).autocorrectionDisabled() }
        else { SecureField(title, text: text) }
    }

    private func field<Content: View>(isError: Bool = false, @ViewBuilder _ content: () -> Content) -> some View {
        content()
            .padding(14)
            .background(Color.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(isError ? .red : .clear, lineWidth: 1.5))
    }

    private func hint(_ text: String, isError: Bool) -> some View {
        Text(text).font(.footnote).foregroundStyle(isError ? .red : .secondary).padding(.leading, 6)
    }
}

/// Start a household or join one with an invite code.
struct HouseholdSetupView: View {
    let user: UserProfile
    @State private var householdName = ""
    @State private var code = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("Welcome, \(user.displayName)").font(.brand(30))
                Text("Lists are shared by everyone in a household. Start one, or join the one your family already uses.")
                    .foregroundStyle(.secondary)

                card(title: "Start a household") {
                    TextField("Household name, e.g. The Smiths", text: $householdName).textInputAutocapitalization(.words)
                        .padding(12).background(Color.warmBackground, in: RoundedRectangle(cornerRadius: 12))
                    Button("Create household") { run { try await HouseholdService.createHousehold(uid: user.id, name: householdName) } }
                        .buttonStyle(.glassProminent).disabled(busy || householdName.trimmed.isEmpty)
                }
                card(title: "Join a household") {
                    TextField("6-character invite code", text: $code)
                        .textInputAutocapitalization(.characters).autocorrectionDisabled()
                        .onChange(of: code) { code = String(code.uppercased().prefix(6)) }
                        .padding(12).background(Color.warmBackground, in: RoundedRectangle(cornerRadius: 12))
                    Button("Join") {
                        run { if try await !HouseholdService.joinHousehold(uid: user.id, code: code) { error = "No household uses that code." } }
                    }
                    .buttonStyle(.glassProminent).disabled(busy || code.count != 6)
                }
                if let error { Text(error).foregroundStyle(.red) }
                Button("Sign out") { AuthService.signOut() }.frame(maxWidth: .infinity)
            }
            .padding(24)
        }
        .background(Color.warmBackground)
    }

    private func card<Content: View>(title: String, @ViewBuilder _ content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.headline)
            content()
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
    }

    private func run(_ work: @escaping () async throws -> Void) {
        busy = true
        error = nil
        Task {
            do { try await work() } catch { self.error = error.localizedDescription }
            busy = false
        }
    }
}
