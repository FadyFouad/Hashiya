import Foundation
import HashiyaData
import HashiyaModel
import Observation
import os

/// What the Library screen shows.
public enum LibraryState: Equatable, Sendable {
    /// Before the first snapshot.
    case loading
    /// Nothing saved; the search field and chips are hidden.
    case empty
    /// Papers are saved, but none match the search and the chip.
    case noMatches(LibraryFilter)
    case papers([LibraryPaper], LibraryFilter)
}

/// What the search field and the status chips show.
public struct LibraryFilter: Equatable, Sendable {
    /// The search text as typed.
    public var query: String
    /// The selected chip; nil is All.
    public var status: ReadingStatus?
    /// Papers matching the applied search per status; all three keys are present.
    public var counts: [ReadingStatus: Int]

    /// The All chip's count.
    public var total: Int { counts.values.reduce(0, +) }

    public init(query: String, status: ReadingStatus?, counts: [ReadingStatus: Int]) {
        self.query = query
        self.status = status
        self.counts = counts
    }
}

public enum LibraryMessage: Equatable, Sendable {
    case statusUpdateFailed
}

@Observable
@MainActor
public final class LibraryViewModel {
    /// `@SceneStorage` keys, as Android's `SavedStateHandle` keys.
    public static let queryKey = "library_query"
    public static let statusKey = "library_status"
    public static let debounce: Duration = .milliseconds(300)

    public private(set) var state: LibraryState = .loading {
        didSet { stateObserver?(state) }
    }
    /// Exactly what is in the search field.
    public private(set) var text = ""
    /// The search the list shows: `text` after the debounce, at once on the Search key or when the text is emptied.
    public private(set) var appliedQuery = ""
    /// The selected chip; nil is All.
    public private(set) var status: ReadingStatus?
    /// The paper in the preview sheet.
    public var selectedPaperID: String?
    /// The latest removal, which Undo can put back.
    public internal(set) var pendingUndo: RemovedPaper?
    public var message: LibraryMessage?

    /// Tests only: called with every new state.
    @ObservationIgnored var stateObserver: ((LibraryState) -> Void)?
    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let sleep: @Sendable (Duration) async throws -> Void
    @ObservationIgnored private let observations = TaskBag()
    @ObservationIgnored private let filterObservation = TaskSlot()
    @ObservationIgnored private var debounceTask: Task<Void, Never>?
    @ObservationIgnored private var hasRestored = false
    /// The whole library, for the preview: a status change that moves the paper out of the chip keeps the sheet open.
    private var allPapers: [LibraryPaper] = []

    public init(
        library: any LibraryRepository,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        self.library = library
        self.sleep = sleep
        observations.add(Task { [weak self] in
            for await snapshot in library.observeLibrary(query: "", status: nil) {
                guard let self else { return }
                self.allPapers = snapshot.papers
                if let id = self.selectedPaperID, !snapshot.papers.contains(where: { $0.id == id }) {
                    self.selectedPaperID = nil
                }
            }
        })
        observeFilter()
    }

    /// False until the first snapshot arrives.
    public var isLoaded: Bool { state != .loading }

    /// The listed papers; empty unless the state is `.papers`.
    public var papers: [LibraryPaper] {
        if case let .papers(papers, _) = state { papers } else { [] }
    }

    /// The chips' counts and the typed search, in `.papers` and `.noMatches`.
    public var filter: LibraryFilter? {
        switch state {
        case let .papers(_, filter), let .noMatches(filter): filter
        case .loading, .empty: nil
        }
    }

    /// The selected paper with its current status; nil once it is gone.
    public var selectedPaper: LibraryPaper? {
        guard let id = selectedPaperID else { return nil }
        return allPapers.first { $0.id == id }
    }

    /// The chip as its `@SceneStorage` value: `toRead`, `reading`, `read`, or "" for All.
    public var storedStatus: String { status?.rawValue ?? "" }

    // MARK: Search and chips

    /// The field changed: search after the debounce; an emptied field applies at once.
    public func updateText(_ newText: String) {
        guard newText != text else { return }
        text = newText
        debounceTask?.cancel()
        if newText.isEmpty {
            apply(query: "")
            return
        }
        debounceTask = Task { [weak self, sleep] in
            do {
                try await sleep(Self.debounce)
            } catch {
                return
            }
            // Cancelled after the pause ended but before this ran (Search key, Clear): the newer text wins.
            guard !Task.isCancelled else { return }
            self?.apply(query: newText)
        }
    }

    /// The keyboard's Search key: search now instead of after the pause.
    public func submitNow() {
        debounceTask?.cancel()
        apply(query: text)
    }

    /// A chip: applies at once.
    public func setStatusFilter(_ newStatus: ReadingStatus?) {
        guard newStatus != status else { return }
        status = newStatus
        observeFilter()
    }

    /// No papers match's button.
    public func clearSearchAndFilters() {
        debounceTask?.cancel()
        text = ""
        let changed = !appliedQuery.isEmpty || status != nil
        appliedQuery = ""
        status = nil
        if changed { observeFilter() }
    }

    /// Restores the field and chip saved with the scene, once; the text applies at once. `status` is `storedStatus`'s
    /// format; anything else is All.
    public func restore(text restoredText: String, status restoredStatus: String) {
        guard !hasRestored else { return }
        hasRestored = true
        let restored = ReadingStatus(rawValue: restoredStatus)
        guard !restoredText.isEmpty || restored != nil else { return }
        debounceTask?.cancel()
        text = restoredText
        appliedQuery = restoredText
        status = restored
        observeFilter()
    }

    private func apply(query: String) {
        guard query != appliedQuery else { return }
        appliedQuery = query
        observeFilter()
    }

    /// Replaces the observation for the applied search and chip. The current state stays until the new first snapshot.
    private func observeFilter() {
        let query = appliedQuery
        let status = status
        filterObservation.replace(with: Task { [weak self, library] in
            for await snapshot in library.observeLibrary(query: query, status: status) {
                guard let self, !Task.isCancelled else { return }
                self.show(snapshot)
            }
        })
    }

    private func show(_ snapshot: LibrarySnapshot) {
        let filter = LibraryFilter(query: text, status: status, counts: snapshot.counts)
        if snapshot.libraryTotal == 0 {
            state = .empty
        } else if snapshot.papers.isEmpty {
            state = .noMatches(filter)
        } else {
            state = .papers(snapshot.papers, filter)
        }
    }

    // MARK: Status

    /// The badge's menu or the preview's selector. A failure shows a message; the stored status stays on screen.
    public func setStatus(of paper: Paper, to newStatus: ReadingStatus) async {
        do {
            try await library.setStatus(openAlexID: paper.openAlexID, status: newStatus)
        } catch {
            message = .statusUpdateFailed
        }
    }

    // MARK: Preview, remove and Undo

    public func select(_ paper: Paper) {
        selectedPaperID = paper.openAlexID
    }

    /// Closes the preview and removes the paper; only the latest removal can be undone.
    public func remove(_ paper: Paper) async {
        selectedPaperID = nil
        do {
            if let removed = try await library.remove(openAlexID: paper.openAlexID) {
                pendingUndo = removed
            }
        } catch {
            Self.log("remove failed")
        }
    }

    /// Puts the latest removed paper back in its place, with its status.
    public func undo() async {
        guard let removed = pendingUndo else { return }
        pendingUndo = nil
        do {
            try await library.restore(removed)
        } catch {
            Self.log("restore failed")
        }
    }

    /// The Undo banner timed out.
    public func undoExpired() {
        pendingUndo = nil
    }

    private static func log(_ message: StaticString) {
        #if DEBUG
        Logger(subsystem: "com.etatech.hashiya", category: "library").error("\(message)")
        #endif
    }
}

/// Holds one task at a time: a new one cancels the previous, and releasing the slot cancels the last.
private final class TaskSlot: Sendable {
    private let task = OSAllocatedUnfairLock<Task<Void, Never>?>(initialState: nil)

    func replace(with newTask: Task<Void, Never>) {
        task.withLock { current in
            current?.cancel()
            current = newTask
        }
    }

    deinit {
        task.withLock { $0?.cancel() }
    }
}
