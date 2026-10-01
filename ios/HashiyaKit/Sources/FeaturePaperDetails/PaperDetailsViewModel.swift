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

/// Whether the stored notes have been read.
public enum NotesLoad: Equatable, Sendable {
    case loading, loaded, failed
}

/// The line beside "My notes".
public enum NotesSaveState: Equatable, Sendable {
    /// No write yet on this screen.
    case idle
    case saving, saved, failed
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

/// A saved paper, its notes and its collections. The notes are read once and then only written: no database change
/// ever replaces what is being typed. Edits save 500 ms after typing stops and on `flush()`; writes run one after
/// another and are never cancelled, and `PendingWrites` tracks each until it ends. The collections and the paper's
/// membership follow the store; a toggle never changes them ahead of it.
@Observable
@MainActor
public final class PaperDetailsViewModel {
    public static let autosaveDelay: Duration = .milliseconds(500)

    public let openAlexID: String
    /// Nil until the first value, and after the paper stops being saved.
    public private(set) var paper: LibraryPaper?
    public private(set) var notesLoad: NotesLoad = .loading
    /// The notes as typed. Fields read this once, to seed themselves.
    public private(set) var notes = PaperNotes()
    public private(set) var saveState: NotesSaveState = .idle
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
    @ObservationIgnored private let sleep: @Sendable (Duration) async throws -> Void
    /// What the database holds, as far as this screen knows: the notes read, then each successful write.
    @ObservationIgnored private var savedNotes = PaperNotes()
    @ObservationIgnored private var lastWrite: Task<Bool, Never>?
    @ObservationIgnored private var debounceTask: Task<Void, Never>?
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
        self.sleep = sleep
    }

    /// The paper is in and the notes were read (or failed to be).
    public var isLoaded: Bool { paper != nil && notesLoad != .loading }

    /// Reads the notes once and follows the paper, the collections and the paper's membership until the paper stops
    /// being saved or the calling task is cancelled. Later calls do nothing.
    public func start() async {
        guard !hasStarted else { return }
        hasStarted = true
        // An `async let` next to the observation task group hung (notes stuck at `.loading`), so this stays unstructured.
        let notesRead = Task { await self.loadNotes() }
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
        guard notesLoad == .failed else { return }
        await loadNotes()
    }

    private func loadNotes() async {
        // Reopened right after leaving: read what the previous screen was still writing, not what came before it.
        await pendingWrites.drained()
        do {
            let stored = try await library.notes(openAlexID: openAlexID)
            notes = stored
            savedNotes = stored
            notesLoad = .loaded
        } catch {
            notesLoad = .failed
        }
    }

    // MARK: Notes

    /// A field changed: keep it, and write 500 ms after typing stops.
    public func updateNote(_ section: NoteSection, _ text: String) {
        guard notesLoad == .loaded, notes[section] != text else { return }
        notes[section] = text
        debounceTask?.cancel()
        debounceTask = Task { [weak self, sleep] in
            do {
                try await sleep(Self.autosaveDelay)
            } catch {
                return
            }
            guard !Task.isCancelled, let self else { return }
            self.write(self.notes)
        }
    }

    /// Writes unsaved notes now: leaving the screen, the app going inactive or to the background, and Retry.
    public func flush() {
        debounceTask?.cancel()
        debounceTask = nil
        guard notesLoad == .loaded, notes != savedNotes else { return }
        write(notes)
    }

    /// Chains a write after the previous one, so writes run in order, and tracks it until it ends. The task holds
    /// this view model until then; nothing cancels it.
    @discardableResult
    private func write(_ value: PaperNotes) -> Task<Bool, Never> {
        let previous = lastWrite
        let task = Task {
            _ = await previous?.value
            return await self.save(value)
        }
        lastWrite = task
        pendingWrites.track(task)
        return task
    }

    /// Returns false only when the write failed.
    private func save(_ value: PaperNotes) async -> Bool {
        guard value != savedNotes else { return true }
        saveState = .saving
        do {
            try await library.saveNotes(openAlexID: openAlexID, notes: value)
            savedNotes = value
            saveState = .saved
            return true
        } catch {
            saveState = .failed
            message = .notesSaveFailed
            return false
        }
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
        debounceTask?.cancel()
        debounceTask = nil
        var saved = true
        if notesLoad == .loaded {
            saved = await write(notes).value
        }
        if saved { exit = .removed }
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
