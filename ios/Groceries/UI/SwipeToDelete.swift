import SwiftUI

/// Swipe left to reveal a Delete button, like a List row's swipe action. Home's lists live in a ScrollView
/// rather than a List, so this does it by hand. The vertical scroll keeps working because the drag only
/// moves the row when it's mostly sideways.
struct SwipeToDelete<Content: View>: View {
    let onDelete: () -> Void
    @ViewBuilder let content: Content
    @State private var offset: CGFloat = 0
    @State private var open = false
    private let revealWidth: CGFloat = 84

    var body: some View {
        ZStack(alignment: .trailing) {
            Button {
                close()
                onDelete()
            } label: {
                Image(systemName: "trash").font(.title3).foregroundStyle(.white)
                    .frame(width: revealWidth - 8)
                    .frame(maxHeight: .infinity)
                    .background(.red, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
            }
            .buttonStyle(.plain)
            .padding(.trailing, 16) // lines up with the row's card, which is inset by its own padding
            .padding(.vertical, 4)
            .opacity(offset < 0 ? 1 : 0)
            .accessibilityLabel("Delete")

            content
                .offset(x: offset)
                .simultaneousGesture(
                    DragGesture(minimumDistance: 20)
                        .onChanged { value in
                            guard abs(value.translation.width) > abs(value.translation.height) else { return }
                            let start = open ? -revealWidth : 0
                            offset = min(0, max(-revealWidth * 1.4, start + value.translation.width))
                        }
                        .onEnded { _ in
                            withAnimation(.snappy) {
                                open = offset < -revealWidth / 2
                                offset = open ? -revealWidth : 0
                            }
                        }
                )
        }
        .accessibilityAction(named: "Delete", onDelete)
    }

    private func close() {
        withAnimation(.snappy) { open = false; offset = 0 }
    }
}

extension View {
    /// Shared by the list's menu and swiping a past list on Home.
    func deleteListAlert(isPresented: Binding<Bool>, onDelete: @escaping () -> Void) -> some View {
        alert("Delete this list?", isPresented: isPresented) {
            Button("Delete", role: .destructive, action: onDelete)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("It's removed for everyone in your household. Your suggestions won't change.")
        }
    }
}
