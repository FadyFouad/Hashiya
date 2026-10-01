import Foundation
import HashiyaData
import HashiyaDesignSystem
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
    case bibtexCopied, bibtexIncomplete, copyFailed
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
    /// Copy BibTeX is running; another tap is ignored until it ends.
    public private(set) var copying = false

    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let pendingWrites: PendingWrites
    @ObservationIgnored private let collectionsRepository: any CollectionsRepository
    @ObservationIgnored private let citations: any CitationRepository
    @ObservationIgnored private let copy: @MainActor (String) -> Void
    @ObservationIgnored private let notesEditor: NotesEditor
    @ObservationIgnored private var hasStarted = false

    /// Stores its dependencies only; `start()` does the work. SwiftUI may build and discard several instances.
    /// - Parameter copy: puts text on the clipboard (the app passes `UIPasteboard.general`).
    public init(
        openAlexID: String,
        library: any LibraryRepository,
        pendingWrites: PendingWrites,
        collections: any CollectionsRepository,
        citations: any CitationRepository,
        copy: @escaping @MainActor (String) -> Void,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        self.openAlexID = openAlexID
        self.library = library
        self.pendingWrites = pendingWrites
        self.collectionsRepository = collections
        self.citations = citations
        self.copy = copy
        notesEditor = NotesEditor(openAlexID: openAlexID, library: library, pendingWrites: pendingWrites, sleep: sleep)
        notesEditor.onSaveFailed = { [weak self] in self?.message = .notesSaveFailed }
    }

    /// The paper is in and the notes were read (or failed to be).
    public var isLoaded: Bool { paper != nil && notesLoad != .loading }

    /// Reads the notes once and follows the paper, the collections and the paper's membership until the paper stops
    /// being saved or the calling task is cancelled. Later calls do nothing.
    public func start() async {
        guard !hasStarted else { return }
        hasStarted = true
        // An `async let` next to the observation task group hung (notes stuck at `.loading`), so this stays unstructured.
        let notesRead = Task { await self.notesEditor.load() }
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await self.followCollections() }
            group.addTask { await self.followMembership() }
            await self.followPaper()
            group.cancelAll()
        }
        await notesRead.value
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
            try await collectionsRepository.setMembership(collectionID: id, openAlexID: openAlexID, member: !memberIDs.contains(id))
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
                try await collectionsRepository.setMembership(collectionID: id, openAlexID: openAlexID, member: true)
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

    // MARK: Copy BibTeX

    /// Puts the paper's entry on the clipboard. iOS shows no confirmation of its own, so the banner always does.
    public func copyBibTeX() async {
        guard !copying else { return }
        copying = true
        defer { copying = false }
        do {
            guard let result = try await citations.entry(openAlexID: openAlexID) else { return }
            copy(result.bibtex)
            message = result.complete ? .bibtexCopied : .bibtexIncomplete
        } catch is CancellationError {
            return
        } catch {
            message = .copyFailed
        }
    }
}
