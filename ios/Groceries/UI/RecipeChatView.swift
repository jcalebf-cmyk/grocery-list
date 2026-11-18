import SwiftUI

/// The "Ask for a recipe" chat. The conversation lives only here, for as long as the screen is open; recipes
/// someone saves go to the household's recipe box. Android: RecipeChatViewModel.
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

    func send(_ text: String, data: HouseholdData) async {
        let message = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !message.isEmpty, !sending else { return }
        let conversation = messages + [ChatMessage(fromUser: true, text: message)]
        messages = conversation
        draft = ""
        sending = true
        defer { sending = false }
        do {
            let reply = try await data.recipeService.chat(conversation, onHand: data.onHand)
            messages.append(reply)
        } catch {
            // Take the message back off so the conversation still alternates, and put it back in the field.
            messages = Array(conversation.dropLast())
            draft = message
            self.error = (error as? ServerError)?.isRateLimited == true
                ? "That's all the recipe chat for today. Check back tomorrow."
                : "Couldn't get an answer. Check your connection and try again."
        }
    }

    func save(_ recipe: Recipe, data: HouseholdData) {
        guard !saved.contains(recipe) else { return }
        data.recipeService.add(recipe, uid: data.user.id)
        saved.insert(recipe)
    }
}

/// Chat with the AI for recipe ideas. Android: RecipeChatScreen.kt.
struct RecipeChatView: View {
    let data: HouseholdData
    let onOpenList: (String) -> Void
    @State private var chat = RecipeChat()
    @State private var viewing: Recipe?
    @FocusState private var fieldFocused: Bool

    private static let examples = [
        "Boneless chicken thighs in the dutch oven",
        "A quick vegetarian weeknight dinner",
        "What can I make with what's in my kitchen?",
    ]

    var body: some View {
        conversation
            .background(Color.warmBackground)
            .navigationTitle("Ask for a recipe")
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) { inputBar }
            .navigationDestination(item: $viewing) { recipe in
                RecipeDetail(data: data, recipe: recipe, onOpenList: onOpenList)
                    .toolbar { ToolbarItem(placement: .topBarTrailing) { saveButton(recipe) } }
            }
            .alert("Recipe chat", isPresented: showingError) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(chat.error ?? "")
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
                if chat.messages.isEmpty && !chat.sending { emptyState } else { messageList }
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: bottomID) { _, id in
                if let id { withAnimation { proxy.scrollTo(id, anchor: .bottom) } }
            }
        }
    }

    private var messageList: some View {
        LazyVStack(alignment: .leading, spacing: 14) {
            ForEach(chat.messages) { message in
                if message.fromUser { userBubble(message.text) } else { reply(message) }
            }
            if chat.sending {
                HStack(spacing: 10) {
                    ProgressView()
                    Text("Thinking…").foregroundStyle(.secondary)
                }
                .id(AnyHashable("thinking"))
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private var emptyState: some View {
        VStack(alignment: .leading, spacing: 14) {
            Image(systemName: "sparkles").font(.largeTitle).foregroundStyle(Color.accentColor)
            Text("What do you feel like cooking?").font(.brand(26))
            Text("Describe a dish, an ingredient, or the gear you want to use. You can save any recipe you like, then add what you're missing to a list.")
                .foregroundStyle(.secondary)
            ForEach(Self.examples, id: \.self) { example in
                Button(example) { Task { await chat.send(example, data: data) } }
                    .buttonStyle(.glass)
                    .font(.subheadline)
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
            ForEach(message.recipes, id: \.self) { recipe in suggestionCard(recipe) }
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

    @ViewBuilder private func saveButton(_ recipe: Recipe) -> some View {
        if chat.saved.contains(recipe) {
            Label("Saved", systemImage: "checkmark").foregroundStyle(.secondary).padding(.horizontal, 8)
        } else {
            Button("Save") { chat.save(recipe, data: data) }.buttonStyle(.glass)
        }
    }

    private var inputBar: some View {
        HStack(alignment: .bottom, spacing: 8) {
            TextField("Ask for a recipe…", text: $chat.draft, axis: .vertical)
                .lineLimit(1...5)
                .focused($fieldFocused)
                .padding(.horizontal, 16).padding(.vertical, 10)
                .glassEffect(in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            Button {
                Task { await chat.send(chat.draft, data: data) }
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
