import FirebaseAuth
import SwiftUI

/// The "Ask for a recipe" conversation, kept on this phone (one per account and household) until someone taps Start
/// over, so leaving the chat and coming back, or closing the app, picks it up where it was. It isn't shared with the
/// household: only recipes someone saves are. A reply still lands if they leave while it's on its way.
/// Android: RecipeChatSession in RecipeChatStore.kt.
@MainActor
@Observable
final class RecipeChat {
    private(set) var messages: [ChatMessage] = []
    private(set) var sending = false
    /// What's in the text field; a message that fails to send comes back here.
    var draft = ""
    var error: String?
    /// Suggested recipes someone has saved.
    private(set) var saved: Set<Recipe> = []
    /// The reply that just came in, whose recipe cards deal in; ones already there just show.
    private(set) var freshReply: UUID?
    /// Three starting points for an empty chat, new each time one starts.
    private(set) var examples = ChatExamples.pick()

    private let uid: String
    private let service: RecipeService
    private let file: URL
    /// Bumped by Start over, so a reply to the old conversation doesn't land in the new one.
    private var generation = 0

    init(uid: String, householdId: String, service: RecipeService) {
        self.uid = uid
        self.service = service
        file = Self.directory.appendingPathComponent("\(Self.safe(uid))-\(Self.safe(householdId)).json")
        if let bytes = try? Data(contentsOf: file), let stored = try? JSONDecoder().decode(Stored.self, from: bytes) {
            messages = stored.messages
            saved = Set(stored.saved)
        }
    }

    func send(_ text: String) async {
        let message = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !message.isEmpty, !sending else { return }
        let conversation = messages + [ChatMessage(fromUser: true, text: message)]
        messages = conversation
        draft = ""
        sending = true
        let started = generation
        do {
            let reply = try await service.chat(conversation)
            guard started == generation else { return }
            freshReply = reply.id
            messages.append(reply)
            sending = false
            persist()
        } catch {
            guard started == generation else { return }
            sending = false
            // Take the message back off so the conversation still alternates, and put it back in the field.
            messages = Array(conversation.dropLast())
            draft = message
            self.error = (error as? ServerError)?.isRateLimited == true
                ? "That's all the recipe chat for today. Check back tomorrow."
                : "Couldn't get an answer. Check your connection and try again."
        }
    }

    func save(_ recipe: Recipe) {
        guard !saved.contains(recipe) else { return }
        service.add(recipe, uid: uid)
        saved.insert(recipe)
        persist()
    }

    /// Leaving the chat: cards already shown stay put when it opens again, and if nothing was asked, it opens with three
    /// new examples.
    func settle() {
        freshReply = nil
        if messages.isEmpty && !sending { examples = ChatExamples.pick(avoiding: examples) }
    }

    /// Clears the conversation for a fresh one. Recipes already saved stay in the recipe box.
    func startOver() {
        generation += 1
        messages = []
        saved = []
        sending = false
        draft = ""
        freshReply = nil
        examples = ChatExamples.pick(avoiding: examples)
        persist()
    }

    private struct Stored: Codable {
        var messages: [ChatMessage]
        var saved: [Recipe]
    }

    private func persist() {
        // A reply that lands after signing out isn't kept for the next person.
        guard Auth.auth().currentUser?.uid == uid else { return }
        if messages.isEmpty {
            try? FileManager.default.removeItem(at: file)
            return
        }
        do {
            try FileManager.default.createDirectory(at: Self.directory, withIntermediateDirectories: true)
            try JSONEncoder().encode(Stored(messages: messages, saved: Array(saved))).write(to: file, options: .atomic)
        } catch {
            print("Couldn't save the recipe chat: \(error)")
        }
    }

    /// On sign-out (or account deletion), so the next person on this phone doesn't see them.
    static func clearAll() {
        try? FileManager.default.removeItem(at: directory)
    }

    private static var directory: URL {
        URL.applicationSupportDirectory.appendingPathComponent("recipe-chats", isDirectory: true)
    }

    private static func safe(_ s: String) -> String {
        String(s.map { $0.isLetter || $0.isNumber || $0 == "-" || $0 == "_" ? $0 : "_" })
    }
}

/// Chat with the AI for recipe ideas. The conversation stays on the phone until Start over, so coming back picks it up.
/// Android: RecipeChatScreen.kt.
struct RecipeChatView: View {
    let data: HouseholdData
    let onOpenList: (String) -> Void
    @State private var viewing: Recipe?
    @State private var confirmingStartOver = false
    @FocusState private var fieldFocused: Bool

    private var chat: RecipeChat { data.recipeChat }

    var body: some View {
        conversation
            .background(Color.warmBackground)
            .navigationTitle("Ask for a recipe")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) { inputBar }
            .onDisappear { chat.settle() }
            .toolbar {
                if !chat.messages.isEmpty || chat.sending {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button("Start over") { confirmingStartOver = true }
                    }
                }
            }
            .navigationDestination(item: $viewing) { recipe in
                RecipeDetail(data: data, recipe: recipe, onOpenList: onOpenList)
                    .toolbar {
                        ToolbarItem(placement: .topBarTrailing) { ShareRecipeButton(recipe: recipe) }
                        ToolbarItem(placement: .topBarTrailing) { saveButton(recipe) }
                    }
            }
            .alert("Recipe chat", isPresented: showingError) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(chat.error ?? "")
            }
            .alert("Start a new chat?", isPresented: $confirmingStartOver) {
                Button("Start over") { withAnimation(.smooth) { chat.startOver() } }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("This conversation will be cleared. Recipes you saved stay in your recipe box.")
            }
    }

    private var showingError: Binding<Bool> {
        Binding(get: { chat.error != nil }, set: { if !$0 { chat.error = nil } })
    }

    /// The newest thing in the conversation, to keep in view.
    private var bottomID: AnyHashable? {
        chat.sending ? AnyHashable("thinking") : chat.messages.last.map { AnyHashable($0.id) }
    }

    private var conversation: some View {
        ScrollViewReader { proxy in
            ScrollView {
                // On a wide screen the conversation stays a readable column down the middle.
                Group {
                    if chat.messages.isEmpty && !chat.sending {
                        emptyState.transition(.opacity)
                    } else {
                        messageList.transition(.opacity)
                    }
                }
                .readableWidth()
            }
            .scrollDismissesKeyboard(.interactively)
            // Coming back to a conversation opens on its latest message.
            .defaultScrollAnchor(chat.messages.isEmpty ? .top : .bottom)
            .onChange(of: bottomID) { _, id in
                if let id { withAnimation { proxy.scrollTo(id, anchor: .bottom) } }
            }
        }
    }

    private var messageList: some View {
        LazyVStack(alignment: .leading, spacing: 14) {
            ForEach(chat.messages) { message in
                Group {
                    if message.fromUser { userBubble(message.text) } else { reply(message) }
                }
                // A new message rises and grows into place from its side of the screen.
                .transition(.asymmetric(
                    insertion: .move(edge: .bottom)
                        .combined(with: .opacity)
                        .combined(with: .scale(scale: 0.9, anchor: message.fromUser ? .bottomTrailing : .bottomLeading)),
                    removal: .opacity
                ))
            }
            if chat.sending {
                TypingBubble()
                    .id(AnyHashable("thinking"))
                    .transition(.scale(scale: 0.6, anchor: .leading).combined(with: .opacity))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .animation(.spring(duration: 0.4, bounce: 0.25), value: chat.messages.count)
        .animation(.spring(duration: 0.3, bounce: 0.2), value: chat.sending)
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 14) {
            // The sparkle breathes while it waits for an idea.
            Image(systemName: "sparkles").font(.largeTitle).foregroundStyle(Color.accentColor)
                .symbolEffect(.breathe)
            Text("What do you feel like cooking?").font(.brand(26))
            Text("Describe a dish, an ingredient, or the gear you want to use. You can save any recipe you like, then add what you're missing to a list.")
                .foregroundStyle(.secondary)
            ForEach(Array(chat.examples.enumerated()), id: \.element) { index, example in
                // The examples arrive one after another.
                DealIn(animate: true, index: index) {
                    Button(example) { Task { await chat.send(example) } }
                        .buttonStyle(.glass)
                        .font(.subheadline)
                }
            }
        }
        .padding(24)
        .padding(.top, 60)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func userBubble(_ text: String) -> some View {
        HStack {
            Spacer(minLength: 48)
            Text(text)
                .padding(.horizontal, 14).padding(.vertical, 10)
                .foregroundStyle(Color.onAccent)
                .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        }
    }

    private func reply(_ message: ChatMessage) -> some View {
        VStack(alignment: .leading, spacing: 10) {
            if !message.text.isEmpty { Text(message.text) }
            // A new reply's recipe cards deal in one after another, under its message.
            ForEach(Array(message.recipes.enumerated()), id: \.offset) { index, recipe in
                DealIn(animate: message.id == chat.freshReply, index: index) { suggestionCard(recipe) }
            }
        }
        .padding(.trailing, 24)
    }

    private func suggestionCard(_ recipe: Recipe) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(recipe.title).font(.headline)
            if !recipe.summary.isEmpty { Text(recipe.summary).font(.caption).foregroundStyle(.secondary) }
            if !recipe.description.isEmpty { Text(recipe.description).font(.subheadline).lineLimit(3) }
            HStack(spacing: 8) {
                Button("View recipe") { viewing = recipe }.prominentGlassButton()
                saveButton(recipe)
            }
            .font(.subheadline.weight(.medium))
            .padding(.top, 6)
        }
        .padding(16)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private func saveButton(_ recipe: Recipe) -> some View {
        let isSaved = chat.saved.contains(recipe)
        return Group {
            if isSaved {
                Label("Saved", systemImage: "checkmark").foregroundStyle(.secondary).padding(.horizontal, 8)
                    .transition(.scale(scale: 0.7).combined(with: .opacity))
            } else {
                Button("Save") { withAnimation(.bouncy) { chat.save(recipe) } }.buttonStyle(.glass)
                    .transition(.opacity)
            }
        }
        .sensoryFeedback(.success, trigger: isSaved) { _, saved in saved }
    }

    private var inputBar: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField("Ask for a recipe…", text: Bindable(chat).draft, axis: .vertical)
                .lineLimit(1...5)
                .focused($fieldFocused)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .glassEffect(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            Button {
                Task { await chat.send(chat.draft) }
            } label: {
                Image(systemName: "arrow.up").font(.body.weight(.semibold)).frame(width: 30, height: 30)
            }
            .prominentGlassButton()
            .buttonBorderShape(.circle)
            .disabled(chat.draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || chat.sending)
            .accessibilityLabel("Send")
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
    }
}

/// Three dots pulsing in a reply bubble while the AI writes. Android: Thinking in RecipeChatScreen.kt.
private struct TypingBubble: View {
    var body: some View {
        Image(systemName: "ellipsis")
            .font(.title2.weight(.bold))
            .foregroundStyle(.secondary)
            .symbolEffect(.variableColor.iterative.dimInactiveLayers.nonReversing, options: .repeating)
            .frame(height: 20)
            .padding(.horizontal, 16).padding(.vertical, 12)
            .background(Color.card, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            .accessibilityLabel("Thinking")
    }
}

/// Fades and rises into place a moment after the one before it (by `index`), when `animate`; otherwise just shows.
private struct DealIn<Content: View>: View {
    let animate: Bool
    let index: Int
    @ViewBuilder let content: Content
    @State private var shown = false

    var body: some View {
        let visible = shown || !animate
        content
            .opacity(visible ? 1 : 0)
            .offset(y: visible ? 0 : 18)
            .onAppear {
                guard animate, !shown else { return }
                withAnimation(.spring(duration: 0.45, bounce: 0.25).delay(0.15 + 0.09 * Double(index))) { shown = true }
            }
    }
}
