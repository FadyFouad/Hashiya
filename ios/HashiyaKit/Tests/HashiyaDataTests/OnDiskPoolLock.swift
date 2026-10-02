import HashiyaDatabase

/// GRDB's suspend notification reaches every on-disk pool in the process, so tests that suspend the database and tests
/// that write through an on-disk pool take turns through this lock.
private actor OnDiskPoolLock {
    static let shared = OnDiskPoolLock()

    private var busy = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    func acquire() async {
        while busy {
            await withCheckedContinuation { waiters.append($0) }
        }
        busy = true
    }

    func release() {
        busy = false
        if !waiters.isEmpty { waiters.removeFirst().resume() }
    }
}

/// Runs `body` while no other test uses an on-disk pool, then resumes the database, whatever `body` did.
func withOnDiskPools(_ body: () async throws -> Void) async throws {
    await OnDiskPoolLock.shared.acquire()
    do {
        try await body()
    } catch {
        HashiyaDatabase.resume()
        await OnDiskPoolLock.shared.release()
        throw error
    }
    HashiyaDatabase.resume()
    await OnDiskPoolLock.shared.release()
}
