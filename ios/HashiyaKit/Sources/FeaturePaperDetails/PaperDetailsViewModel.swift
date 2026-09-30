import Foundation
import HashiyaData
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
}

/// Why the screen should go away: the paper stopped being saved, or the user removed it (after its notes saved).
public enum PaperDetailsExit: Equatable, Sendable {
    case closed, removed
}

/// A saved paper and its notes. The notes are read once and then only written: no database change ever replaces
/// what is being typed. Edits save 500 ms after typing stops and on `flush()`; writes run one after another and are
/// never cancelled, and `PendingWrites` tracks each until it ends.
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

    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let pendingWrites: PendingWrites
    @ObservationIgnored private let sleep: @Sendable (Duration) async throws -> Void
    /// What the database holds, as far as this screen knows: the notes read, then each successful write.
    @ObservationIgnored private var savedNotes = PaperNotes()
    @ObservationIgnored private var lastWrite: Task<Bool, Never>?
    @ObservationIgnored private var debounceTask: Task<Void, Never>?
    @ObservationIgnored private var hasStarted = false

    /// Stores its dependencies only; `start()` does the work. SwiftUI may build and discard several instances.
    public init(
        openAlexID: String,
        library: any LibraryRepository,
        pendingWrites: PendingWrites,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        self.openAlexID = openAlexID
        self.library = library
        self.pendingWrites = pendingWrites
        self.sleep = sleep
    }

    /// The paper is in and the notes were read (or failed to be).
    public var isLoaded: Bool { paper != nil && notesLoad != .loading }

    /// Reads the notes once and follows the paper until the calling task is cancelled. Later calls do nothing.
    public func start() async {
        guard !hasStarted else { return }
        hasStarted = true
        async let notesRead: Void = loadNotes()
        for await paper in library.observePaper(openAlexID: openAlexID) {
            guard let paper else {
                if exit == nil { exit = .closed }
                break
            }
            self.paper = paper
        }
        await notesRead
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
}
