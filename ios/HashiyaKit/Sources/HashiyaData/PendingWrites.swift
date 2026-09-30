/// Writes that must finish before the app suspends the shared database (`SharedLibraryDatabase.suspend()`), which
/// would refuse them. The Details screen tracks its note writes here; the app waits for `drained()` when it moves
/// to the background.
@MainActor
public final class PendingWrites {
    private var running = 0
    private var waiters: [CheckedContinuation<Void, Never>] = []

    public init() {}

    /// True when no tracked write is running.
    public var isIdle: Bool { running == 0 }

    /// Tracks `task` until it ends. Registering is synchronous, so a write started in the same main-actor turn as
    /// a later `drained()` call is always waited for.
    public func track<T: Sendable>(_ task: Task<T, Never>) {
        running += 1
        Task {
            _ = await task.value
            running -= 1
            guard running == 0 else { return }
            let waiting = waiters
            waiters = []
            waiting.forEach { $0.resume() }
        }
    }

    /// Returns once every tracked write has ended, including writes tracked while it waited.
    public func drained() async {
        // A write can be tracked after the last one ends but before this resumes: wait again for it.
        while running > 0 {
            await withCheckedContinuation { waiters.append($0) }
        }
    }
}
