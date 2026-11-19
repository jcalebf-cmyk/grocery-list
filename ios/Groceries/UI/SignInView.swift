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
    @State private var resettingPassword = false
    @State private var resetEmail = ""
    /// After "Send link": where the link went, or why it couldn't be sent.
    @State private var resetSentTo: String?
    @State private var resetError: String?
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
                    } else {
                        Button("Forgot password?") { resetEmail = email.trimmed; resettingPassword = true }
                            .font(.callout)
                            .frame(maxWidth: .infinity, alignment: .trailing)
                            .disabled(busy)
                    }
                }

                if let error { Text(error).foregroundStyle(.red).font(.callout).multilineTextAlignment(.center) }

                Button(action: submit) {
                    Text(creatingAccount ? "Create account" : "Sign in").fontWeight(.semibold).frame(maxWidth: .infinity, minHeight: 34)
                }
                .prominentGlassButton()
                .disabled(!canSubmit)

                Button(creatingAccount ? "I already have an account" : "New here? Create an account") {
                    // Fields move when the form changes, so leave none focused rather than the one typed in last.
                    UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
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
        .alert("Reset your password", isPresented: $resettingPassword) {
            TextField("Email", text: $resetEmail)
                .textContentType(.emailAddress).keyboardType(.emailAddress)
                .textInputAutocapitalization(.never).autocorrectionDisabled()
            // Not .disabled: alert buttons keep the state they had when the alert opened.
            Button("Send link", action: sendPasswordReset)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("We'll email you a link to choose a new password.")
        }
        // Alert buttons always close their alert, so the result follows in an alert of its own.
        .alert("Check your email", isPresented: isSet($resetSentTo)) {
            Button("Done") {}
        } message: {
            Text("If there's an account for \(resetSentTo ?? ""), we've sent it a link to reset your password.")
        }
        .alert("Couldn't send the link", isPresented: isSet($resetError)) {
            Button("Try again") { resettingPassword = true }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text(resetError ?? "")
        }
    }

    private func isSet(_ value: Binding<String?>) -> Binding<Bool> {
        Binding(get: { value.wrappedValue != nil }, set: { if !$0 { value.wrappedValue = nil } })
    }

    private func sendPasswordReset() {
        let address = resetEmail.trimmed
        busy = true
        Task {
            defer { busy = false }
            // Give the email alert time to close; one alert can't open while another is closing.
            try? await Task.sleep(for: .milliseconds(400))
            guard !address.isEmpty else { resetError = "Type your email to get a reset link."; return }
            do {
                try await AuthService.sendPasswordReset(email: address)
            } catch let error as ServerError where error.isRateLimited {
                resetError = "That's a lot of reset emails for one day. Try again tomorrow."
                return
            } catch is URLError {
                resetError = "Couldn't connect. Check your internet and try again."
                return
            } catch {
                resetError = "Couldn't send the email. Try again in a few minutes."
                return
            }
            // Fill in the sign-in email too, ready for when they come back with a new password.
            email = address
            resetSentTo = address
        }
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
        case 17010: return "Too many tries. Wait a few minutes and try again."
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

/// Start a household or join one with an invite code: the first onboarding step. It asks which first,
/// then shows just that one field, so nobody starts a second household by mistake. The rest
/// (OnboardingView) follows on Home once the household is set.
/// Android: HouseholdSetupScreen.kt.
struct HouseholdSetupView: View {
    let user: UserProfile
    @Environment(SessionModel.self) private var session

    private enum Step { case choose, join, start }

    @State private var step = Step.choose
    @State private var householdName = ""
    @State private var code = ""
    @State private var busy = false
    @State private var error: String?
    @FocusState private var fieldFocused: Bool

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    switch step {
                    case .choose: choose
                    case .join: join
                    case .start: start
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 8)
                .padding(.bottom, 24)
                .frame(maxWidth: .infinity, alignment: .leading)
                .id(step) // each step slides in fresh
                // The choice screen always sits to the left of the step it leads to.
                .transition(.move(edge: step == .choose ? .leading : .trailing).combined(with: .opacity))
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .background(Color.warmBackground)
    }

    private func go(to next: Step) {
        error = nil
        if next == .start && householdName.isEmpty { householdName = suggestedHouseholdName(user.displayName) }
        withAnimation(.snappy) { step = next }
        if next != .choose {
            Task { try? await Task.sleep(for: .milliseconds(350)); fieldFocused = true }
        }
    }

    // MARK: Chrome

    private var header: some View {
        HStack {
            Button { go(to: .choose) } label: {
                Image(systemName: "chevron.left").font(.body.weight(.semibold)).frame(width: 36, height: 36)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .opacity(step == .choose ? 0 : 1)
            .disabled(step == .choose || busy)
            .accessibilityLabel("Back")
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private func intro(_ systemImage: String, _ title: String, _ text: String) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: systemImage)
                .font(.title2)
                .foregroundStyle(Color.accentColor)
                .frame(width: 52, height: 52)
                .background(Color.accentColor.opacity(0.15), in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .accessibilityHidden(true)
            Text(title).font(.brand(30))
            Text(text).foregroundStyle(.secondary)
        }
    }

    // MARK: Steps

    @ViewBuilder private var choose: some View {
        intro("person.2.fill", "Welcome, \(user.displayName)",
              "Groceries keeps one shared list for your whole household, updated live as anyone adds or checks off items.")
        Text("Is someone in your household already using Groceries?")
            .font(.headline)
            .padding(.top, 8)
        choice("person.2.fill", "Join my household", "Yes, someone sent me an invite code") { go(to: .join) }
        choice("house.fill", "Start a new household", "No, I'm the first one here") { go(to: .start) }
        Button("Sign out") { AuthService.signOut() }
            .frame(maxWidth: .infinity)
            .padding(.top, 8)
    }

    private func choice(_ systemImage: String, _ title: String, _ subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: systemImage)
                    .font(.title3)
                    .foregroundStyle(Color.accentColor)
                    .frame(width: 44, height: 44)
                    .background(Color.accentColor.opacity(0.15), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(.headline).foregroundStyle(.primary)
                    Text(subtitle).font(.subheadline).foregroundStyle(.secondary)
                }
                Spacer(minLength: 0)
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundStyle(.tertiary)
            }
            .padding(16)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .contentShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder private var join: some View {
        intro("person.2.fill", "Join your household",
              "Ask anyone already in your household for the invite code. They'll find it in Settings.")
        TextField("6-character invite code", text: $code)
            .font(.system(.title3, design: .monospaced))
            .textInputAutocapitalization(.characters).autocorrectionDisabled()
            .submitLabel(.go)
            .focused($fieldFocused)
            .onChange(of: code) { code = String(code.uppercased().filter { $0.isLetter || $0.isNumber }.prefix(6)) }
            .onSubmit { if canJoin { joinHousehold() } }
            .padding(14)
            .background(Color.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .padding(.top, 8)
        if let error { Text(error).foregroundStyle(.red) }
        Button(action: joinHousehold) {
            Text("Join household").font(.headline).frame(maxWidth: .infinity, minHeight: 36)
        }
        .prominentGlassButton().disabled(!canJoin)
    }

    @ViewBuilder private var start: some View {
        intro("house.fill", "Name your household",
              "Everyone you invite sees this name. You can change it in Settings later.")
        TextField("Household name, like The Smiths", text: $householdName)
            .textInputAutocapitalization(.words)
            .submitLabel(.done)
            .focused($fieldFocused)
            .onSubmit { if canCreate { createHousehold() } }
            .padding(14)
            .background(Color.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .padding(.top, 8)
        if let error { Text(error).foregroundStyle(.red) }
        Button(action: createHousehold) {
            Text("Create household").font(.headline).frame(maxWidth: .infinity, minHeight: 36)
        }
        .prominentGlassButton().disabled(!canCreate)
    }

    private var canJoin: Bool { !busy && code.count == 6 }
    private var canCreate: Bool { !busy && !householdName.trimmed.isEmpty }

    private func joinHousehold() {
        run(.joined) { if try await !HouseholdService.joinHousehold(uid: user.id, code: code) { error = "No household uses that code. Check it and try again." } }
    }

    private func createHousehold() {
        run(.created) { try await HouseholdService.createHousehold(uid: user.id, name: householdName) }
    }

    /// Marks onboarding first: once the household is set, the session moves straight on to Home, which
    /// shows the rest of the walkthrough.
    private func run(_ start: OnboardingStart, _ work: @escaping () async throws -> Void) {
        busy = true
        error = nil
        session.onboarding = start
        Task {
            do { try await work() } catch { self.error = error.localizedDescription }
            if error != nil { session.onboarding = nil }
            busy = false
        }
    }
}
