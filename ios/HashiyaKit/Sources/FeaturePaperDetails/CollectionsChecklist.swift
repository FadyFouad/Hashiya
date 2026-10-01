import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// What the checklist does when the user acts; `PaperDetailsScreen` wires these to the view model.
public struct CollectionsChecklistActions {
    public var toggle: (Int64) -> Void = { _ in }
    public var newCollection: () -> Void = {}
    public var done: () -> Void = {}

    public init() {}
}

/// The checklist sheet's content: every collection with a check mark when the paper is in it, then New collection.
/// With no collections, only New collection and a line explaining what collections are for. It brings its own
/// navigation bar, and its banners show inside it, so a sheet never hides them.
public struct CollectionsChecklist: View {
    private let collections: [PaperCollection]
    private let memberIDs: Set<Int64>
    private let message: PaperDetailsMessage?
    private let actions: CollectionsChecklistActions

    public init(collections: [PaperCollection], memberIDs: Set<Int64>, message: PaperDetailsMessage?, actions: CollectionsChecklistActions) {
        self.collections = collections
        self.memberIDs = memberIDs
        self.message = message
        self.actions = actions
    }

    public var body: some View {
        NavigationStack {
            List {
                if collections.isEmpty {
                    Section {
                        newCollectionButton
                    } footer: {
                        Text(verbatim: L10n.string("details.collectionsHint"))
                            .font(.hashiya(.meta))
                            .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    }
                } else {
                    Section {
                        ForEach(collections) { collection in
                            row(collection)
                        }
                    }
                    Section {
                        newCollectionButton
                    }
                }
            }
            .navigationTitle(Text(verbatim: L10n.string("details.collections")))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button(action: actions.done) {
                        Text(verbatim: L10n.string("details.doneEditing"))
                    }
                }
            }
            .overlay(alignment: .bottom) { PaperDetailsBanner(message: message) }
            .animation(.default, value: message)
        }
    }

    private func row(_ collection: PaperCollection) -> some View {
        let isMember = memberIDs.contains(collection.id)
        return Button {
            actions.toggle(collection.id)
        } label: {
            HStack(spacing: 12) {
                Text(verbatim: collection.name)
                    .font(.hashiya(.body))
                    .foregroundStyle(HashiyaColors.onSurface)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "checkmark")
                    .font(.body.weight(.semibold))
                    .foregroundStyle(HashiyaColors.primary)
                    .opacity(isMember ? 1 : 0)
                    .accessibilityHidden(true)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isMember ? .isSelected : [])
        .accessibilityIdentifier("collection.\(collection.name)")
    }

    private var newCollectionButton: some View {
        Button(action: actions.newCollection) {
            Label {
                Text(verbatim: L10n.string("details.newCollection"))
            } icon: {
                Image(systemName: "plus")
            }
            .font(.hashiya(.body))
            .foregroundStyle(HashiyaColors.primary)
        }
    }
}
