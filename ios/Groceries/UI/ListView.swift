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
    @State private var noting: ListItem?
    @State private var editedNote = ""
    @Environment(\.dismiss) private var dismiss

    private var active: Bool { list?.isActive == true }
    private var sortedItems: [ListItem] {
        items.sorted { a, b in a.checked != b.checked ? !a.checked : a.createdAt > b.createdAt }
    }
    private var suggestions: [ItemStat] {
        guard let list, active else { return [] }
        return suggestItems(data.stats, storeId: list.storeId, alreadyOnList: Set(items.map(\.key)), now: nowMillis())
    }

    var body: some View {
        content
            .deleteListAlert(isPresented: $confirmingDelete) {
                let groceries = data.groceries, id = listId
                Task { try? await groceries.deleteList(id) }
                dismiss()
            }
            .alert("Note for \(noting?.name ?? "")", isPresented: Binding(get: { noting != nil }, set: { if !$0 { noting = nil } }), presenting: noting) { item in
                TextField("The low-sodium one", text: $editedNote)
                    .textInputAutocapitalization(.sentences)
                Button("Cancel", role: .cancel) {}
                // Saving it blank removes the note.
                Button("Save") { data.groceries.setNote(item, in: listId, to: editedNote) }
            }
            .onChange(of: editedNote) { _, text in
                if text.count > maxNoteLength { editedNote = String(text.prefix(maxNoteLength)) }
            }
            .alert("Edit name", isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } }), presenting: editing) { item in
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

    private var content: some View {
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
                                    .contextMenu {
                                        Button("Don't suggest", systemImage: "xmark") { data.groceries.dismissSuggestion(stat) }
                                    }
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
                        itemRow(item)
                    }
                }
            }
        }
        .scrollContentBackground(.hidden)
        .readableScrollWidth()
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
                // What's still to get; nothing to send once everything's checked off.
                if let list, let text = listShareText(list, items: items) {
                    let subject = listShareSubject(list)
                    ShareLink(item: text, subject: Text(subject)) {
                        Label("Share list", systemImage: "square.and.arrow.up")
                    }
                } else {
                    Button("Share list", systemImage: "square.and.arrow.up") {}.disabled(true)
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
    }

    private func itemRow(_ item: ListItem) -> some View {
        ItemRow(item: item, editable: active, addedBy: data.otherMembers[item.addedBy]) {
            data.groceries.setChecked(item, in: listId, !item.checked)
        }
        .swipeActions {
            if active, let list {
                Button("Delete", systemImage: "trash", role: .destructive) { data.groceries.deleteItem(item, from: list) }.tint(.red)
                Button("Edit", systemImage: "pencil") { startEditing(item) }.tint(.gray)
                Button("Note", systemImage: "note.text") { startNote(item) }.tint(.accentColor)
            }
        }
        .contextMenu {
            if active {
                Button(item.note == nil ? "Add a note" : "Edit note", systemImage: "note.text") { startNote(item) }
                Button("Edit name", systemImage: "pencil") { startEditing(item) }
            }
        }
    }

    private func add(_ name: String) {
        guard let list else { return }
        data.groceries.addItem(to: list, name: name, uid: data.user.id)
    }

    private func startEditing(_ item: ListItem) {
        editedName = item.name
        editing = item
    }

    private func startNote(_ item: ListItem) {
        editedNote = item.note ?? ""
        noting = item
    }
}

/// A list item: tap to check off (with a haptic and an animated strike-through); swipe or long-press to add a note or edit.
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
                VStack(alignment: .leading, spacing: 2) {
                    Text(item.name)
                        .strikethrough(item.checked)
                        .foregroundStyle(!editable ? Color.primary.opacity(0.45) : item.checked ? Color.secondary : Color.primary)
                    if let note = item.note, !note.isEmpty {
                        Text(note).font(.subheadline).foregroundStyle(.secondary).opacity(editable && !item.checked ? 1 : 0.6)
                    }
                }
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
