import os

/// A fake's state that queues stream emissions instead of yielding them under its lock. Yielding resumes the consumer's
/// task, which takes the task's status lock; cancelling a task holds that lock while it runs the stream's termination
/// handler, which takes the fake's lock. Yielding under the fake's lock can therefore deadlock against a cancel.
protocol DeferredYields {
    var pending: [@Sendable () -> Void] { get set }
}

extension OSAllocatedUnfairLock where State: DeferredYields {
    /// `withLock`, then the queued emissions, outside the lock.
    func update<R>(_ body: (inout State) throws -> R) rethrows -> R {
        let (result, pending) = try withLockUnchecked { state -> (R, [@Sendable () -> Void]) in
            let result = try body(&state)
            let pending = state.pending
            state.pending = []
            return (result, pending)
        }
        pending.forEach { $0() }
        return result
    }
}
