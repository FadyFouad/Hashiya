import Foundation
import HashiyaData
import HashiyaDesignSystem
import HashiyaDiagnostics
import HashiyaModel
import Observation
import os

/// What the Library screen shows.
public enum LibraryState: Equatable, Sendable {
    /// Before the first snapshot.
    case loading
    /// Nothing saved; the search field and chips are hidden.
    case empty
    /// The selected collection has no papers at all; the search field and chips are hidden.
    case emptyCollection
    /// Papers are in view, but none match the search and the chip.
    case noMatches(LibraryFilter)
    case papers([LibraryPaper], LibraryFilter)
}

/// What the search field and the status chips show.
public struct LibraryFilter: Equatable, Sendable {
    /// The search text as typed.
    public var query: String
    /// The selected chip; nil is All.
    public var status: ReadingStatus?
    /// Papers matching the applied search per status, within the selected collection; all three keys are present.
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
    case collectionsUpdateFailed
    case exportFailed
    /// After the share sheet closed: some exported entries still lack their refetched details.
    case exportIncomplete
}

/// The name sheet on screen: New collection, or Rename for `collectionID`.
public struct NameSheet: Equatable, Identifiable, Sendable {
    public var mode: CollectionNameSheet.Mode
    /// The field's text when the sheet opens.
    public var initialName: String
    /// "A collection with that name already exists", under the field.
    public var error: String?
    /// The collection being renamed; nil for New collection.
    public var collectionID: Int64?

    /// Stable while the error changes, so the sheet isn't presented again.
    public var id: String { "\(mode)-\(collectionID ?? 0)" }

    public init(mode: CollectionNameSheet.Mode, initialName: String, error: String?, collectionID: Int64?) {
        self.mode = mode
        self.initialName = initialName
        self.error = error
        self.collectionID = collectionID
    }
}

/// A paper swiped out of a collection, which Undo puts back.
public struct CollectionUndo: Equatable, Sendable {
    public var collectionID: Int64
    public var collectionName: String
    public var openAlexID: String

    public init(collectionID: Int64, collectionName: String, openAlexID: String) {
        self.collectionID = collectionID
        self.collectionName = collectionName
        self.openAlexID = openAlexID
    }
}

@Observable
@MainActor
public final class LibraryViewModel {
    /// `@SceneStorage` keys, as Android's `SavedStateHandle` keys.
    public static let queryKey = "library_query"
    public static let statusKey = "library_status"
    public static let collectionKey = "library_collection"
    /// The title menu's tag and the stored value for All papers.
    public static let allPapersTag: Int64 = -1
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
    /// The latest removal from the library, which Undo can put back.
    public internal(set) var pendingUndo: RemovedPaper?
    public var message: LibraryMessage?

    /// Every collection, sorted by name, with its paper count.
    public private(set) var collections: [PaperCollection] = []
    /// The selected collection; nil is All papers.
    public private(set) var collectionID: Int64?
    /// Papers in the whole library, whatever the collection, search and chip: the title menu's All papers count.
    public private(set) var allPapersTotal = 0
    /// Papers in the current view (the collection, or the library), ignoring the search and chip.
    public private(set) var viewTotal = 0
    /// True from an export's start until its share sheet closes.
    public private(set) var exporting = false
    /// The style the last export used; the Export menu lists it first.
    public private(set) var citationStyle: CitationStyle
    public internal(set) var nameSheet: NameSheet?
    /// The collection the delete confirmation asks about.
    public var pendingDelete: PaperCollection?
    /// The latest swipe out of a collection, which Undo can put back.
    public internal(set) var pendingCollectionUndo: CollectionUndo?

    /// Tests only: called with every new state.
    @ObservationIgnored var stateObserver: ((LibraryState) -> Void)?
    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let collectionsRepository: any CollectionsRepository
    @ObservationIgnored private let citations: any CitationRepository
    @ObservationIgnored private let pdfs: any PdfRepository
    @ObservationIgnored private let exportFiles: ExportFiles
    @ObservationIgnored private let styles: CitationStyleStore
    @ObservationIgnored private let share: @MainActor (URL) async -> Bool
    @ObservationIgnored private let diagnostics: Diagnostics
    @ObservationIgnored private let sleep: @Sendable (Duration) async throws -> Void
    @ObservationIgnored private let filterObservation = TaskSlot()
    @ObservationIgnored private let collectionsObservation = TaskSlot()
    @ObservationIgnored private var debounceTask: Task<Void, Never>?
    @ObservationIgnored private var hasRestored = false
    /// False until the first collection list arrives: before it, an unknown selection isn't treated as deleted.
    @ObservationIgnored private var collectionsLoaded = false
    /// A collection just created and selected, not yet in `collections`: the fallback leaves it alone until it appears.
    @ObservationIgnored private var awaitedCollectionID: Int64?
    @ObservationIgnored private var isSubmittingName = false
    /// The shown collection's last known name, kept for the title while the list doesn't have it (right after Create).
    @ObservationIgnored private var lastCollectionName: String?

    /// - Parameter share: presents the share sheet for the exported file and returns when it closes, saying whether it was shown.
    public init(
        library: any LibraryRepository,
        collections: any CollectionsRepository,
        citations: any CitationRepository,
        pdfs: any PdfRepository,
        exportFiles: ExportFiles,
        share: @escaping @MainActor (URL) async -> Bool,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) },
        diagnostics: Diagnostics = .none,
        styles: CitationStyleStore = CitationStyleStore()
    ) {
        self.library = library
        self.collectionsRepository = collections
        self.citations = citations
        self.pdfs = pdfs
        self.exportFiles = exportFiles
        self.share = share
        self.sleep = sleep
        self.diagnostics = diagnostics
        self.styles = styles
        citationStyle = styles.style
        observeFilter()
        observeCollections()
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
        case .loading, .empty, .emptyCollection: nil
        }
    }

    /// The chip as its `@SceneStorage` value: `toRead`, `reading`, `read`, or "" for All.
    public var storedStatus: String { status?.rawValue ?? "" }

    /// The selected collection as its `@SceneStorage` value; -1 is All papers.
    public var storedCollection: Int { Int(collectionID ?? Self.allPapersTag) }

    /// A stored `library_collection` value as a selection: nil for All papers (any negative value).
    public static func collectionID(stored: Int) -> Int64? {
        stored < 0 ? nil : Int64(stored)
    }

    /// The selected collection, once the collection list has it.
    public var selectedCollection: PaperCollection? {
        collectionID.flatMap { id in collections.first { $0.id == id } }
    }

    /// The title's collection name: the shown collection's, or its last known name while the list lacks it; nil for
    /// All papers, or when the name isn't known yet (a restored selection).
    public var collectionTitle: String? {
        guard collectionID != nil else { return nil }
        return selectedCollection?.name ?? lastCollectionName
    }

    /// Export references shows when the current view has papers, whatever the search and chip.
    public var canExport: Bool { viewTotal > 0 }

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

    /// Restores the field, chip and collection saved with the scene, once; the text applies at once. `status` is
    /// `storedStatus`'s format; anything else is All. A collection that no longer exists falls back to All papers once
    /// the collection list arrives.
    public func restore(text restoredText: String, status restoredStatus: String, collectionID restoredCollection: Int64? = nil) {
        guard !hasRestored else { return }
        hasRestored = true
        let restored = ReadingStatus(rawValue: restoredStatus)
        guard !restoredText.isEmpty || restored != nil || restoredCollection != nil else { return }
        debounceTask?.cancel()
        text = restoredText
        appliedQuery = restoredText
        status = restored
        collectionID = restoredCollection.flatMap { isSelectable($0) ? $0 : nil }
        observeFilter()
    }

    private func apply(query: String) {
        guard query != appliedQuery else { return }
        appliedQuery = query
        observeFilter()
    }

    /// Replaces the observation for the applied search, chip and collection. The current state stays until the new
    /// first snapshot.
    private func observeFilter() {
        let query = appliedQuery
        let status = status
        let collectionID = collectionID
        filterObservation.replace(with: Task { [weak self, library] in
            for await snapshot in library.observeLibrary(query: query, status: status, collectionID: collectionID) {
                guard let self, !Task.isCancelled else { return }
                self.show(snapshot, collectionID: collectionID)
            }
        })
    }

    private func show(_ snapshot: LibrarySnapshot, collectionID: Int64?) {
        allPapersTotal = snapshot.allPapersTotal
        viewTotal = snapshot.libraryTotal
        let filter = LibraryFilter(query: text, status: status, counts: snapshot.counts)
        if snapshot.allPapersTotal == 0 {
            state = .empty
        } else if collectionID != nil, snapshot.libraryTotal == 0 {
            state = .emptyCollection
        } else if snapshot.papers.isEmpty {
            state = .noMatches(filter)
        } else {
            state = .papers(snapshot.papers, filter)
        }
    }

    // MARK: Collections

    /// The title menu: shows one collection, or All papers for nil. A collection deleted meanwhile shows All papers.
    public func selectCollection(_ id: Int64?) {
        let target = id.flatMap { isSelectable($0) ? $0 : nil }
        guard target != collectionID else { return }
        collectionID = target
        observeFilter()
    }

    /// Before the collection list arrives every id is selectable; after, only the listed ones.
    private func isSelectable(_ id: Int64) -> Bool {
        !collectionsLoaded || collections.contains { $0.id == id }
    }

    private func observeCollections() {
        collectionsObservation.replace(with: Task { [weak self, collectionsRepository] in
            for await list in collectionsRepository.observeCollections() {
                guard let self, !Task.isCancelled else { return }
                self.showCollections(list)
            }
        })
    }

    private func showCollections(_ list: [PaperCollection]) {
        collections = list
        collectionsLoaded = true
        lastCollectionName = selectedCollection?.name ?? lastCollectionName
        if let awaited = awaitedCollectionID, list.contains(where: { $0.id == awaited }) {
            awaitedCollectionID = nil
        }
        // Deleted from the title menu or from Details, or a restored selection that is gone: back to All papers.
        if let id = collectionID, id != awaitedCollectionID, !list.contains(where: { $0.id == id }) {
            collectionID = nil
            observeFilter()
        }
    }

    public func showNewCollection() {
        nameSheet = NameSheet(mode: .create, initialName: "", error: nil, collectionID: nil)
    }

    /// Renames the shown collection.
    public func showRename() {
        guard let collection = selectedCollection else { return }
        nameSheet = NameSheet(mode: .rename, initialName: collection.name, error: nil, collectionID: collection.id)
    }

    public func dismissNameSheet() {
        nameSheet = nil
    }

    /// The name sheet's Create or Save. A clash keeps the sheet open with its error; a new collection is then shown.
    /// While one submit runs, another does nothing.
    public func submitName(_ name: String) async {
        guard let sheet = nameSheet, !isSubmittingName else { return }
        isSubmittingName = true
        defer { isSubmittingName = false }
        // Cleared first, so the same clash again shows the error again.
        nameSheet?.error = nil
        do {
            let result: CollectionResult
            if sheet.mode == .rename, let id = sheet.collectionID {
                result = try await collectionsRepository.rename(id: id, name: name)
            } else {
                result = try await collectionsRepository.create(name: name)
            }
            switch result {
            case let .done(id):
                nameSheet = nil
                if sheet.mode == .create {
                    diagnostics.analytics.log(.collectionCreated)
                    // Not awaited when the list with it was handled before this resumed: nothing would clear it.
                    awaitedCollectionID = collections.contains { $0.id == id } ? nil : id
                    lastCollectionName = name.trimmingCharacters(in: .whitespacesAndNewlines)
                    collectionID = id
                    observeFilter()
                }
            case .nameTaken:
                // Only if the sheet is still open, so a dismissal meanwhile isn't undone.
                if nameSheet != nil {
                    nameSheet?.error = DesignSystemStrings.collectionNameTaken
                }
            case .invalidName:
                // The confirm button is disabled for invalid names, so this only happens on a race: keep the sheet.
                break
            case .notFound:
                // Renaming a collection deleted meanwhile (from Details).
                nameSheet = nil
                message = .collectionsUpdateFailed
            }
        } catch {
            nameSheet = nil
            message = .collectionsUpdateFailed
        }
    }

    /// The title menu's Delete: asks for confirmation for the shown collection.
    public func requestDelete() {
        pendingDelete = selectedCollection
    }

    /// The confirmation's Delete. The papers stay in the library; the Library shows All papers.
    public func confirmDelete(_ collection: PaperCollection) async {
        pendingDelete = nil
        do {
            try await collectionsRepository.delete(id: collection.id)
            if collectionID == collection.id {
                collectionID = nil
                observeFilter()
            }
        } catch {
            message = .collectionsUpdateFailed
        }
    }

    /// A swipe in a collection: takes the paper out of that collection only, with Undo. Does nothing when no collection
    /// is shown or the shown one was just deleted, so this never removes a paper from the library.
    public func removeFromCollection(openAlexID: String) async {
        guard let collection = selectedCollection else { return }
        do {
            try await collectionsRepository.setMembership(collectionID: collection.id, openAlexID: openAlexID, member: false)
            pendingCollectionUndo = CollectionUndo(collectionID: collection.id, collectionName: collection.name, openAlexID: openAlexID)
        } catch {
            message = .collectionsUpdateFailed
        }
    }

    /// Puts the latest swiped paper back into its collection. Dropped silently when the collection is gone.
    public func undoCollectionRemoval() async {
        guard let undo = pendingCollectionUndo else { return }
        pendingCollectionUndo = nil
        // The collection was deleted meanwhile: there is nothing to put the paper back into.
        guard collections.contains(where: { $0.id == undo.collectionID }) else { return }
        do {
            try await collectionsRepository.setMembership(collectionID: undo.collectionID, openAlexID: undo.openAlexID, member: true)
        } catch {
            // Deleted before the list caught up: dropped silently. Any other failure gets the usual message.
            var current: [PaperCollection] = []
            for await list in collectionsRepository.observeCollections() {
                current = list
                break
            }
            if current.contains(where: { $0.id == undo.collectionID }) {
                message = .collectionsUpdateFailed
            }
        }
    }

    /// The collection Undo banner timed out.
    public func collectionUndoExpired() {
        pendingCollectionUndo = nil
    }

    // MARK: Export

    /// Export references: builds every paper in the current view (ignoring the search and chip) in `style`, writes the
    /// file (`.bib` or `.rtf`), remembers the style and opens the share sheet. Busy until the share sheet closes, so another tap does nothing. "May be incomplete" shows once
    /// the sheet has closed.
    public func export(style: CitationStyle) async {
        guard !exporting else { return }
        styles.set(style)
        citationStyle = style
        exporting = true
        defer { exporting = false }
        let id = collectionID
        // nil for All papers; a collection whose name isn't known yet gets the file name's fallback.
        let name = id.map { id in collections.first { $0.id == id }?.name ?? "" }
        let file: URL
        let complete: Bool
        do {
            let result = try await citations.export(collectionID: id, style: style)
            file = try exportFiles.write(result.rtf ?? result.text, fileName: ExportFiles.fileName(collectionName: name, style: style))
            complete = result.complete
        } catch {
            message = .exportFailed
            return
        }
        guard await share(file) else {
            message = .exportFailed
            return
        }
        diagnostics.analytics.log(.export(format: style.exportFormat, withPdfs: false))
        // `share` returns once the share sheet has closed, so nothing covers the screen now.
        diagnostics.review.recordExport()
        diagnostics.review.askIfDue()
        if !complete {
            message = .exportIncomplete
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

    // MARK: Remove and Undo

    /// A swipe in All papers: removes the paper; only the latest removal can be undone.
    public func remove(_ paper: Paper) async {
        await remove(openAlexID: paper.openAlexID)
    }

    /// Remove on Details, after it saved the notes: the same as a swipe, with Undo.
    public func remove(openAlexID: String) async {
        do {
            if let removed = try await library.remove(openAlexID: openAlexID) {
                // The banner's timer restarts for the new removal without calling undoExpired, so the one it
                // replaces is final now and its PDF goes.
                let previous = pendingUndo
                pendingUndo = removed
                if let previous {
                    diagnostics.analytics.log(.paperRemoved)
                    discardPdf(of: previous)
                }
            }
        } catch {
            Self.log("remove failed")
        }
    }

    /// Puts the latest removed paper back in its place, with its status, notes, collections and cite key.
    public func undo() async {
        guard let removed = pendingUndo else { return }
        pendingUndo = nil
        do {
            try await library.restore(removed)
        } catch {
            Self.log("restore failed")
        }
    }

    /// The Undo banner timed out: the removal is final, so the paper's PDF goes too. Leaving the Library only pauses the
    /// banner's timer (it starts again when the Library shows); if the app ends first, the startup sweep deletes the file.
    public func undoExpired() {
        guard let removed = pendingUndo else { return }
        pendingUndo = nil
        diagnostics.analytics.log(.paperRemoved)
        discardPdf(of: removed)
    }

    private func discardPdf(of removed: RemovedPaper) {
        let pdfs = self.pdfs
        Task { await pdfs.discardRemoved(removed) }
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

/// The styles in the order the Export menu lists them: the remembered one first, then the rest as `apa, ieee, bibtex`.
public func exportStyles(_ remembered: CitationStyle) -> [CitationStyle] {
    [remembered] + [CitationStyle.apa, .ieee, .bibtex].filter { $0 != remembered }
}

private extension CitationStyle {
    var exportFormat: ExportFormat {
        switch self {
        case .bibtex: .bibtex
        case .apa: .apa
        case .ieee: .ieee
        }
    }
}
