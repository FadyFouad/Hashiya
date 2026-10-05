import HashiyaData
import HashiyaModel
import os

/// Answers each search with `handler` and records the queries and cursors it was asked for.
public final class FakeSearchRepository: SearchRepository {
    public struct Call: Equatable, Sendable {
        public var query: SearchQuery
        public var cursor: String?

        public init(query: SearchQuery, cursor: String?) {
            self.query = query
            self.cursor = cursor
        }
    }

    public typealias Handler = @Sendable (SearchQuery, String?) async throws -> SearchPage

    private struct State {
        var handler: Handler
        var calls: [Call] = []
    }

    private let state: OSAllocatedUnfairLock<State>
    public let maxPagesPerQuery: Int

    public init(maxPagesPerQuery: Int = 1000, handler: @escaping Handler) {
        self.maxPagesPerQuery = maxPagesPerQuery
        state = OSAllocatedUnfairLock(initialState: State(handler: handler))
    }

    /// Every search returns `page`.
    public convenience init(page: SearchPage, maxPagesPerQuery: Int = 1000) {
        self.init(maxPagesPerQuery: maxPagesPerQuery) { _, _ in page }
    }

    public var calls: [Call] { state.withLock { $0.calls } }

    public func setHandler(_ handler: @escaping Handler) {
        state.withLock { $0.handler = handler }
    }

    public func searchPage(_ query: SearchQuery, cursor: String?) async throws -> SearchPage {
        let handler = state.withLock { state -> Handler in
            state.calls.append(Call(query: query, cursor: cursor))
            return state.handler
        }
        return try await handler(query, cursor)
    }
}

extension SearchPage {
    /// A page of `papers` with OpenAlex's total and the cursor of the next page.
    public static func of(_ papers: [Paper], total: Int64? = nil, next: String? = nil) -> SearchPage {
        SearchPage(papers: papers, totalCount: total ?? Int64(papers.count), nextCursor: next)
    }
}

/// A gate that suspends callers of `wait()` until `open()`.
public final class AsyncGate: Sendable {
    private struct State {
        var isOpen = false
        var waiters: [CheckedContinuation<Void, Never>] = []
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    public init() {}

    public func wait() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let open = state.withLock { state -> Bool in
                if state.isOpen { return true }
                state.waiters.append(continuation)
                return false
            }
            if open { continuation.resume() }
        }
    }

    public func open() {
        let waiters = state.withLock { state -> [CheckedContinuation<Void, Never>] in
            state.isOpen = true
            defer { state.waiters = [] }
            return state.waiters
        }
        waiters.forEach { $0.resume() }
    }
}
