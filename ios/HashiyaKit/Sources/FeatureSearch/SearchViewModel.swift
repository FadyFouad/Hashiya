import Foundation
import HashiyaData
import HashiyaModel
import Observation
import os

public enum SearchPhase: Equatable, Sendable {
    case idle, loading, results, empty
    case failed(SearchError)
}

public enum AppendState: Equatable, Sendable {
    case idle, loading, endReached
    case failed(SearchError)
}

public enum SearchMessage: Equatable, Sendable {
    case saveFailed, removeFailed
}

/// ID mode: the text is a DOI, an arXiv ID or a link.
public enum LookupState: Equatable, Sendable {
    case looking(PaperIdentifier)
    case found(Paper)
    /// `searchTitle`: arXiv's title, offered as a keyword search.
    case notFound(PaperIdentifier, searchTitle: String?)
    case failed(SearchError)
    /// A link with no DOI or arXiv ID in it: nothing is requested.
    case noIDInLink
}

@Observable
@MainActor
public final class SearchViewModel {
    /// Exactly what is in the search field.
    public private(set) var text = ""
    /// The chips; `query.text` mirrors the submitted text.
    public private(set) var query = SearchQuery(text: "")
    public internal(set) var phase: SearchPhase = .idle
    /// Unique by OpenAlex ID, in arrival order.
    public internal(set) var papers: [Paper] = []
    /// Nil until the first page arrives.
    public internal(set) var totalCount: Int64?
    public internal(set) var append: AppendState = .idle
    public internal(set) var savedIDs: Set<String> = []
    /// Non-nil in ID mode, which replaces the keyword results.
    public private(set) var lookup: LookupState?
    /// Set by `startFresh(focus: true)`; the view activates the field and calls `focusHandled()`.
    public private(set) var focusRequested = false
    /// The paper in the preview sheet.
    public var selectedPaper: Paper?
    public var message: SearchMessage?

    @ObservationIgnored private let repository: any SearchRepository
    @ObservationIgnored private let lookupRepository: any PaperLookupRepository
    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private var activeQuery: SearchQuery?
    @ObservationIgnored private var nextCursor: String?
    @ObservationIgnored private var seenIDs: Set<String> = []
    @ObservationIgnored private var searchTask: Task<Void, Never>?
    /// Cancelled searches still finishing, by ID; each is dropped when it finishes. Only tests wait for them.
    @ObservationIgnored private var supersededTasks: [UUID: Task<Void, Never>] = [:]
    @ObservationIgnored private var lookupTask: Task<Void, Never>?
    /// The identifier of the current lookup, while in ID mode.
    @ObservationIgnored private var lookupIdentifier: PaperIdentifier?
    @ObservationIgnored private var hasRestored = false
    @ObservationIgnored private let observations = TaskBag()

    public init(
        repository: any SearchRepository,
        lookup: any PaperLookupRepository,
        library: any LibraryRepository,
        preferences: any UserPreferencesRepository
    ) {
        self.repository = repository
        self.lookupRepository = lookup
        self.library = library

        observations.add(Task { [weak self] in
            for await ids in library.observeSavedIDs() {
                guard let self else { return }
                self.savedIDs = ids
            }
        })
        observations.add(Task { [weak self] in
            var isFirst = true
            for await _ in preferences.userAPIKeyUpdates() {
                guard let self else { return }
                if isFirst {
                    isFirst = false
                } else {
                    self.reloadAfterKeyChange()
                }
            }
        })
    }

    // MARK: Text and chips

    /// The field changed. Typing never searches, so every OpenAlex request is one the user asked for (the Search key,
    /// a suggestion, a shared link); a blank field goes idle at once.
    public func updateText(_ newText: String) {
        guard newText != text else { return }
        text = newText
        if newText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            submit("")
        }
    }

    /// The keyboard's Search key: submit the text; the same text again after an error retries it.
    public func submitNow() {
        if case .failed = phase, activeQuery?.text == keywordText(text) {
            retry()
            return
        }
        submit(text)
    }

    /// A suggestion chip: fill the field and submit at once.
    public func applySuggestion(_ suggestion: String) {
        text = suggestion
        submit(suggestion)
    }

    /// Not found's "Search for …": like a suggestion, with the full title.
    public func searchTitle(_ title: String) {
        applySuggestion(title)
    }

    /// The Library's Add paper: stop everything, clear the field and the chips, and ask for the keyboard.
    public func startFresh(focus: Bool) {
        stopLookup()
        stopKeywordSearch()
        text = ""
        query = SearchQuery(text: "")
        focusRequested = focus
    }

    /// The view has activated the field.
    public func focusHandled() {
        focusRequested = false
    }

    public func setSort(_ sort: SearchSort) {
        query.sort = sort
        chipsChanged()
    }

    public func setYears(_ years: YearFilter) {
        query.years = years
        chipsChanged()
    }

    public func setOpenAccessOnly(_ openAccessOnly: Bool) {
        query.openAccessOnly = openAccessOnly
        chipsChanged()
    }

    /// Resets the year and open-access filters; keeps the sort.
    public func clearFilters() {
        query.years = .anyTime
        query.openAccessOnly = false
        chipsChanged()
    }

    /// Restores the field and chips saved with the scene, once, and submits the text at once.
    /// Nothing saved (blank text, default chips) changes nothing.
    public func restore(text restoredText: String, query restoredQuery: SearchQuery) {
        guard !hasRestored else { return }
        hasRestored = true
        guard !restoredText.isEmpty || restoredQuery != SearchQuery(text: "") else { return }
        text = restoredText
        query = restoredQuery
        submit(restoredText)
    }

    // MARK: Paging

    /// Re-runs the lookup in ID mode, else the first page after a first-page error.
    public func retry() {
        if let identifier = lookupIdentifier {
            runLookup(identifier)
        } else {
            loadFirstPage()
        }
    }

    /// The last row appeared: load the next page if there is one and nothing is loading.
    public func loadMore() {
        guard phase == .results, append == .idle, nextCursor != nil else { return }
        loadNextPage()
    }

    /// The footer's Retry after a failed append: requests the same cursor again.
    public func retryAppend() {
        guard case .failed = append, nextCursor != nil else { return }
        loadNextPage()
    }

    // MARK: Library

    public func isSaved(_ paper: Paper) -> Bool {
        savedIDs.contains(paper.openAlexID)
    }

    /// Removes the paper when it is in the library, else saves it.
    public func toggleSave(_ paper: Paper) async {
        if isSaved(paper) {
            do {
                _ = try await library.remove(openAlexID: paper.openAlexID)
            } catch {
                message = .removeFailed
            }
        } else {
            do {
                try await library.save(paper)
            } catch {
                message = .saveFailed
            }
        }
    }

    // MARK: Private

    /// An identifier or a link switches to ID mode; anything else is a keyword query without Arabic marks
    /// (a query of only marks is idle).
    private func submit(_ submitted: String) {
        let trimmed = submitted.trimmingCharacters(in: .whitespacesAndNewlines)
        if let identifier = parsePaperIdentifier(submitted) {
            query.text = trimmed
            stopKeywordSearch()
            startLookup(identifier)
            return
        }
        if looksLikeLink(submitted) {
            query.text = trimmed
            stopKeywordSearch()
            stopLookup()
            lookup = .noIDInLink
            return
        }
        stopLookup()
        let keywords = keywordText(submitted)
        query.text = keywords
        guard !keywords.isEmpty else {
            stopKeywordSearch()
            return
        }
        activate(query)
    }

    /// The keyword query for the text: trimmed, without Arabic marks.
    private func keywordText(_ text: String) -> String {
        withoutArabicMarks(text.trimmingCharacters(in: .whitespacesAndNewlines)).trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private func stopKeywordSearch() {
        supersede(searchTask)
        activeQuery = nil
        resetResults()
        phase = .idle
    }

    private func stopLookup() {
        lookupTask?.cancel()
        lookupIdentifier = nil
        lookup = nil
    }

    /// The same identifier again (e.g. with a version or a prefix) keeps its result unless it failed.
    private func startLookup(_ identifier: PaperIdentifier) {
        if identifier == lookupIdentifier, let lookup, !lookup.isFailure { return }
        runLookup(identifier)
    }

    private func runLookup(_ identifier: PaperIdentifier) {
        lookupTask?.cancel()
        lookupIdentifier = identifier
        lookup = .looking(identifier)
        lookupTask = Task { [weak self, lookupRepository] in
            // A lookup replaced before this task started never reaches the network.
            guard !Task.isCancelled else { return }
            let result = await lookupRepository.lookup(identifier)
            guard let self, !Task.isCancelled else { return }
            self.lookup = switch result {
            case let .found(paper): .found(paper)
            case let .notFound(arxivTitle): .notFound(identifier, searchTitle: arxivTitle)
            case let .failed(error): .failed(error)
            }
        }
    }

    private func chipsChanged() {
        guard activeQuery != nil else { return }
        activate(query)
    }

    private func activate(_ newQuery: SearchQuery) {
        guard newQuery != activeQuery else { return }
        activeQuery = newQuery
        loadFirstPage()
    }

    private func reloadAfterKeyChange() {
        if let identifier = lookupIdentifier {
            runLookup(identifier)
        } else if activeQuery != nil {
            loadFirstPage()
        }
    }

    private func resetResults() {
        papers = []
        totalCount = nil
        seenIDs = []
        nextCursor = nil
        append = .idle
    }

    private func loadFirstPage() {
        guard let active = activeQuery else { return }
        supersede(searchTask)
        resetResults()
        phase = .loading
        searchTask = Task { [weak self, repository] in
            // A query replaced before this task started never reaches the network.
            guard !Task.isCancelled else { return }
            do {
                let page = try await repository.searchPage(active, cursor: nil)
                guard let self, !Task.isCancelled else { return }
                self.totalCount = page.totalCount
                self.add(page)
                self.phase = self.papers.isEmpty ? .empty : .results
            } catch is CancellationError {
                return
            } catch {
                guard let self, !Task.isCancelled else { return }
                self.phase = .failed(error as? SearchError ?? .unexpected)
            }
        }
    }

    private func supersede(_ task: Task<Void, Never>?) {
        guard let task else { return }
        task.cancel()
        let id = UUID()
        supersededTasks[id] = task
        Task { [weak self] in
            await task.value
            self?.supersededTasks[id] = nil
        }
    }

    /// Pages made only of papers already shown are skipped this many times in a row.
    private static let maxDuplicatePages = 3

    /// `duplicatePages` counts the all-duplicate pages just skipped for this scroll.
    private func loadNextPage(duplicatePages: Int = 0) {
        guard let active = activeQuery, let cursor = nextCursor else { return }
        append = .loading
        searchTask = Task { [weak self, repository] in
            guard !Task.isCancelled else { return }
            do {
                let page = try await repository.searchPage(active, cursor: cursor)
                guard let self, !Task.isCancelled else { return }
                let addedAny = self.add(page)
                // The last row stays the same when a page adds nothing, so keep going here.
                if !addedAny, self.nextCursor != nil, duplicatePages + 1 < Self.maxDuplicatePages {
                    self.loadNextPage(duplicatePages: duplicatePages + 1)
                }
            } catch is CancellationError {
                return
            } catch {
                guard let self, !Task.isCancelled else { return }
                self.append = .failed(error as? SearchError ?? .unexpected)
            }
        }
    }

    /// Appends the page's papers that were not shown yet for this query.
    @discardableResult
    private func add(_ page: SearchPage) -> Bool {
        let new = page.papers.filter { seenIDs.insert($0.openAlexID).inserted }
        papers.append(contentsOf: new)
        nextCursor = page.nextCursor
        append = page.nextCursor == nil ? .endReached : .idle
        return !new.isEmpty
    }

    // MARK: Tests

    /// Superseded searches still tracked.
    var supersededSearchCount: Int { supersededTasks.count }

    /// Waits for the search and the lookup in flight, including work they start.
    func waitForPendingWork() async {
        var finished: Set<UUID> = []
        while true {
            let search = searchTask
            let lookup = lookupTask
            let superseded = supersededTasks.filter { !finished.contains($0.key) }
            await search?.value
            await lookup?.value
            for (id, task) in superseded {
                await task.value
                finished.insert(id)
            }
            if searchTask == search, lookupTask == lookup,
               supersededTasks.keys.allSatisfy(finished.contains) { return }
        }
    }
}

extension LookupState {
    fileprivate var isFailure: Bool {
        if case .failed = self { true } else { false }
    }
}
