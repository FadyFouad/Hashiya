import Foundation
import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// What Details does when the user acts; `PaperDetailsScreen` wires these to the view model.
public struct PaperDetailsActions {
    public var updateNote: (NoteSection, String) -> Void = { _, _ in }
    public var setStatus: (ReadingStatus) -> Void = { _ in }
    public var openURL: (URL) -> Void = { _ in }
    public var remove: () -> Void = {}
    public var retrySave: () -> Void = {}
    public var retryLoadNotes: () -> Void = {}
    public var showCollections: () -> Void = {}
    public var copyBibTeX: () -> Void = {}

    public init() {}
}

/// A loaded paper's Details: the header, the status, the links, the abstract and the notes form, with the banners
/// and the navigation bar's More options. Put it in a `NavigationStack`.
public struct PaperDetailsContent: View {
    private let paper: LibraryPaper
    private let collections: [PaperCollection]
    private let memberIDs: Set<Int64>
    private let notes: PaperNotes?
    private let saveState: NotesSaveState
    private let message: PaperDetailsMessage?
    private let actions: PaperDetailsActions

    @FocusState private var focusedSection: NoteSection?

    /// - Parameters:
    ///   - collections: every collection; the row shows those in `memberIDs`, in this order.
    ///   - notes: the notes to seed the fields with; nil shows "Couldn't load your notes" and no fields.
    public init(
        paper: LibraryPaper,
        collections: [PaperCollection] = [],
        memberIDs: Set<Int64> = [],
        notes: PaperNotes?,
        saveState: NotesSaveState,
        message: PaperDetailsMessage?,
        actions: PaperDetailsActions
    ) {
        self.paper = paper
        self.collections = collections
        self.memberIDs = memberIDs
        self.notes = notes
        self.saveState = saveState
        self.message = message
        self.actions = actions
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                header
                ReadingStatusSelector(status: paper.status, onChange: actions.setStatus)
                    .padding(.top, 20)
                CollectionsRow(names: collections.filter { memberIDs.contains($0.id) }.map(\.name), action: actions.showCollections)
                    .padding(.top, 16)
                links
                abstract
                    .padding(.top, 24)
                notesSection
                    .padding(.top, 24)
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 32)
        }
        .scrollDismissesKeyboard(.interactively)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(HashiyaColors.surface)
        .overlay(alignment: .bottom) { PaperDetailsBanner(message: message, retrySave: actions.retrySave) }
        .animation(.default, value: message)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { moreOptions }
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button {
                    focusedSection = nil
                } label: {
                    Text(verbatim: L10n.string("details.doneEditing"))
                }
            }
        }
    }

    // MARK: Paper

    private var header: some View {
        VStack(alignment: .leading, spacing: 12) {
            PaperText(PaperFormat.title(paper.paper), style: .previewTitle)
                .accessibilityAddTraits(.isHeader)
            if !paper.paper.authors.isEmpty {
                PaperText(paper.paper.authors.map(\.name).joined(separator: ", "), style: .body, color: HashiyaColors.onSurfaceVariant)
            }
            Text(verbatim: PaperFormat.previewMeta(paper.paper))
                .font(.hashiya(.meta))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .frame(maxWidth: .infinity, alignment: .leading)
            if paper.paper.isOpenAccess {
                StatusBadge(text: DesignSystemStrings.openAccess(hasPDF: paper.paper.openAccessPDFURL != nil), kind: .openAccess)
            }
        }
    }

    @ViewBuilder
    private var links: some View {
        let doi = paper.paper.doi.flatMap(DOILink.url(for:))
        let pdf = paper.paper.openAccessPDFURL.flatMap(URL.init(string:))
        if doi != nil || pdf != nil {
            HashiyaGlassGroup(spacing: 12) {
                HStack(spacing: 12) {
                    if let doi {
                        linkButton(DesignSystemStrings.openDOI, icon: "arrow.up.forward.square") { actions.openURL(doi) }
                    }
                    if let pdf {
                        linkButton(L10n.string("details.openPDF"), icon: "doc.richtext") { actions.openURL(pdf) }
                    }
                }
            }
            .controlSize(.large)
            .padding(.top, 16)
        }
    }

    private func linkButton(_ title: String, icon: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Label {
                Text(verbatim: title)
            } icon: {
                Image(systemName: icon)
            }
            .font(.hashiya(.label))
            .frame(maxWidth: .infinity)
        }
        .hashiyaSecondaryButton()
    }

    private var abstract: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(verbatim: DesignSystemStrings.abstract)
                .font(.hashiya(.label))
                .foregroundStyle(HashiyaColors.onSurfaceVariant)
                .accessibilityAddTraits(.isHeader)
            if let abstract = paper.paper.abstract {
                PaperText(abstract, style: .body)
            } else {
                Text(verbatim: DesignSystemStrings.noAbstract)
                    .font(.hashiya(.body))
                    .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    // MARK: Notes

    private var notesSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Text(verbatim: L10n.string("details.notesTitle"))
                    .font(.hashiya(.stateTitle))
                    .foregroundStyle(HashiyaColors.onSurface)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                if let status = L10n.saveStatus(saveState) {
                    Text(verbatim: status)
                        .font(.hashiya(.meta))
                        .foregroundStyle(saveState == .failed ? HashiyaColors.error : HashiyaColors.onSurfaceVariant)
                        .accessibilityIdentifier("details.saveStatus")
                }
            }
            if let notes {
                ForEach(NoteSection.allCases, id: \.self) { section in
                    NoteField(section: section, initialText: notes[section], focus: $focusedSection) { text in
                        actions.updateNote(section, text)
                    }
                }
            } else {
                VStack(alignment: .leading, spacing: 8) {
                    Text(verbatim: L10n.string("details.notesLoadFailed"))
                        .font(.hashiya(.body))
                        .foregroundStyle(HashiyaColors.onSurfaceVariant)
                    Button(action: actions.retryLoadNotes) {
                        Text(verbatim: L10n.string("details.retry")).font(.hashiya(.label))
                    }
                    .hashiyaSecondaryButton()
                }
            }
        }
    }

    // MARK: Chrome

    private var moreOptions: some View {
        Menu {
            Button(action: actions.copyBibTeX) {
                Label {
                    Text(verbatim: L10n.string("details.copyBibtex"))
                } icon: {
                    Image(systemName: "doc.on.doc")
                }
            }
            Button(role: .destructive, action: actions.remove) {
                Label {
                    Text(verbatim: DesignSystemStrings.removeFromLibrary)
                } icon: {
                    Image(systemName: "trash")
                }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
        }
        .accessibilityLabel(Text(verbatim: L10n.string("details.moreOptions")))
    }
}
