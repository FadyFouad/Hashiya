import HashiyaData
import HashiyaTesting
import Testing

@MainActor
struct PendingWritesTests {
    @Test func isIdleWithNothingTracked() async {
        let writes = PendingWrites()
        #expect(writes.isIdle)
        await writes.drained()
    }

    @Test func drainedWaitsForARunningWrite() async {
        let writes = PendingWrites()
        let gate = AsyncGate()
        let write = Task { await gate.wait(); return true }
        writes.track(write)
        #expect(!writes.isIdle)

        let drained = Task { await writes.drained(); return true }
        try? await Task.sleep(for: .milliseconds(50))
        #expect(!writes.isIdle)

        gate.open()
        #expect(await drained.value)
        #expect(writes.isIdle)
    }

    @Test func drainedWaitsForEveryTrackedWrite() async {
        let writes = PendingWrites()
        let first = AsyncGate()
        let second = AsyncGate()
        writes.track(Task { await first.wait() })
        writes.track(Task { await second.wait() })

        first.open()
        #expect(await eventually { !writes.isIdle })
        second.open()
        await writes.drained()
        #expect(writes.isIdle)
    }
}

/// Suspends `wait()` callers until `open()`.
@MainActor
private final class AsyncGate {
    private var isOpen = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func wait() async {
        guard !isOpen else { return }
        await withCheckedContinuation { waiters.append($0) }
    }

    func open() {
        isOpen = true
        let waiting = waiters
        waiters = []
        waiting.forEach { $0.resume() }
    }
}
