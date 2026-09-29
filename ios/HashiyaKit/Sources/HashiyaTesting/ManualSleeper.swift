import os

/// A clock for tests: `sleep` suspends until `advance(by:)` moves virtual time past its deadline.
/// Pass `sleeper.sleep` where a view model takes `sleep: @Sendable (Duration) async throws -> Void`.
public final class ManualSleeper: Sendable {
    private struct Sleeper {
        let id: Int
        let deadline: Duration
        let continuation: CheckedContinuation<Void, any Error>
    }

    private struct State {
        var now: Duration = .zero
        var nextID = 0
        var sleepers: [Sleeper] = []
        var cancelledIDs: Set<Int> = []
        var waiters: [CheckedContinuation<Void, Never>] = []
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    public init() {}

    /// The number of sleeps in progress.
    public var pendingCount: Int { state.withLock { $0.sleepers.count } }

    public func sleep(_ duration: Duration) async throws {
        let id = state.withLock { state -> Int in
            state.nextID += 1
            return state.nextID
        }
        try await withTaskCancellationHandler {
            try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, any Error>) in
                let waiters = state.withLock { state -> [CheckedContinuation<Void, Never>]? in
                    if state.cancelledIDs.remove(id) != nil {
                        continuation.resume(throwing: CancellationError())
                        return nil
                    }
                    state.sleepers.append(Sleeper(id: id, deadline: state.now + duration, continuation: continuation))
                    defer { state.waiters = [] }
                    return state.waiters
                }
                waiters?.forEach { $0.resume() }
            }
        } onCancel: {
            let sleeper = state.withLock { state -> Sleeper? in
                if let index = state.sleepers.firstIndex(where: { $0.id == id }) {
                    return state.sleepers.remove(at: index)
                }
                state.cancelledIDs.insert(id)
                return nil
            }
            sleeper?.continuation.resume(throwing: CancellationError())
        }
    }

    /// Suspends until at least one sleep is in progress.
    public func waitForSleeper() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let ready = state.withLock { state -> Bool in
                if !state.sleepers.isEmpty { return true }
                state.waiters.append(continuation)
                return false
            }
            if ready { continuation.resume() }
        }
    }

    /// Moves virtual time forward and wakes every sleep whose deadline has passed.
    public func advance(by duration: Duration) {
        let due = state.withLock { state -> [Sleeper] in
            state.now += duration
            let due = state.sleepers.filter { $0.deadline <= state.now }
            state.sleepers.removeAll { $0.deadline <= state.now }
            return due
        }
        due.forEach { $0.continuation.resume() }
    }
}
