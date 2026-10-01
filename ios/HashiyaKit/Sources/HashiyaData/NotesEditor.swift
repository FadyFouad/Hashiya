import Foundation
import HashiyaModel
import Observation

/// The autosave behind every notes editor: Details and the reader's Notes sheet. `load()` reads the stored notes once,
/// so no later database change replaces what is being typed. Edits save 500 ms after typing stops and on `flush()`;
/// writes run one after another and are never cancelled, and `PendingWrites` tracks each until it ends. The init
/// does nothing, so SwiftUI can build and discard owners freely; the owner calls `load()`.
@Observable
@MainActor
public final class NotesEditor {
    public static let saveDelay: Duration = .milliseconds(500)

    public let openAlexID: String
    public private(set) var notesLoad: NotesLoad = .loading
    /// The notes as typed. Fields read this once, to seed themselves, and again when `version` grows.
    public private(set) var notes = PaperNotes()
    public private(set) var saveState: NotesSaveState = .idle
    /// Grows when `reload()` replaces notes already on screen, so fields that seeded themselves start again.
    public private(set) var version = 0
    /// Runs after a write fails; the owner shows its banner.
    @ObservationIgnored public var onSaveFailed: @MainActor () -> Void = {}

    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let pendingWrites: PendingWrites
    @ObservationIgnored private let sleep: @Sendable (Duration) async throws -> Void
    /// What the database holds, as far as this editor knows: the notes read, then each successful write.
    @ObservationIgnored private var savedNotes = PaperNotes()
    /// Grows with each successful write, so `reload()` can tell that one ended while it read.
    @ObservationIgnored private var writesSaved = 0
    @ObservationIgnored private var lastWrite: Task<Bool, Never>?
    @ObservationIgnored private var debounceTask: Task<Void, Never>?

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

    /// Something typed here isn't stored yet.
    public var hasUnsavedChanges: Bool { notesLoad == .loaded && notes != savedNotes }

    /// Reads the stored notes. Waits first for writes still running, so a screen reopened right after leaving reads
    /// what the previous one was writing, not what came before it.
    public func load() async {
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

    /// "Couldn't load your notes" → Retry. The failure stays until a read succeeds.
    public func retryLoad() async {
        guard notesLoad == .failed else { return }
        await load()
    }

    /// A field changed: keep it, and write 500 ms after typing stops.
    public func onNoteChange(section: NoteSection, text: String) {
        guard notesLoad == .loaded, notes[section] != text else { return }
        notes[section] = text
        debounceTask?.cancel()
        debounceTask = Task { [weak self, sleep] in
            do {
                try await sleep(Self.saveDelay)
            } catch {
                return
            }
            guard !Task.isCancelled, let self else { return }
            self.write(self.notes)
        }
    }

    /// Writes unsaved notes now: leaving the screen, the app going inactive or to the background.
    public func flush() {
        debounceTask?.cancel()
        debounceTask = nil
        guard notesLoad == .loaded, notes != savedNotes else { return }
        write(notes)
    }

    /// The banner's Retry.
    public func retry() {
        flush()
    }

    /// Writes unsaved notes now and waits. Returns false only when the write failed.
    public func saveNow() async -> Bool {
        debounceTask?.cancel()
        debounceTask = nil
        guard notesLoad == .loaded else { return true }
        return await write(notes).value
    }

    /// Reads the stored notes again, for when another screen may have written them (the reader's Notes sheet), but
    /// only when nothing typed here is unsaved, so typing is never replaced.
    public func reload() async {
        guard notesLoad == .loaded, !hasUnsavedChanges else { return }
        await pendingWrites.drained()
        guard !hasUnsavedChanges else { return }
        let writesBefore = writesSaved
        guard let stored = try? await library.notes(openAlexID: openAlexID) else { return }
        // Typing that started while the read ran wins, and so does a write of it that ended meanwhile: the read
        // may have come before that write, so its answer can be older than what is stored now.
        guard !hasUnsavedChanges, writesSaved == writesBefore else { return }
        savedNotes = stored
        if stored != notes {
            notes = stored
            version += 1
        }
    }

    /// Chains a write after the previous one, so writes run in order, and tracks it until it ends. The task holds
    /// this editor until then; nothing cancels it.
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
            writesSaved += 1
            saveState = .saved
            return true
        } catch {
            saveState = .failed
            onSaveFailed()
            return false
        }
    }
}
