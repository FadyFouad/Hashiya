import Foundation
import HashiyaData
import HashiyaDesignSystem
import HashiyaDiagnostics
import HashiyaModel
import Observation

/// The Details screen of a saved paper, pushed on the Library's or Search's navigation stack.
public struct PaperDetailsRoute: Hashable, Codable, Sendable {
    public var openAlexID: String

    public init(openAlexID: String) {
        self.openAlexID = openAlexID
    }
}

public enum PaperDetailsMessage: Equatable, Sendable {
    case notesSaveFailed, statusUpdateFailed
    case collectionsUpdateFailed
    case apaCopied, ieeeCopied, bibtexCopied, citationIncomplete, copyFailed
    case pdfAttachNotPdf, pdfAttachTooLarge, pdfAttachFailed
}

/// What goes on the clipboard: plain text, plus HTML for the styles that have it.
public struct CopiedText: Equatable, Sendable {
    public let text: String
    public let html: String?

    public init(text: String, html: String?) {
        self.text = text
        self.html = html
    }
}

/// The styles in the order Copy lists them: the remembered one first, then the rest as `apa, ieee, bibtex`.
public func orderedStyles(_ remembered: CitationStyle) -> [CitationStyle] {
    [remembered] + [CitationStyle.apa, .ieee, .bibtex].filter { $0 != remembered }
}

/// Why the screen should go away: the paper stopped being saved, or the user removed it (after its notes saved).
public enum PaperDetailsExit: Equatable, Sendable {
    case closed, removed
}

/// A saved paper, its notes and its collections. The notes go through a `NotesEditor`: read once and then only
/// written, so no database change ever replaces what is being typed; edits save 500 ms after typing stops and on
/// `flush()`. The collections and the paper's membership follow the store; a toggle never changes them ahead of it.
@Observable
@MainActor
public final class PaperDetailsViewModel {
    public static let autosaveDelay: Duration = NotesEditor.saveDelay

    public let openAlexID: String
    /// Nil until the first value, and after the paper stops being saved.
    public private(set) var paper: LibraryPaper?
    public var notesLoad: NotesLoad { notesEditor.notesLoad }
    /// The notes as typed. Fields read this once, to seed themselves, and again when `notesVersion` grows.
    public var notes: PaperNotes { notesEditor.notes }
    public var saveState: NotesSaveState { notesEditor.saveState }
    /// Grows when the notes on screen were replaced by a reload (after the reader's Notes sheet wrote them).
    public var notesVersion: Int { notesEditor.version }
    public var message: PaperDetailsMessage?
    public private(set) var exit: PaperDetailsExit?
    /// Every collection, in the repository's order.
    public private(set) var collections: [PaperCollection] = []
    /// The collections this paper is in, as stored.
    public private(set) var memberIDs: Set<Int64> = []
    /// The checklist sheet.
    public var showingChecklist = false
    /// The New collection name sheet, over the checklist.
    public var showingNameSheet = false
    /// "A collection with that name already exists" under the name field, or nil.
    public private(set) var nameSheetError: String?
    /// A Create is running; another is ignored until it ends.
    public private(set) var creatingCollection = false
    /// A copy is running; another tap is ignored until it ends.
    public private(set) var copying = false
    /// The style Copy lists first: the last one copied.
    public private(set) var citationStyle: CitationStyle
    /// The stored PDF, as the store has it.
    public private(set) var storedPdf: PaperPdf?
    /// A running or failed download, or nil.
    public private(set) var download: DownloadState?
    /// Replace or Remove waiting for the user's answer.
    public var pdfConfirmation: PdfConfirmation?
    /// The Files picker for Attach and Replace.
    public var showingFileImporter = false
    /// The notes are saved and the screen should push the reader; `readerOpened()` resets it.
    public private(set) var openReader = false

    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let pendingWrites: PendingWrites
    @ObservationIgnored private let collectionsRepository: any CollectionsRepository
    @ObservationIgnored private let citations: any CitationRepository
    @ObservationIgnored private let pdfs: any PdfRepository
    /// The reader was pushed from here; on return, the notes are read again.
    @ObservationIgnored private var readerShown = false
    /// Read is saving the notes; another tap is ignored until it ends.
    @ObservationIgnored private var savingForReader = false
    @ObservationIgnored private let diagnostics: Diagnostics
    @ObservationIgnored private let copy: @MainActor (CopiedText) -> Void
    @ObservationIgnored private let styles: CitationStyleStore
    @ObservationIgnored private let notesEditor: NotesEditor
    /// The one read of the notes; a later `start()` waits for it instead of reading again.
    @ObservationIgnored private var notesRead: Task<Void, Never>?
    /// The current run of the followers; a new `start()` stops it first.
    @ObservationIgnored private var followers: Task<Void, Never>?

    /// Stores its dependencies only; `start()` does the work. SwiftUI may build and discard several instances.
    /// - Parameter copy: puts text on the clipboard (the app passes `UIPasteboard.general`); `styles` remembers the last style copied.
    public init(
        openAlexID: String,
        library: any LibraryRepository,
        pendingWrites: PendingWrites,
        collections: any CollectionsRepository,
        citations: any CitationRepository,
        pdfs: any PdfRepository,
        copy: @escaping @MainActor (CopiedText) -> Void,
        styles: CitationStyleStore = CitationStyleStore(),
        diagnostics: Diagnostics = .none,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        self.openAlexID = openAlexID
        self.library = library
        self.pendingWrites = pendingWrites
        self.collectionsRepository = collections
        self.citations = citations
        self.pdfs = pdfs
        self.copy = copy
        self.styles = styles
        citationStyle = styles.style
        self.diagnostics = diagnostics
        notesEditor = NotesEditor(openAlexID: openAlexID, library: library, pendingWrites: pendingWrites, diagnostics: diagnostics, sleep: sleep)
        notesEditor.onSaveFailed = { [weak self] in self?.message = .notesSaveFailed }
    }

    /// The paper is in and the notes were read (or failed to be).
    public var isLoaded: Bool { paper != nil && notesLoad != .loading }

    /// The PDF row: the stored file, a running or failed download, and the paper's open-access link.
    public var pdf: PdfRow {
        PdfRow(pdf: storedPdf, download: download, link: paper?.paper.openAccessPDFURL.flatMap(URL.init(string:)))
    }

    /// Reads the notes once and follows the paper, the collections, the paper's membership, its PDF and its download
    /// until the paper stops being saved or the calling task is cancelled. A push cancels the screen's `.task` and Back
    /// runs it again, so every call follows the store again (stopping an earlier run still going), but the notes are
    /// read only by the first: what is typed is never replaced.
    public func start() async {
        // An `async let` next to the observation task group hung (notes stuck at `.loading`), so this stays unstructured.
        if notesRead == nil { notesRead = Task { await self.notesEditor.load() } }
        followers?.cancel()
        let run = Task { await self.follow() }
        followers = run
        await withTaskCancellationHandler {
            await run.value
        } onCancel: {
            run.cancel()
        }
        await notesRead?.value
    }

    private func follow() async {
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.followCollections() }
            group.addTask { await self.followMembership() }
            group.addTask { await self.followPdf() }
            group.addTask { await self.followDownload() }
            await self.followPaper()
            group.cancelAll()
        }
    }

    private func followPaper() async {
        for await paper in library.observePaper(openAlexID: openAlexID) {
            guard let paper else {
                if exit == nil { exit = .closed }
                break
            }
            self.paper = paper
        }
    }

    private func followCollections() async {
        for await collections in collectionsRepository.observeCollections() {
            self.collections = collections
        }
    }

    private func followMembership() async {
        for await ids in collectionsRepository.observeCollectionIDs(openAlexID: openAlexID) {
            memberIDs = ids
        }
    }

    private func followPdf() async {
        for await pdf in pdfs.observePdf(openAlexID: openAlexID) {
            storedPdf = pdf
        }
    }

    private func followDownload() async {
        for await state in pdfs.observeDownload(openAlexID: openAlexID) {
            download = state
        }
    }

    /// "Couldn't load your notes" → Retry. The failure stays on screen until a read succeeds.
    public func retryLoadNotes() async {
        await notesEditor.retryLoad()
    }

    // MARK: Notes

    /// A field changed: keep it, and write 500 ms after typing stops.
    public func updateNote(_ section: NoteSection, _ text: String) {
        notesEditor.onNoteChange(section: section, text: text)
    }

    /// Writes unsaved notes now: leaving the screen, the app going inactive or to the background, and Retry.
    public func flush() {
        notesEditor.flush()
    }

    // MARK: Status and remove

    /// The selector. A failure shows a message; the selector keeps showing the stored status.
    public func setStatus(_ status: ReadingStatus) async {
        do {
            try await library.setStatus(openAlexID: openAlexID, status: status)
        } catch {
            message = .statusUpdateFailed
        }
    }

    /// Saves unsaved notes first, so Undo on the screen below restores what was just typed, then asks to leave.
    /// If that save fails, the screen stays with Couldn't save and Retry, so Undo can never bring back older notes.
    public func remove() async {
        if await notesEditor.saveNow() { exit = .removed }
    }

    // MARK: Collections

    /// Adds the paper to the collection, or takes it out. The check mark follows the store, so a failure leaves the
    /// stored state on screen.
    public func toggleCollection(_ id: Int64) async {
        do {
            let adding = !memberIDs.contains(id)
            try await collectionsRepository.setMembership(collectionID: id, openAlexID: openAlexID, member: adding)
            if adding { diagnostics.analytics.log(.paperAddedToCollection) }
        } catch {
            message = .collectionsUpdateFailed
        }
    }

    public func showNewCollection() {
        nameSheetError = nil
        showingNameSheet = true
    }

    public func dismissNameSheet() {
        showingNameSheet = false
        nameSheetError = nil
    }

    /// Creates the collection and adds the paper to it. A taken name keeps the sheet open with the error; a failure
    /// closes it and says so. A second submit while one runs is ignored.
    public func submitNewCollection(_ name: String) async {
        guard !creatingCollection else { return }
        creatingCollection = true
        defer { creatingCollection = false }
        // Cleared first, so the same clash again shows the error again.
        nameSheetError = nil
        do {
            switch try await collectionsRepository.create(name: name) {
            case .done(let id):
                dismissNameSheet()
                diagnostics.analytics.log(.collectionCreated)
                try await collectionsRepository.setMembership(collectionID: id, openAlexID: openAlexID, member: true)
                diagnostics.analytics.log(.paperAddedToCollection)
            case .nameTaken:
                nameSheetError = DesignSystemStrings.collectionNameTaken
            case .invalidName, .notFound:
                // The sheet only submits valid names, and a create never reports a missing collection.
                break
            }
        } catch {
            dismissNameSheet()
            message = .collectionsUpdateFailed
        }
    }

    // MARK: PDF

    /// One of the PDF row's actions. Returns a link for the screen to open in the browser, or nil.
    public func handle(_ action: PdfAction) -> URL? {
        switch action {
        case .read:
            Task { await readPdf() }
        case .download, .tryAgain:
            downloadPdf()
        case .cancel:
            cancelPdfDownload()
        case .attach:
            showingFileImporter = true
        case .replace:
            pdfConfirmation = .replace
        case .remove:
            pdfConfirmation = .remove
        case .openInBrowser, .openLink:
            return pdf.link
        }
        return nil
    }

    public func downloadPdf() {
        pdfs.download(openAlexID: openAlexID)
    }

    public func cancelPdfDownload() {
        pdfs.cancelDownload(openAlexID: openAlexID)
    }

    /// Replace confirmed: pick the new file.
    public func confirmReplace() {
        pdfConfirmation = nil
        showingFileImporter = true
    }

    /// Copies the picked file in, replacing any stored PDF. Says why when it isn't stored; nothing changes then.
    public func attachPdf(from url: URL) async {
        switch await pdfs.attach(openAlexID: openAlexID, from: url) {
        case .done: break
        case .notPDF: message = .pdfAttachNotPdf
        case .tooLarge: message = .pdfAttachTooLarge
        case .unreadable: message = .pdfAttachFailed
        }
    }

    /// Remove confirmed. On failure the row keeps showing the stored PDF; the spec has no message for it.
    public func removePdf() async {
        pdfConfirmation = nil
        try? await pdfs.remove(openAlexID: openAlexID)
    }

    /// Read: saves typed notes first, so the reader's Notes sheet reads them and can never overwrite them with older
    /// ones. If that save fails, the screen stays with Couldn't save and Retry.
    public func readPdf() async {
        guard !openReader, !savingForReader else { return }
        savingForReader = true
        defer { savingForReader = false }
        if await notesEditor.saveNow() { openReader = true }
    }

    /// The screen pushed the reader.
    public func readerOpened() {
        openReader = false
        readerShown = true
    }

    /// The screen appeared. After the reader, its Notes sheet may have written the notes: read them again, unless
    /// something typed here is unsaved.
    public func onReaderClosed() async {
        guard readerShown else { return }
        readerShown = false
        await notesEditor.reload()
    }

    // MARK: Copy

    /// Puts the paper's citation in `style` on the clipboard and remembers the style. iOS shows no confirmation of
    /// its own, so the banner always does.
    public func copyCitation(_ style: CitationStyle) async {
        guard !copying else { return }
        copying = true
        defer { copying = false }
        styles.set(style)
        citationStyle = style
        do {
            guard let result = try await citations.entry(openAlexID: openAlexID, style: style) else { return }
            copy(CopiedText(text: result.text, html: result.html))
            if !result.complete {
                message = .citationIncomplete
            } else {
                switch style {
                case .apa: message = .apaCopied
                case .ieee: message = .ieeeCopied
                case .bibtex: message = .bibtexCopied
                }
            }
        } catch is CancellationError {
            return
        } catch {
            message = .copyFailed
        }
    }
}
