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
    /// The paper in the preview sheet.
    public var selectedPaper: Paper?
    public var message: SearchMessage?

    public static let debounce: Duration = .milliseconds(300)

    @ObservationIgnored private let repository: any SearchRepository
    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let sleep: @Sendable (Duration) async throws -> Void
    @ObservationIgnored private var activeQuery: SearchQuery?
    @ObservationIgnored private var nextCursor: String?
    @ObservationIgnored private var seenIDs: Set<String> = []
    @ObservationIgnored private var debounceTask: Task<Void, Never>?
    @ObservationIgnored private var searchTask: Task<Void, Never>?
    /// Cancelled searches still finishing, by ID; each is dropped when it finishes. Only tests wait for them.
    @ObservationIgnored private var supersededTasks: [UUID: Task<Void, Never>] = [:]
    @ObservationIgnored private var hasRestored = false
    @ObservationIgnored private let observations = TaskBag()

    public init(
        repository: any SearchRepository,
        library: any LibraryRepository,
        preferences: any UserPreferencesRepository,
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        self.repository = repository
        self.library = library
        self.sleep = sleep

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

    /// The field changed: (re)start the debounce; a blank field goes idle at once.
    public func updateText(_ newText: String) {
        guard newText != text else { return }
        text = newText
        debounceTask?.cancel()
        if newText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            submit("")
            return
        }
        debounceTask = Task { [weak self, sleep] in
            do {
                try await sleep(Self.debounce)
            } catch {
                return
            }
            self?.submit(newText)
        }
    }

    /// The keyboard's Search key: submit without waiting.
    public func submitNow() {
        debounceTask?.cancel()
        submit(text)
    }

    /// A suggestion chip: fill the field and submit at once.
    public func applySuggestion(_ suggestion: String) {
        debounceTask?.cancel()
        text = suggestion
        submit(suggestion)
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
        debounceTask?.cancel()
        text = restoredText
        query = restoredQuery
        submit(restoredText)
    }

    // MARK: Paging

    /// Re-runs the first page after a first-page error.
    public func retry() {
        loadFirstPage()
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

    private func submit(_ submitted: String) {
        let trimmed = submitted.trimmingCharacters(in: .whitespacesAndNewlines)
        query.text = trimmed
        guard !trimmed.isEmpty else {
            supersede(searchTask)
            activeQuery = nil
            resetResults()
            phase = .idle
            return
        }
        activate(query)
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
        guard activeQuery != nil else { return }
        loadFirstPage()
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

    /// Waits for the debounce and the search in flight, including work they start.
    func waitForPendingWork() async {
        var finished: Set<UUID> = []
        while true {
            let debounce = debounceTask
            let search = searchTask
            let superseded = supersededTasks.filter { !finished.contains($0.key) }
            await debounce?.value
            await search?.value
            for (id, task) in superseded {
                await task.value
                finished.insert(id)
            }
            if debounceTask == debounce, searchTask == search, supersededTasks.keys.allSatisfy(finished.contains) { return }
        }
    }
}
