import Foundation
import HashiyaData
import HashiyaDesignSystem
import HashiyaDiagnostics
import HashiyaModel
import Observation
import PDFKit

/// The reader of a saved paper's PDF, pushed on a tab's navigation stack from Details.
public struct ReaderRoute: Hashable, Codable, Sendable {
    public var openAlexID: String

    public init(openAlexID: String) {
        self.openAlexID = openAlexID
    }
}

public enum ReaderState: Equatable, Sendable {
    case loading
    /// The document is open; `startPage` is where it opened (zero-based).
    case ready(pageCount: Int, startPage: Int)
    /// PDFKit can't open the file: damaged, not a PDF, or locked with a password.
    case cantOpen
}

/// A banner on the reader. "Couldn't save your notes" isn't one: it shows while the notes editor's save failed.
public enum ReaderMessage: Equatable, Sendable {
    case notPDF, tooLarge, attachFailed
}

public enum ReaderExit: Equatable, Sendable {
    /// Back, after the typed notes saved.
    case back
    /// There is nothing to read any more: no PDF, or it was removed.
    case closed
}

/// A paper's PDF and its notes. Opens on the last page read, saves the page one second after it stops changing and at
/// once when the screen goes away. Leaving saves typed notes first; if that fails, the reader stays with Retry.
@Observable
@MainActor
public final class ReaderViewModel {
    public static let pageSaveDelay: Duration = .seconds(1)

    public let openAlexID: String
    public private(set) var title = ""
    public private(set) var state: ReaderState = .loading
    /// The open document while `state` is `.ready`.
    public private(set) var document: PDFDocument?
    /// The stored file, for Share; set even when PDFKit can't open it.
    public private(set) var fileURL: URL?
    /// The page on screen, zero-based.
    public private(set) var currentPage = 0
    public var message: ReaderMessage?
    public private(set) var exit: ReaderExit?
    public var showingNotes = false
    /// The paper's notes, the same editor as on Details.
    public let notes: NotesEditor

    @ObservationIgnored private let pdfs: any PdfRepository
    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let pendingWrites: PendingWrites
    @ObservationIgnored private let sleep: @Sendable (Duration) async throws -> Void
    /// The page last read from or written to the database.
    @ObservationIgnored private var savedPage: Int?
    @ObservationIgnored private var pageSave: Task<Void, Never>?
    /// The last page write; the next one runs after it, so pages are written in order.
    @ObservationIgnored private var lastPageWrite: Task<Void, Never>?
    /// Opening runs once, whichever `start()` comes first; later calls wait for it.
    @ObservationIgnored private var opening: Task<Void, Never>?
    /// The current `start()`'s follower of the stored PDF.
    @ObservationIgnored private var follower: Task<Void, Never>?
    @ObservationIgnored private let diagnostics: Diagnostics
    /// The PDF opened once; reopening it (a replaced file) isn't counted again.
    @ObservationIgnored private var openCounted = false

    public init(
        openAlexID: String,
        pdfs: any PdfRepository,
        library: any LibraryRepository,
        notes: NotesEditor,
        pendingWrites: PendingWrites,
        diagnostics: Diagnostics = .none,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        self.openAlexID = openAlexID
        self.pdfs = pdfs
        self.library = library
        self.notes = notes
        self.pendingWrites = pendingWrites
        self.diagnostics = diagnostics
        self.sleep = sleep
    }

    /// Opens the stored PDF once, then follows it: a PDF removed elsewhere (Details, Settings, the paper's removal)
    /// closes the reader. Runs for as long as the screen's `.task`; a `.task` that is cancelled and runs again (the
    /// screen came back) follows the PDF again without opening it or reading the notes again.
    public func start() async {
        if opening == nil {
            opening = Task { await self.open() }
        }
        await opening?.value
        guard !Task.isCancelled, exit == nil else { return }
        follower?.cancel()
        let run = Task { await self.follow() }
        follower = run
        await withTaskCancellationHandler {
            await run.value
        } onCancel: {
            run.cancel()
        }
    }

    /// The page on screen changed (PDFKit's page-changed notification).
    public func onPageChanged(_ page: Int) {
        currentPage = page
        pageSave?.cancel()
        let sleep = sleep
        pageSave = Task { [weak self] in
            guard (try? await sleep(Self.pageSaveDelay)) != nil else { return }
            // Cancelled after the wait ended: Back or leaving already saved a newer page.
            guard !Task.isCancelled else { return }
            self?.savePage(page)
        }
    }

    /// The screen went away or the app left the foreground: write the notes and the page now.
    public func onDisappear() {
        notes.flush()
        flushPage()
    }

    /// Back: leaves only once typed notes are saved, so Details reads them.
    public func back() async {
        guard await notes.saveNow() else { return }
        flushPage()
        if exit == nil { exit = .back }
    }

    /// The toolbar's Notes. Notes that couldn't be read are read again, so the sheet isn't stuck loading.
    public func showNotes() {
        showingNotes = true
        if notes.notesLoad == .failed {
            Task { await notes.retryLoad() }
        }
    }

    /// The Notes sheet closed (its close button, or swiped down): write what was typed without waiting for the pause.
    /// The write is tracked on the shared `PendingWrites` at once, so Details, which drains them before it reads the
    /// notes again, sees it even if it reappears before this screen's `onDisappear`.
    public func notesClosed() {
        showingNotes = false
        notes.flush()
    }

    /// Replace PDF on the can't-open screen, with a file from `.fileImporter`.
    public func replace(with url: URL) async {
        switch await pdfs.attach(openAlexID: openAlexID, from: url) {
        case .done:
            guard let stored = await pdfs.pdfFile(openAlexID: openAlexID) else { return close() }
            savedPage = 0
            open(stored, page: 0)
            countOpened(source: .attached)
        case .notPDF: message = .notPDF
        case .tooLarge: message = .tooLarge
        case .unreadable: message = .attachFailed
        }
    }

    /// Remove PDF on the can't-open screen. Typed notes are saved first, as on Back; the reader closes either way.
    public func removePdf() async {
        guard await notes.saveNow() else { return }
        try? await pdfs.remove(openAlexID: openAlexID)
        close()
    }

    /// The title, the stored PDF on its last page, then the notes. No PDF closes the reader.
    private func open() async {
        for await paper in library.observePaper(openAlexID: openAlexID) {
            title = paper.map { PaperFormat.title($0.paper) } ?? ""
            break
        }
        var stored: PaperPdf?
        for await pdf in pdfs.observePdf(openAlexID: openAlexID) {
            stored = pdf
            break
        }
        guard let stored, let url = await pdfs.pdfFile(openAlexID: openAlexID) else { return close() }
        savedPage = stored.lastPage
        open(url, page: stored.lastPage)
        countOpened(source: stored.source == .attached ? .attached : .downloaded)
        await notes.load()
    }

    /// Closes the reader once the PDF is gone. Its first value is the current one.
    private func follow() async {
        for await pdf in pdfs.observePdf(openAlexID: openAlexID) where pdf == nil {
            return close()
        }
    }

    /// Counts the first PDF that opens, not one PDFKit can't read.
    private func countOpened(source: PdfOrigin) {
        guard !openCounted, case .ready = state else { return }
        openCounted = true
        diagnostics.analytics.log(.pdfOpened(source: source))
    }

    private func open(_ url: URL, page: Int) {
        fileURL = url
        guard let document = PDFDocument(url: url), !document.isLocked, document.pageCount > 0 else {
            document = nil
            state = .cantOpen
            return
        }
        self.document = document
        let start = min(max(page, 0), document.pageCount - 1)
        currentPage = start
        state = .ready(pageCount: document.pageCount, startPage: start)
    }

    private func savePage(_ page: Int) {
        guard page != savedPage else { return }
        savedPage = page
        writePage(page)
    }

    private func flushPage() {
        pageSave?.cancel()
        pageSave = nil
        guard case .ready = state, currentPage != savedPage else { return }
        savePage(currentPage)
    }

    /// Writes the page after the previous write, tracked on `PendingWrites` from this turn on, so the app waits for it
    /// before it suspends the database in the background.
    private func writePage(_ page: Int) {
        let previous = lastPageWrite
        let task = Task { [pdfs, openAlexID] in
            _ = await previous?.value
            try? await pdfs.setLastPage(openAlexID: openAlexID, page: page)
        }
        lastPageWrite = task
        pendingWrites.track(task)
    }

    /// Nothing left to read (the PDF is gone). Typed notes are written first, so Details, reappearing, reads them.
    private func close() {
        notes.flush()
        if exit == nil { exit = .closed }
    }
}
