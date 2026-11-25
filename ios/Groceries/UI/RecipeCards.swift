import SwiftUI

/// Recipe cards in rows that fill the width: two across on any phone (one when the text is set very large), more where
/// there's room for cards at least 180pt wide. Android: recipeRows in RecipeCards.kt.
struct RecipeGrid: View {
    let matches: [RecipeMatch]
    /// Opening a recipe zooms out of its card.
    var zoom: Namespace.ID?
    @State private var width: CGFloat = 375
    @Environment(\.dynamicTypeSize) private var textSize

    private let gap: CGFloat = 12
    private let wideCard: CGFloat = 180

    private var columns: Int {
        textSize.isAccessibilitySize ? 1 : max(2, Int((width + gap) / (wideCard + gap)))
    }

    var body: some View {
        LazyVGrid(columns: Array(repeating: GridItem(.flexible(), spacing: gap), count: columns), spacing: gap) {
            ForEach(matches) { match in
                NavigationLink(value: Route.recipe(match.id)) { RecipeCard(match: match) }
                    .buttonStyle(.plain)
                    .modifier(ZoomSource(id: match.id, namespace: zoom))
            }
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
        .padding(.horizontal, 16)
    }
}

private struct ZoomSource: ViewModifier {
    let id: String
    let namespace: Namespace.ID?

    func body(content: Content) -> some View {
        if let namespace { content.matchedTransitionSource(id: id, in: namespace) } else { content }
    }
}

/// A saved recipe: its picture, its title and the basics, or the ingredient a search matched, with an icon for where it
/// came from.
private struct RecipeCard: View {
    let match: RecipeMatch
    /// The icons differ in size; a fixed box keeps every card the same height and their text lined up.
    @ScaledMetric(relativeTo: .caption) private var iconSize: CGFloat = 16

    private var recipe: Recipe { match.recipe }

    private var icon: String {
        if !recipe.sourceUrl.isEmpty { return "play.rectangle" }
        return recipe.source == .ai ? "sparkles" : "fork.knife"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            RecipePicture(recipe: recipe).frame(height: 96)
            details
        }
        .background(Color.card)
        .clipShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
    }

    private var details: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Always room for two lines, so every card is the same height whatever its title or the text size.
            Text(recipe.title)
                .font(.subheadline.weight(.semibold))
                .lineLimit(2, reservesSpace: true)
                .multilineTextAlignment(.leading)
            HStack(spacing: 6) {
                Image(systemName: icon).foregroundStyle(Color.accentColor)
                    .frame(width: iconSize, height: iconSize).accessibilityHidden(true)
                // The space keeps the line's height when there's nothing to say.
                let detail = match.ingredient.map { "Has \($0)" } ?? recipe.cardSummary
                Text(detail.isEmpty ? " " : detail).foregroundStyle(.secondary).lineLimit(1)
            }
            .font(.caption)
        }
        .padding(.horizontal, 14)
        .padding(.top, 10)
        .padding(.bottom, 12)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The video's thumbnail, or a food emoji on a colored tile when there's no video (or no connection).
private struct RecipePicture: View {
    let recipe: Recipe
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        let visual = recipeEmoji(recipe.title)
        ZStack {
            visual.tint.color(dark: scheme == .dark)
            if let url = videoThumbnail(recipe.sourceUrl) {
                VideoThumbnail(url: url) { emoji(visual) }
            } else {
                emoji(visual)
            }
        }
        .frame(maxWidth: .infinity)
        .clipped()
        .accessibilityHidden(true)
    }

    // A picture, so it doesn't grow with the text size.
    private func emoji(_ visual: RecipeEmoji) -> some View { Text(visual.emoji).font(.system(size: 44)) }
}

/// AsyncImage, except that thumbnails stay in memory and one scrolled away while loading tries again when it's back
/// (AsyncImage would be left showing its failure).
private struct VideoThumbnail<Fallback: View>: View {
    let url: URL
    @ViewBuilder let fallback: Fallback
    @State private var image: UIImage?
    @State private var failed = false

    var body: some View {
        Group {
            if let image = image ?? thumbnailCache.object(forKey: url as NSURL) {
                // An overlay, so filling the tile doesn't make it bigger.
                Color.clear.overlay { Image(uiImage: image).resizable().scaledToFill() }.clipped()
            } else if failed {
                fallback
            } else {
                Color.clear
            }
        }
        .task(id: url) {
            guard thumbnailCache.object(forKey: url as NSURL) == nil else { return }
            do {
                let (data, response) = try await URLSession.shared.data(from: url)
                guard (response as? HTTPURLResponse)?.statusCode == 200,
                      let loaded = await UIImage(data: data)?.byPreparingForDisplay() else { failed = true; return }
                thumbnailCache.setObject(loaded, forKey: url as NSURL)
                image = loaded
                failed = false
            } catch {
                if !Task.isCancelled { failed = true }
            }
        }
    }
}

/// Emptied by the system when memory's short.
private let thumbnailCache = NSCache<NSURL, UIImage>()

private extension RecipeTint {
    /// Same shades as `tintColor` in RecipeCards.kt.
    func color(dark: Bool) -> Color {
        let hex: UInt32 = switch self {
        case .red: dark ? 0x5C2B25 : 0xFFDAD4
        case .orange: dark ? 0x5C3A17 : 0xFFDFC2
        case .yellow: dark ? 0x4E4313 : 0xFBEBB5
        case .green: dark ? 0x23472F : 0xD2EBD5
        case .pink: dark ? 0x552A3B : 0xFADAE4
        case .blue: dark ? 0x1F4357 : 0xD3E6F2
        case .tan: dark ? 0x4A3F30 : 0xEADFCF
        }
        return Color(red: Double(hex >> 16 & 0xFF) / 255, green: Double(hex >> 8 & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

/// Every saved recipe as cards, newest first, with search. Opens ready to type. Android: AllRecipesScreen.
struct AllRecipesView: View {
    let data: HouseholdData
    let onPaste: () -> Void
    @State private var query = ""
    @State private var searching = false
    @State private var opened = false
    @State private var scroll = ScrollPosition(edge: .top)

    var body: some View {
        let shown = searchRecipes(data.recipes, query: query)
        ScrollView {
            RecipeGrid(matches: shown)
                .padding(.vertical, 8)
                .readableWidth()
        }
        .scrollPosition($scroll)
        .scrollDismissesKeyboard(.immediately)
        // The best matches are at the top.
        .onChange(of: query) { scroll.scrollTo(edge: .top) }
        .overlay {
            if shown.isEmpty && !query.isEmpty { ContentUnavailableView.search(text: query) }
        }
        .background(Color.warmBackground)
        .navigationTitle("Recipes")
        .searchable(text: $query, isPresented: $searching, prompt: "Search by name or ingredient")
        // Only on opening, not on coming back from a recipe.
        .onAppear {
            if !opened { searching = true }
            opened = true
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                NavigationLink(value: Route.recipeChat) { Image(systemName: "sparkles") }.accessibilityLabel("Ask for a recipe")
            }
            ToolbarItem(placement: .topBarTrailing) {
                Button("Paste a recipe", systemImage: "doc.on.clipboard", action: onPaste)
            }
        }
    }
}
