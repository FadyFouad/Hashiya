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

    /// A write tracked after the last one ended but before the drain resumes is waited for too. Main-actor jobs
    /// can resume in either order here, so the case repeats until the late write lands in between.
    @Test func drainedWaitsForAWriteStartedWhileItResumes() async {
        for _ in 0..<20 {
            let writes = PendingWrites()
            let gate = AsyncGate()
            let first = Task { await gate.wait(); return true }
            writes.track(first)
            Task {
                _ = await first.value
                // After the first write's own watcher has run and resumed the drain.
                await Task.yield()
                writes.track(Task { try? await Task.sleep(for: .milliseconds(20)); return true })
            }
            let drained = Task { await writes.drained(); return writes.isIdle }
            try? await Task.sleep(for: .milliseconds(10))

            gate.open()

            #expect(await drained.value)
        }
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
