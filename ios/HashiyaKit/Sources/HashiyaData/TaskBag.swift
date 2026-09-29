import os

/// Holds long-running tasks (such as observations) and cancels them when it is released, so an
/// object that owns a bag stops its observations when it goes away.
public final class TaskBag: Sendable {
    private let tasks = OSAllocatedUnfairLock<[Task<Void, Never>]>(initialState: [])

    public init() {}

    public func add(_ task: Task<Void, Never>) {
        tasks.withLock { $0.append(task) }
    }

    deinit {
        tasks.withLock { tasks in tasks.forEach { $0.cancel() } }
    }
}
