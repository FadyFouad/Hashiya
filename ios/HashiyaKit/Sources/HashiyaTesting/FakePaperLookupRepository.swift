import HashiyaData
import HashiyaModel
import os

/// Lookups with scripted results. Records every lookup, and can hold lookups until `release()`.
public final class FakePaperLookupRepository: PaperLookupRepository {
    private struct State {
        var results: [PaperIdentifier: LookupResult]
        var otherwise: LookupResult
        var lookups: [PaperIdentifier] = []
        var gate: AsyncGate?
    }

    private let state: OSAllocatedUnfairLock<State>

    /// - Parameters:
    ///   - results: the result for each identifier.
    ///   - otherwise: the result for any other identifier.
    public init(results: [PaperIdentifier: LookupResult] = [:], otherwise: LookupResult = .notFound(arxivTitle: nil)) {
        state = OSAllocatedUnfairLock(initialState: State(results: results, otherwise: otherwise))
    }

    /// Every lookup started so far, oldest first.
    public var lookups: [PaperIdentifier] { state.withLock { $0.lookups } }

    public func setResult(_ result: LookupResult, for identifier: PaperIdentifier) {
        state.withLock { $0.results[identifier] = result }
    }

    /// Lookups started from now on wait until `release()`; they answer with the result current at release.
    public func hold() {
        state.withLock { $0.gate = AsyncGate() }
    }

    /// Lets every held lookup finish.
    public func release() {
        let gate = state.withLock { state -> AsyncGate? in
            defer { state.gate = nil }
            return state.gate
        }
        gate?.open()
    }

    public func lookup(_ identifier: PaperIdentifier) async -> LookupResult {
        let gate = state.withLock { state -> AsyncGate? in
            state.lookups.append(identifier)
            return state.gate
        }
        await gate?.wait()
        return state.withLock { $0.results[identifier] ?? $0.otherwise }
    }
}
