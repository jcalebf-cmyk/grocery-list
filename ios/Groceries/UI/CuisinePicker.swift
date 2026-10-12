import SwiftUI

/// Toggleable cuisine chips. Once `max` are picked, the rest are disabled. Same as Android's CuisineChips.
struct CuisineChips: View {
    @Binding var selected: [String]
    var max: Int?

    var body: some View {
        FlowLayout {
            ForEach(cuisines, id: \.self) { cuisine in
                chip(cuisine, isSelected: selected.contains(cuisine))
            }
        }
    }

    @ViewBuilder private func chip(_ cuisine: String, isSelected: Bool) -> some View {
        let button = Button {
            selected = cuisines.filter { $0 == cuisine ? !isSelected : selected.contains($0) }
        } label: {
            HStack(spacing: 6) {
                if isSelected { Image(systemName: "checkmark") }
                Text(cuisine)
            }
            .fixedSize()
        }
        .disabled(!isSelected && max.map { selected.count >= $0 } ?? false)
        if isSelected { button.buttonStyle(.glassProminent) } else { button.buttonStyle(.glass) }
    }
}

/// Shown once per household, before the first recipes, so they start out leaning the right way.
struct CuisineSetupView: View {
    let onDone: ([String]) -> Void
    @State private var selected: [String] = []

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                Text("What do you like to eat?").font(.brand(30))
                Text("Pick a few cuisines and your recipe ideas will lean toward them. You can change these in Settings.")
                    .foregroundStyle(.secondary)
                CuisineChips(selected: $selected)
                Button { onDone(selected) } label: {
                    Text("Continue").font(.headline).frame(maxWidth: .infinity, minHeight: 36)
                }
                .buttonStyle(.glassProminent)
                .disabled(selected.isEmpty)
                .padding(.top, 8)
                Button("Skip for now") { onDone([]) }
                    .frame(maxWidth: .infinity)
            }
            .padding(24)
        }
        .background(Color.warmBackground)
    }
}

/// A sheet of cuisine chips with a confirm button, and an optional secondary action like "Back to usual".
struct CuisineSheet: View {
    let title: String
    let subtitle: String
    let initial: [String]
    let confirm: String
    var max: Int?
    var requireSelection = false
    var secondary: (String, () -> Void)?
    let onConfirm: ([String]) -> Void
    @State private var selected: [String] = []
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text(subtitle).foregroundStyle(.secondary)
                    CuisineChips(selected: $selected, max: max)
                    if let (label, action) = secondary {
                        Button(label, action: action).frame(maxWidth: .infinity)
                    }
                }
                .padding(20)
            }
            .background(Color.warmBackground)
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button(confirm) { onConfirm(selected) }.disabled(requireSelection && selected.isEmpty)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .onAppear { selected = initial }
    }
}
