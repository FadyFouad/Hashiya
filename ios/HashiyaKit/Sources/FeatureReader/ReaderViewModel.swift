import Foundation
import HashiyaData
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
    @ObservationIgnored private let sleep: @Sendable (Duration) async throws -> Void
    /// The page last read from or written to the database.
    @ObservationIgnored private var savedPage: Int?
    @ObservationIgnored private var pageSave: Task<Void, Never>?
    @ObservationIgnored private var hasStarted = false

    public init(
        openAlexID: String,
        pdfs: any PdfRepository,
        library: any LibraryRepository,
        notes: NotesEditor,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        self.openAlexID = openAlexID
        self.pdfs = pdfs
        self.library = library
        self.notes = notes
        self.sleep = sleep
    }

    /// Opens the stored PDF and then follows it: a PDF removed elsewhere (Details, Settings, the paper's removal) closes
    /// the reader. Runs for as long as the screen's `.task`.
    public func start() async {
        guard !hasStarted else { return }
        hasStarted = true
        for await paper in library.observePaper(openAlexID: openAlexID) {
            title = paper?.paper.title ?? ""
            break
        }
        var opened = false
        for await pdf in pdfs.observePdf(openAlexID: openAlexID) {
            if !opened {
                opened = true
                guard let pdf, let url = await pdfs.pdfFile(openAlexID: openAlexID) else { return close() }
                savedPage = pdf.lastPage
                open(url, page: pdf.lastPage)
                await notes.load()
            } else if pdf == nil {
                return close()
            }
        }
    }

    /// The page on screen changed (PDFKit's page-changed notification).
    public func onPageChanged(_ page: Int) {
        currentPage = page
        pageSave?.cancel()
        let sleep = sleep
        pageSave = Task { [weak self] in
            guard (try? await sleep(Self.pageSaveDelay)) != nil else { return }
            await self?.savePage(page)
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

    private func savePage(_ page: Int) async {
        guard page != savedPage else { return }
        savedPage = page
        try? await pdfs.setLastPage(openAlexID: openAlexID, page: page)
    }

    private func flushPage() {
        pageSave?.cancel()
        pageSave = nil
        guard case .ready = state, currentPage != savedPage else { return }
        let page = currentPage
        savedPage = page
        Task { [pdfs, openAlexID] in try? await pdfs.setLastPage(openAlexID: openAlexID, page: page) }
    }

    /// Nothing left to read (the PDF is gone). Typed notes are written first, so Details, reappearing, reads them.
    private func close() {
        notes.flush()
        if exit == nil { exit = .closed }
    }
}
