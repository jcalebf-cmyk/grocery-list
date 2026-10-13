import SwiftUI

/// Account and household settings: same capabilities and rules as the Android Settings screen.
struct SettingsView: View {
    let data: HouseholdData
    @State private var editingName = false
    @State private var renaming = false
    @State private var draft = ""
    @State private var promoting: UserProfile?
    @State private var removing: UserProfile?
    @State private var leaving = false
    @State private var deleting = false
    @State private var error: String?
    @State private var editingCuisines = false
    @State private var emailUnconfirmed = false
    @State private var confirmationSent = false
    @Environment(\.scenePhase) private var scenePhase

    private var me: UserProfile { data.members.first { $0.id == data.user.id } ?? data.user }
    private var shared: Bool { data.members.count > 1 }
    private var others: [UserProfile] { data.members.filter { $0.id != data.user.id } }

    /// e.g. "0.5 (4)": the version testers see, and the TestFlight build number.
    static let appVersion: String = {
        let info = Bundle.main.infoDictionary
        return "\(info?["CFBundleShortVersionString"] as? String ?? "?") (\(info?["CFBundleVersion"] as? String ?? "?"))"
    }()

    var body: some View {
        Form {
            Section("Account") {
                Button { draft = me.displayName; editingName = true } label: {
                    HStack(spacing: 12) {
                        MemberBadge(name: me.displayName, size: 34)
                        VStack(alignment: .leading) {
                            Text(me.displayName).foregroundStyle(.primary)
                            Text(me.email).font(.footnote).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Image(systemName: "pencil").foregroundStyle(.secondary)
                    }
                }
                if emailUnconfirmed {
                    HStack(spacing: 12) {
                        Image(systemName: "envelope.badge").foregroundStyle(.tint).frame(width: 34)
                        VStack(alignment: .leading) {
                            Text("Confirm your email")
                            Text("Tap the link we sent to \(me.email) so you can reset your password if you forget it.")
                                .font(.footnote).foregroundStyle(.secondary)
                        }
                        Spacer()
                        Button(confirmationSent ? "Sent" : "Resend") {
                            Task {
                                do {
                                    try await AuthService.sendEmailConfirmation()
                                    confirmationSent = true
                                } catch {
                                    self.error = "Couldn't send the email. Try again in a few minutes."
                                }
                            }
                        }
                        .buttonStyle(.borderless)
                        .disabled(confirmationSent)
                    }
                }
                Button("Sign out", systemImage: "rectangle.portrait.and.arrow.right") { AuthService.signOut() }
                Button("Delete account", systemImage: "trash", role: .destructive) { deleting = true }
            }

            Section("Household") {
                Button { draft = data.household?.name ?? ""; renaming = true } label: {
                    LabeledContent("Name") { Text(data.household?.name ?? "") }
                }
                if let household = data.household {
                    VStack(spacing: 8) {
                        Text(shared ? "Invite code" : "Share your lists").font(.subheadline.weight(.semibold))
                        Text(household.inviteCode).font(.system(.largeTitle, design: .monospaced).weight(.semibold))
                        Text(shared ? "Anyone with this code can join and edit your lists."
                                    : "Send this code to someone you shop with. Once they join, you'll both see and edit the same lists.")
                            .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                        ShareLink(item: "Join our grocery list household \"\(household.name)\" with code \(household.inviteCode)") {
                            Label("Share", systemImage: "square.and.arrow.up")
                        }
                        .buttonStyle(.glass)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
            }

            // Members and leaving only matter once someone else has joined.
            if shared {
                Section("Members") {
                    ForEach(data.members) { member in
                        HStack(spacing: 12) {
                            MemberBadge(name: member.displayName, size: 30)
                            VStack(alignment: .leading) {
                                Text(member.id == data.user.id ? "\(member.displayName) (you)" : member.displayName)
                                Text(member.id == data.household?.ownerId ? "Owner · \(member.email)" : member.email)
                                    .font(.footnote).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if data.isOwner && member.id != data.user.id {
                                Menu {
                                    Button("Make owner", systemImage: "crown") { promoting = member }
                                    Button("Remove from household", systemImage: "person.badge.minus", role: .destructive) { removing = member }
                                } label: { Image(systemName: "ellipsis.circle") }
                            }
                        }
                    }
                }
                Section {
                    Button("Leave household", role: .destructive) { leaving = true }
                }
            }

            Section {
                Button { editingCuisines = true } label: {
                    LabeledContent("Favorite cuisines") {
                        Text(data.cuisinePrefs.map { $0.cuisines.isEmpty ? "Any" : $0.cuisines.joined(separator: ", ") } ?? "")
                    }
                }
            } header: {
                Text("Recipes")
            } footer: {
                Text("Version \(Self.appVersion)").frame(maxWidth: .infinity).padding(.top, 24)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.warmBackground)
        .navigationTitle("Settings")
        // Checked on every return to the app, since the link is tapped outside it.
        .task(id: scenePhase) {
            if scenePhase == .active { emailUnconfirmed = await AuthService.needsEmailConfirmation() }
        }
        // Recipes pick up the change when Home is next shown.
        .sheet(isPresented: $editingCuisines) {
            CuisineSheet(
                title: "Favorite cuisines",
                subtitle: "Recipe ideas lean toward these, with a few others mixed in.",
                initial: data.cuisinePrefs?.cuisines ?? [],
                confirm: "Save",
                onConfirm: { picked in editingCuisines = false; data.recipeService.savePreferences(picked) }
            )
        }
        .alert("Your name", isPresented: $editingName) {
            TextField("Name", text: $draft)
            Button("Save") { run { try await HouseholdService.updateDisplayName(uid: data.user.id, name: draft); await data.refreshMembers() } }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Household name", isPresented: $renaming) {
            TextField("Name", text: $draft)
            Button("Rename") { run { try await HouseholdService.rename(data.householdId, to: draft) } }
            Button("Cancel", role: .cancel) {}
        }
        .alert("Make \(promoting?.displayName ?? "") the owner?", isPresented: binding($promoting)) {
            Button("Make owner") {
                if let member = promoting, let household = data.household {
                    run { try await HouseholdService.transferOwnership(household, to: member.id) }
                }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("They'll be able to remove members and hand ownership on. You'll stay in the household as a regular member.")
        }
        .alert("Remove \(removing?.displayName ?? "")?", isPresented: binding($removing)) {
            Button("Remove", role: .destructive) {
                if let member = removing { run { try await HouseholdService.removeMember(data.householdId, member: member.id) } }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("They'll lose access to your lists. They can rejoin with the invite code.")
        }
        .sheet(isPresented: $leaving) {
            LeaveSheet(data: data, others: others) { newOwner in
                leaving = false
                run {
                    if let newOwner, let household = data.household { try await HouseholdService.transferOwnership(household, to: newOwner.id) }
                    try await HouseholdService.leave(data.householdId, uid: data.user.id)
                }
            }
        }
        .sheet(isPresented: $deleting) {
            DeleteAccountSheet(data: data, others: others)
        }
        .alert("Something went wrong", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
            Button("OK") {}
        } message: { Text(error ?? "") }
    }

    private func binding(_ value: Binding<UserProfile?>) -> Binding<Bool> {
        Binding(get: { value.wrappedValue != nil }, set: { if !$0 { value.wrappedValue = nil } })
    }

    private func run(_ work: @escaping () async throws -> Void) {
        Task { do { try await work() } catch { self.error = error.localizedDescription } }
    }
}

/// Owners pick who takes over before leaving; members just confirm.
private struct LeaveSheet: View {
    let data: HouseholdData
    let others: [UserProfile]
    let onLeave: (UserProfile?) -> Void
    @State private var newOwner: UserProfile?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Text(data.isOwner ? "You're the owner. Choose who takes over before you go."
                                  : "You'll lose access to its lists. You can rejoin with the invite code.")
                if data.isOwner {
                    Picker("New owner", selection: $newOwner) {
                        ForEach(others) { Text($0.displayName).tag(Optional($0)) }
                    }
                    .pickerStyle(.inline)
                }
            }
            .navigationTitle("Leave \(data.household?.name ?? "household")?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(data.isOwner ? "Make owner & leave" : "Leave", role: .destructive) { onLeave(newOwner) }
                        .disabled(data.isOwner && newOwner == nil)
                }
            }
            .onAppear { if data.isOwner && others.count == 1 { newOwner = others.first } }
        }
        .presentationDetents([.medium])
    }
}

/// Confirms identity (password, Google, or Apple) as the last step, since Firebase requires a fresh
/// sign-in to delete. For Apple accounts this also revokes Sign in with Apple, as Apple requires.
private struct DeleteAccountSheet: View {
    let data: HouseholdData
    let others: [UserProfile]
    @State private var password = ""
    @State private var newOwner: UserProfile?
    @State private var working = false
    @State private var error: String?
    @State private var editingCuisines = false
    @Environment(\.dismiss) private var dismiss

    private var shared: Bool { !others.isEmpty }
    private var needsNewOwner: Bool { shared && data.isOwner }
    private var provider: AuthService.Provider { AuthService.provider }

    var body: some View {
        NavigationStack {
            Form {
                Text(shared
                     ? "This permanently deletes your account and removes you from \(data.household?.name ?? "your household"). Its lists stay with the other members."
                     : "This permanently deletes your account and \(data.household?.name ?? "your household"), including all of its lists, stores, kitchen items, and recipes. This can't be undone.")
                if needsNewOwner {
                    Picker("Who takes over?", selection: $newOwner) {
                        ForEach(others) { Text($0.displayName).tag(Optional($0)) }
                    }
                    .pickerStyle(.inline)
                }
                Section {
                    switch provider {
                    case .password: SecureField("Your password", text: $password)
                    case .google: Text("You'll confirm with Google before anything is deleted.").font(.footnote)
                    case .apple: Text("You'll confirm with Apple before anything is deleted.").font(.footnote)
                    }
                    if let error { Text(error).foregroundStyle(.red).font(.footnote) }
                }
            }
            .navigationTitle("Delete your account?")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.disabled(working) }
                ToolbarItem(placement: .confirmationAction) {
                    Button(working ? "Deleting…" : "Delete", role: .destructive, action: delete)
                        .disabled(working || (needsNewOwner && newOwner == nil) || (provider == .password && password.isEmpty))
                }
            }
            .onAppear { if needsNewOwner && others.count == 1 { newOwner = others.first } }
        }
        .interactiveDismissDisabled(working)
    }

    private func delete() {
        working = true
        error = nil
        Task {
            do {
                try await AuthService.reauthenticate(password: password)
            } catch {
                self.error = (error as NSError).code == 17009 ? "That password isn't right." : "Couldn't confirm it's you. Try again."
                working = false
                return
            }
            do {
                // Deleting changes the session, which closes these screens; that's expected.
                try await AccountDeletion.deleteAccount(uid: data.user.id, householdId: data.householdId, newOwner: newOwner?.id)
            } catch {
                self.error = "Couldn't finish deleting your account. Try again."
                working = false
            }
        }
    }
}
