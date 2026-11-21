import SwiftUI

/// The walkthrough after someone starts or joins a household: invite (new households only), then the
/// recipe box. Each step teaches one thing the app does.
/// Android: OnboardingScreen.kt.
struct OnboardingView: View {
    let data: HouseholdData
    let start: OnboardingStart
    let onDone: () -> Void

    private enum Step { case invite, recipes }

    @State private var index = 0

    private var steps: [Step] { start == .created ? [.invite, .recipes] : [.recipes] }
    private var step: Step { steps[index] }

    var body: some View {
        VStack(spacing: 0) {
            header
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    switch step {
                    case .invite: invite
                    case .recipes: recipes
                    }
                }
                .padding(.horizontal, 24)
                .padding(.top, 16)
                .frame(maxWidth: .infinity, alignment: .leading)
                .id(step) // each step slides in fresh
                .transition(.asymmetric(insertion: .move(edge: .trailing), removal: .move(edge: .leading)).combined(with: .opacity))
            }
            .scrollBounceBehavior(.basedOnSize)
            footer
        }
        .readableWidth()
        .background(Color.warmBackground)
    }

    // MARK: Chrome

    /// Back button and progress dots. The household step that came before counts as the first dot.
    private var header: some View {
        HStack {
            Button { withAnimation(.snappy) { index -= 1 } } label: {
                Image(systemName: "chevron.left").font(.body.weight(.semibold)).frame(width: 36, height: 36)
            }
            .buttonStyle(.glass)
            .buttonBorderShape(.circle)
            .opacity(index > 0 ? 1 : 0)
            .disabled(index == 0)
            .accessibilityLabel("Back")
            Spacer()
            HStack(spacing: 6) {
                ForEach(0...steps.count, id: \.self) { dot in
                    Capsule()
                        .fill(dot == index + 1 ? Color.accentColor : Color.secondary.opacity(0.3))
                        .frame(width: dot == index + 1 ? 18 : 7, height: 7)
                }
            }
            .animation(.snappy, value: index)
            .accessibilityElement()
            .accessibilityLabel("Step \(index + 2) of \(steps.count + 1)")
            Spacer()
            Color.clear.frame(width: 36, height: 36)
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
    }

    private var footer: some View {
        VStack(spacing: 4) {
            Button(action: next) {
                Text(primaryLabel).font(.headline).frame(maxWidth: .infinity, minHeight: 36)
            }
            .prominentGlassButton()
        }
        .padding(.horizontal, 24)
        .padding(.bottom, 8)
    }

    private var primaryLabel: String {
        step == steps.last ? "Done" : "Continue"
    }

    private func next() {
        if index + 1 < steps.count { withAnimation(.snappy) { index += 1 } } else { onDone() }
    }

    // MARK: Steps

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

    @ViewBuilder private var invite: some View {
        intro("person.2.fill", "Invite your household",
              "Everyone who joins sees and edits the same lists, live. Send them this code now, or find it in Settings later.")
        VStack(spacing: 14) {
            if let household = data.household {
                Text(household.inviteCode)
                    .font(.system(size: 40, weight: .semibold, design: .monospaced))
                    .tracking(4)
                    .textSelection(.enabled)
                ShareLink(item: "Join our grocery list household \"\(household.name)\" with code \(household.inviteCode)") {
                    Label("Share code", systemImage: "square.and.arrow.up").font(.headline)
                }
                .buttonStyle(.glass)
            } else {
                ProgressView().frame(height: 90)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 24)
        .background(Color.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .padding(.top, 8)
    }

    @ViewBuilder private var recipes: some View {
        intro("fork.knife", "Your household's recipes",
              "Save recipes in one place everyone can see. Paste one from a website, a message, or a YouTube video, share one to Groceries from another app, or ask for one, like \"chicken thighs in the dutch oven\". Then add what you're missing to a list in a tap.")
    }
}
