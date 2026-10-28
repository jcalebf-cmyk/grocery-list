import SwiftUI

struct ListView: View {
    let data: HouseholdData
    let listId: String
    @State private var list: GroceryList?
    @State private var items: [ListItem] = []
    @State private var confirmingFinish = false
    @State private var confirmingDelete = false
    @State private var editing: ListItem?
    @State private var editedName = ""
    @Environment(\.dismiss) private var dismiss

    private var active: Bool { list?.isActive == true }
    private var sortedItems: [ListItem] {
        items.sorted { a, b in a.checked != b.checked ? !a.checked : a.createdAt > b.createdAt }
    }
    private var suggestions: [ItemStat] {
        guard let list, active else { return [] }
        return suggestItems(data.stats, storeId: list.storeId, alreadyOnList: Set(items.map(\.key)), now: nowMillis(),
                            onHand: data.onHand.map(\.name))
    }

    var body: some View {
        List {
            if list?.isActive == false {
                Text("This list is closed out. Reopen it from the ••• menu to make changes.")
                    .font(.subheadline)
                    .listRowBackground(Color.citrusContainer)
            }
            if !suggestions.isEmpty {
                Section("Suggested") {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(suggestions) { stat in
                                Button { add(stat.displayName) } label: { Label(stat.displayName, systemImage: "plus") }
                                    .buttonStyle(.glass)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                    .listRowInsets(EdgeInsets(top: 0, leading: 16, bottom: 0, trailing: 16))
                    .listRowBackground(Color.clear)
                }
            }
            if items.isEmpty && list != nil {
                Text(active ? "Nothing on this list yet" : "No items on this list").foregroundStyle(.secondary)
            }
            ForEach(groupByAisle(sortedItems)) { section in
                Section(section.aisle?.label ?? "Checked off") {
                    ForEach(section.items) { item in
                        ItemRow(item: item, editable: active, addedBy: data.otherMembers[item.addedBy]) {
                            data.groceries.setChecked(item, in: listId, !item.checked)
                        }
                        .swipeActions {
                            if active, let list {
                                Button("Delete", systemImage: "trash", role: .destructive) { data.groceries.deleteItem(item, from: list) }
                                Button("Edit", systemImage: "pencil") { startEditing(item) }.tint(.gray)
                            }
                        }
                        .contextMenu {
                            if active {
                                Button("Edit", systemImage: "pencil") { startEditing(item) }
                            }
                        }
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .background(Color.warmBackground)
        .animation(.snappy, value: sortedItems)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .principal) {
                HStack(spacing: 8) {
                    if let name = list?.storeName, !name.isEmpty {
                        StoreLogo(name: name, cornerRadius: 7).frame(width: 36, height: 26)
                    }
                    Text(list?.displayName ?? "").font(.headline)
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    if active {
                        Button("Finish & close out", systemImage: "checkmark.circle") { confirmingFinish = true }
                        Button("Clear checked items", systemImage: "trash") { data.groceries.clearChecked(in: listId, items: items) }
                            .disabled(!items.contains(where: \.checked))
                    } else if list != nil {
                        Button("Reopen list", systemImage: "arrow.uturn.backward") { data.groceries.reopen(listId) }
                        Button("Delete list", systemImage: "trash", role: .destructive) { confirmingDelete = true }
                    }
                } label: {
                    Image(systemName: "ellipsis")
                }
            }
        }
        .deleteListAlert(isPresented: $confirmingDelete) {
            let groceries = data.groceries, id = listId
            Task { try? await groceries.deleteList(id) }
            dismiss()
        }
        .alert("Edit item", isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } }), presenting: editing) { item in
            TextField("Item", text: $editedName)
                .textInputAutocapitalization(.sentences)
            Button("Cancel", role: .cancel) {}
            Button("Save") {
                if let list { data.groceries.renameItem(item, in: list, to: editedName) }
            }
            .disabled(editedName.trimmed.isEmpty)
        }
        .closeOutListAlert(isPresented: $confirmingFinish) {
            data.groceries.closeOut(listId)
            dismiss()
        }
        .safeAreaInset(edge: .bottom) {
            if active { AddItemBar(onAdd: add) }
        }
        .task { await consume(data.groceries.listStream(listId)) { v in list = v } }
        .task { await consume(data.groceries.itemsStream(listId)) { v in items = v } }
    }

    private func add(_ name: String) {
        guard let list else { return }
        data.groceries.addItem(to: list, name: name, uid: data.user.id)
    }

    private func startEditing(_ item: ListItem) {
        editedName = item.name
        editing = item
    }

}

/// A list item: tap to check off (with a haptic and an animated strike-through); swipe or long-press to edit.
/// Closed lists show faded, read-only rows.
private struct ItemRow: View {
    let item: ListItem
    let editable: Bool
    let addedBy: String?
    let toggle: () -> Void

    var body: some View {
        Button(action: toggle) {
            HStack(spacing: 12) {
                Image(systemName: item.checked ? "checkmark.circle.fill" : "circle")
                    .font(.title2)
                    .foregroundStyle(editable ? (item.checked ? Color.accentColor : .secondary) : Color.secondary.opacity(0.4))
                    .contentTransition(.symbolEffect(.replace))
                Text(item.name)
                    .strikethrough(item.checked)
                    .foregroundStyle(!editable ? Color.primary.opacity(0.45) : item.checked ? Color.secondary : Color.primary)
                Spacer()
                if let addedBy { MemberBadge(name: addedBy, size: 26) }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(!editable)
        .sensoryFeedback(.selection, trigger: item.checked)
    }
}
