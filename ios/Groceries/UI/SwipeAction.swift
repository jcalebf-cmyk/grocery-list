import SwiftUI

/// Swipe a row left to run an action (each one asks to confirm first), like Android's swipe to dismiss.
/// Home's lists live in a ScrollView rather than a List, so this does it by hand. The drag is a UIKit pan
/// that only starts when it's mostly sideways: vertical drags still scroll, and a sideways swipe cancels
/// the row's tap so it doesn't open the list.
struct SwipeAction<Content: View>: View {
    let label: String
    let systemImage: String
    let tint: Color
    var iconColor: Color = .white
    let action: () -> Void
    @ViewBuilder let content: Content
    @State private var offset: CGFloat = 0
    private let threshold: CGFloat = 96

    private var armed: Bool { offset <= -threshold }

    var body: some View {
        ZStack(alignment: .trailing) {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(tint.opacity(armed ? 1 : 0.75))
                .overlay(alignment: .trailing) {
                    Image(systemName: systemImage).font(.title3.weight(.semibold)).foregroundStyle(iconColor)
                        .scaleEffect(armed ? 1.15 : 1)
                        .padding(.trailing, 28)
                }
                .padding(.horizontal, 16) // lines up with the row's card, which is inset by its own padding
                .padding(.vertical, 4)
                .opacity(offset < 0 ? 1 : 0)
                .accessibilityHidden(true)

            content
                .offset(x: offset)
                .gesture(SidewaysPan(
                    onChange: { x in
                        // Past the threshold the row slows down, so it feels like it's being pulled.
                        let pulled = min(0, x)
                        offset = pulled > -threshold ? pulled : -threshold + (pulled + threshold) / 3
                    },
                    onEnd: {
                        let triggered = armed
                        withAnimation(.snappy) { offset = 0 }
                        if triggered { action() }
                    }
                ))
                .sensoryFeedback(.impact(weight: .medium), trigger: armed) { _, isArmed in isArmed }
        }
        .animation(.snappy(duration: 0.15), value: armed)
        .accessibilityAction(named: label, action)
    }
}

/// A UIKit pan that only begins for leftward, mostly horizontal drags. Unlike a SwiftUI DragGesture, it
/// takes the touch away from the row's tap and leaves vertical drags to the scroll view.
private struct SidewaysPan: UIGestureRecognizerRepresentable {
    let onChange: (CGFloat) -> Void
    let onEnd: () -> Void

    func makeCoordinator(converter: CoordinateSpaceConverter) -> Coordinator { Coordinator() }

    func makeUIGestureRecognizer(context: Context) -> UIPanGestureRecognizer {
        let pan = UIPanGestureRecognizer()
        pan.delegate = context.coordinator
        return pan
    }

    func handleUIGestureRecognizerAction(_ recognizer: UIPanGestureRecognizer, context: Context) {
        switch recognizer.state {
        case .began, .changed: onChange(recognizer.translation(in: recognizer.view).x)
        case .ended, .cancelled, .failed: onEnd()
        default: break
        }
    }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        func gestureRecognizerShouldBegin(_ recognizer: UIGestureRecognizer) -> Bool {
            guard let pan = recognizer as? UIPanGestureRecognizer else { return false }
            let velocity = pan.velocity(in: pan.view)
            return velocity.x < 0 && abs(velocity.x) > abs(velocity.y)
        }

        // The scroll view waits to see whether this is a sideways swipe before it starts scrolling.
        func gestureRecognizer(_ recognizer: UIGestureRecognizer, shouldBeRequiredToFailBy other: UIGestureRecognizer) -> Bool {
            other.view is UIScrollView
        }
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

    /// Shared by the list's menu and swiping an active list on Home.
    func closeOutListAlert(isPresented: Binding<Bool>, onCloseOut: @escaping () -> Void) -> some View {
        alert("Close out this list?", isPresented: isPresented) {
            Button("Close out", action: onCloseOut)
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("It moves to Past lists. You can reopen it anytime.")
        }
    }
}
