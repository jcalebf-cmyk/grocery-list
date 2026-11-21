import SwiftUI

/// Big screens: the iPhone Duo's inner screen and iPad-size windows. On a phone-size screen nothing changes. On a
/// wide one, single-column screens keep a readable width instead of stretching edge to edge, and screens that
/// split naturally (Home, a recipe) show two panes. Same breakpoints as `Adaptive.kt` on Android.
enum AdaptiveLayout {
    /// The widest a column of rows and text gets.
    static let readableWidth: CGFloat = 640
    /// From this width two panes sit side by side.
    static let twoPaneWidth: CGFloat = 840
}

extension View {
    /// Centers content in a column at most `max` wide, for stacks inside a ScrollView.
    func readableWidth(_ max: CGFloat = AdaptiveLayout.readableWidth) -> some View {
        frame(maxWidth: max).frame(maxWidth: .infinity)
    }

    /// For a List, Form or ScrollView: side margins that keep its content at most `max` wide. The view itself stays
    /// full width, so it still scrolls from anywhere on screen. The margins also reach scroll views nested inside
    /// (a horizontal row, a TextEditor), so use `readableWidth()` where there are any.
    func readableScrollWidth(_ max: CGFloat = AdaptiveLayout.readableWidth) -> some View {
        modifier(ReadableScrollWidth(max: max))
    }

    /// Reports whether the view is wide enough for two panes, as it's resized or the phone is opened and closed.
    func onTwoPaneChange(_ action: @escaping (Bool) -> Void) -> some View {
        onGeometryChange(for: Bool.self) { $0.size.width >= AdaptiveLayout.twoPaneWidth } action: { action($0) }
    }
}

private struct ReadableScrollWidth: ViewModifier {
    let max: CGFloat
    @State private var margins = Margins()

    /// Explicit content margins count from the screen edge, not the safe area (the opened iPhone Duo keeps its
    /// clock and camera in a column down one side), so each side adds its safe-area inset to stay centered with
    /// bars that sit inside the safe area.
    /// nil leaves the system's own margins alone: setting 0 on a phone-size screen would strip a List's insets.
    struct Margins: Equatable {
        var leading: CGFloat?
        var trailing: CGFloat?
    }

    func body(content: Content) -> some View {
        content
            .contentMargins(.leading, margins.leading, for: .scrollContent)
            .contentMargins(.trailing, margins.trailing, for: .scrollContent)
            .onGeometryChange(for: Margins.self) { proxy in
                let gutter = Swift.max(0, (proxy.size.width - max) / 2)
                guard gutter > 0 else { return Margins() }
                return Margins(leading: proxy.safeAreaInsets.leading + gutter, trailing: proxy.safeAreaInsets.trailing + gutter)
            } action: { margins = $0 }
    }
}
