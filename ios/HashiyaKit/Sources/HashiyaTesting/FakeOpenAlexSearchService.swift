import HashiyaNetwork
import os

/// Returns scripted responses in order and records every request.
public final class FakeOpenAlexSearchService: OpenAlexSearchService {
    private struct State {
        var replies: [Result<NetworkWorksResponse, any Error>]
        var requests: [WorksSearchRequest] = []
    }

    private let state: OSAllocatedUnfairLock<State>

    public init(replies: [Result<NetworkWorksResponse, any Error>] = []) {
        state = OSAllocatedUnfairLock(initialState: State(replies: replies))
    }

    public var requests: [WorksSearchRequest] {
        state.withLock { $0.requests }
    }

    public func enqueue(_ reply: Result<NetworkWorksResponse, any Error>) {
        state.withLock { $0.replies.append(reply) }
    }

    /// Throws `NetworkFailure.unknown` when nothing is scripted.
    public func searchWorks(_ request: WorksSearchRequest) async throws -> NetworkWorksResponse {
        let reply = state.withLock { state -> Result<NetworkWorksResponse, any Error> in
            state.requests.append(request)
            return state.replies.isEmpty ? .failure(NetworkFailure.unknown) : state.replies.removeFirst()
        }
        return try reply.get()
    }
}
