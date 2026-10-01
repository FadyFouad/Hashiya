import HashiyaNetwork
import os

/// OpenAlex lookups from scripted works, recording every request.
public final class FakeOpenAlexLookupService: OpenAlexLookupService {
    public struct WorksRequest: Equatable, Sendable {
        public var filter: String
        public var perPage: Int

        public init(filter: String, perPage: Int) {
            self.filter = filter
            self.perPage = perPage
        }
    }

    private struct State {
        var works: [String: NetworkWork]
        var found: [NetworkWork]
        var workFailure: NetworkFailure?
        var worksFailure: NetworkFailure?
        var workRequests: [String] = []
        var worksRequests: [WorksRequest] = []
    }

    private let state: OSAllocatedUnfairLock<State>

    /// - Parameters:
    ///   - works: what `work(id:)` returns, by ID ("doi:…"); any other ID is not found.
    ///   - found: what every `works(filter:perPage:)` returns.
    public init(works: [String: NetworkWork] = [:], found: [NetworkWork] = []) {
        state = OSAllocatedUnfairLock(initialState: State(works: works, found: found))
    }

    public var workRequests: [String] { state.withLock { $0.workRequests } }
    public var worksRequests: [WorksRequest] { state.withLock { $0.worksRequests } }

    /// Every `work(id:)` throws `failure` (nil: answer normally).
    public func setWorkFailure(_ failure: NetworkFailure?) { state.withLock { $0.workFailure = failure } }
    /// Every `works(filter:perPage:)` throws `failure` (nil: answer normally).
    public func setWorksFailure(_ failure: NetworkFailure?) { state.withLock { $0.worksFailure = failure } }

    /// Replaces what `work(id:)` returns, by ID.
    public func setWorks(_ works: [String: NetworkWork]) { state.withLock { $0.works = works } }

    public func work(id: String) async throws -> NetworkWork? {
        try state.withLock { state in
            state.workRequests.append(id)
            if let failure = state.workFailure { throw failure }
            return state.works[id]
        }
    }

    public func works(filter: String, perPage: Int) async throws -> NetworkWorksResponse {
        try state.withLock { state in
            state.worksRequests.append(WorksRequest(filter: filter, perPage: perPage))
            if let failure = state.worksFailure { throw failure }
            return NetworkWorksResponse(meta: NetworkMeta(count: Int64(state.found.count), nextCursor: nil), results: state.found)
        }
    }
}

/// arXiv titles from a table, recording every request.
public final class FakeArxivTitleService: ArxivTitleService {
    private struct State {
        var titles: [String: String]
        var failure: NetworkFailure?
        var requests: [String] = []
    }

    private let state: OSAllocatedUnfairLock<State>

    /// - Parameter titles: arXiv's title by ID; any other ID has no paper.
    public init(titles: [String: String] = [:]) {
        state = OSAllocatedUnfairLock(initialState: State(titles: titles))
    }

    public var requests: [String] { state.withLock { $0.requests } }

    /// Every request throws `failure` (nil: answer normally).
    public func setFailure(_ failure: NetworkFailure?) { state.withLock { $0.failure = failure } }

    public func title(id: String) async throws -> String? {
        try state.withLock { state in
            state.requests.append(id)
            if let failure = state.failure { throw failure }
            return state.titles[id]
        }
    }
}
