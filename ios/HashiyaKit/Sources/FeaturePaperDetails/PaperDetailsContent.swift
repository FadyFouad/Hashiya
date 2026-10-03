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
    public var pdfAction: (PdfAction) -> Void = { _ in }

    public init() {}
}

/// A loaded paper's Details: the header, the status, the collections, the PDF row, Open DOI, the abstract and the
/// notes form, with the banners and the navigation bar's More options. Put it in a `NavigationStack`.
public struct PaperDetailsContent: View {
    private let paper: LibraryPaper
    private let collections: [PaperCollection]
    private let memberIDs: Set<Int64>
    private let pdf: PdfRow
    private let notes: PaperNotes?
    private let notesVersion: Int
    private let saveState: NotesSaveState
    private let message: PaperDetailsMessage?
    private let actions: PaperDetailsActions

    /// - Parameters:
    ///   - collections: every collection; the row shows those in `memberIDs`, in this order.
    ///   - pdf: the PDF row; nil shows the paper with no PDF stored and no download.
    ///   - notes: the notes to seed the fields with; nil shows "Couldn't load your notes" and no fields.
    public init(
        paper: LibraryPaper,
        collections: [PaperCollection] = [],
        memberIDs: Set<Int64> = [],
        pdf: PdfRow? = nil,
        notes: PaperNotes?,
        notesVersion: Int = 0,
        saveState: NotesSaveState,
        message: PaperDetailsMessage?,
        actions: PaperDetailsActions
    ) {
        self.paper = paper
        self.collections = collections
        self.memberIDs = memberIDs
        self.pdf = pdf ?? PdfRow(pdf: nil, download: nil, link: paper.paper.openAccessPDFURL.flatMap(URL.init(string:)))
        self.notes = notes
        self.notesVersion = notesVersion
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
                group
                    .padding(.top, 16)
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

    /// One grouped list: Collections, PDF and, when the paper has one, its DOI (which replaces Open DOI).
    private var group: some View {
        let doi = paper.paper.doi.flatMap { doi in DOILink.url(for: doi).map { (doi, $0) } }
        let count = doi == nil ? 2 : 3
        return VStack(spacing: GroupedRows.gap) {
            CollectionsRow(
                names: collections.filter { memberIDs.contains($0.id) }.map(\.name),
                shape: GroupedRows.shape(index: 0, count: count),
                action: actions.showCollections
            )
            PdfRowView(row: pdf, shape: GroupedRows.shape(index: 1, count: count), onAction: actions.pdfAction)
            if let (text, url) = doi {
                DoiRow(doi: text, shape: GroupedRows.shape(index: 2, count: count)) { actions.openURL(url) }
            }
        }
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
            NotesHeading(saveState: saveState)
            if let notes {
                NoteFields(notes: notes, version: notesVersion, onChange: actions.updateNote)
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
