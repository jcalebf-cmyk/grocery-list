import SwiftUI

// Brand touches on top of standard iOS styling: Plus Jakarta Sans for titles (body text stays San Francisco),
// the garden-green accent, citrus highlights, and a warm background, matching the Android app.

extension Font {
    static func brand(_ size: CGFloat, _ weight: Font.Weight = .bold) -> Font {
        .custom("Plus Jakarta Sans", size: size).weight(weight)
    }
}

extension Color {
    static let citrus = Color("Citrus")
    static let citrusContainer = Color("CitrusContainer")
    static let warmBackground = Color("WarmBackground")
    static let card = Color("Card")
}

/// A colored initial for a household member. Each name always gets the same color.
struct MemberBadge: View {
    let name: String
    var size: CGFloat = 28

    var body: some View {
        Text(String(name.trimmed.prefix(1)).uppercased())
            .font(.system(size: size * 0.45, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: size, height: size)
            .background(Circle().fill(monogramColor(name)))
            .accessibilityLabel("Added by \(name)")
    }
}

private let monogramPalette: [Color] = [
    Color(red: 0.18, green: 0.49, blue: 0.20), Color(red: 0.08, green: 0.40, blue: 0.75),
    Color(red: 0.78, green: 0.16, blue: 0.16), Color(red: 0.42, green: 0.11, blue: 0.60),
    Color(red: 0.94, green: 0.42, blue: 0.00), Color(red: 0.00, green: 0.51, blue: 0.56),
    Color(red: 0.31, green: 0.20, blue: 0.18), Color(red: 0.68, green: 0.08, blue: 0.34),
]

/// Stable across launches (unlike String.hashValue), so a name keeps its color.
func monogramColor(_ name: String) -> Color {
    let sum = name.lowercased().unicodeScalars.reduce(0) { ($0 &* 31 &+ Int($1.value)) & 0x7fffffff }
    return monogramPalette[sum % monogramPalette.count]
}

/// The chain's logo on a white tile, or a colored tile with the store's initials.
struct StoreLogo: View {
    let name: String
    var cornerRadius: CGFloat = 14
    var shadow = false

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
        Group {
            if let url = StoreBrands.logoURL(for: name) {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().scaledToFit().padding(8).frame(maxWidth: .infinity, maxHeight: .infinity).background(.white)
                    } else if phase.error != nil {
                        monogram
                    } else {
                        Color.white
                    }
                }
            } else {
                monogram
            }
        }
        .clipShape(shape)
        .overlay(shape.strokeBorder(.black.opacity(shadow ? 0 : 0.08)))
        .shadow(color: .black.opacity(shadow ? 0.12 : 0), radius: 6, y: 3)
    }

    private var monogram: some View {
        let initials = name.split(separator: " ").prefix(2).compactMap(\.first).map { String($0).uppercased() }.joined()
        return Text(initials)
            .font(.system(.title3, weight: .bold))
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(monogramColor(name))
    }
}

/// Wraps children onto new lines, for chip clouds.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = arrange(width: proposal.width ?? .infinity, subviews: subviews)
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0,
                      height: rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0)))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in arrange(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.indices {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row { var indices: [Int] = []; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func arrange(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows: [Row] = [Row()]
        for (index, subview) in subviews.enumerated() {
            let size = subview.sizeThatFits(ProposedViewSize(width: nil, height: nil))
            if !rows[rows.count - 1].indices.isEmpty && rows[rows.count - 1].width + spacing + size.width > width {
                rows.append(Row())
            }
            var row = rows[rows.count - 1]
            row.width += (row.indices.isEmpty ? 0 : spacing) + size.width
            row.height = max(row.height, size.height)
            row.indices.append(index)
            rows[rows.count - 1] = row
        }
        return rows
    }
}

/// A frosted, floating text field for adding items, pinned above the keyboard.
struct AddItemBar: View {
    var placeholder = "Add an item"
    let onAdd: (String) -> Void
    @State private var text = ""
    @FocusState private var focused: Bool

    var body: some View {
        HStack(spacing: 10) {
            TextField(placeholder, text: $text)
                .textInputAutocapitalization(.sentences)
                .submitLabel(.done)
                .focused($focused)
                .onSubmit(submit)
                .padding(.leading, 18)
                .padding(.vertical, 14)
            Button(action: submit) {
                Image(systemName: "plus").font(.body.weight(.semibold)).frame(width: 22, height: 22)
            }
            .buttonStyle(.glassProminent)
            .buttonBorderShape(.circle)
            .disabled(text.trimmed.isEmpty)
            .padding(.vertical, 6)
            .padding(.trailing, 6)
        }
        .glassEffect(.regular.interactive(), in: .capsule)
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    private func submit() {
        guard !text.trimmed.isEmpty else { return }
        onAdd(text)
        text = ""
        focused = true // keep the keyboard up so several items can be added in a row
    }
}
