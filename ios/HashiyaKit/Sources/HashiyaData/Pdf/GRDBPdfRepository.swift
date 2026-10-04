import Foundation
import HashiyaDatabase
import HashiyaModel
import HashiyaNetwork
import os

/// The PDF repository on the shared database, mirroring Android's `RoomPdfRepository`.
public final class GRDBPdfRepository: PdfRepository {
    private struct Job {
        let token: UUID
        /// Nil for the moment between `download` registering the job and starting its task.
        var task: Task<Void, Never>?
        /// A cancel that arrived while `task` was still nil; the task is cancelled as soon as it is set.
        var cancelled = false
    }

    private struct State {
        var jobs: [String: Job] = [:]
        var downloads: [String: DownloadState] = [:]
        var subscribers: [UUID: (openAlexID: String, continuation: AsyncStream<DownloadState?>.Continuation)] = [:]
        /// Downloads and attaches that may still write a file or a row.
        var activeStores = 0
        var storesFinishedWaiters: [CheckedContinuation<Void, Never>] = []
        /// Downloads that may still write the paper's file or row, by token, with the replacements waiting for them.
        var writing: [UUID: [CheckedContinuation<Void, Never>]] = [:]
    }

    private let store: PaperStore
    private let files: PdfFileStore
    private let downloader: any PdfDownloading
    private let pdfLinks: any OpenAlexPdfLinksService
    private let background: any BackgroundTimeGranting
    private let now: @Sendable () -> Int64
    private let maxBytes: Int64
    private let state = OSAllocatedUnfairLock<State>(uncheckedState: State())
    private let gate = StoreGate()

    /// - Parameter now: epoch milliseconds.
    public init(
        store: PaperStore,
        files: PdfFileStore,
        downloader: any PdfDownloading,
        pdfLinks: any OpenAlexPdfLinksService = NoPdfLinks(),
        background: any BackgroundTimeGranting = NoBackgroundTime(),
        now: @escaping @Sendable () -> Int64 = { Int64((Date().timeIntervalSince1970 * 1000).rounded()) },
        maxBytes: Int64 = PdfFileStore.maxPdfBytes
    ) {
        self.store = store
        self.files = files
        self.downloader = downloader
        self.pdfLinks = pdfLinks
        self.background = background
        self.now = now
        self.maxBytes = maxBytes
    }

    public func observePdf(openAlexID: String) -> AsyncStream<PaperPdf?> {
        store.observePdf(openAlexID: openAlexID).mapped { $0?.pdf }
    }

    public func observeDownload(openAlexID: String) -> AsyncStream<DownloadState?> {
        let id = UUID()
        return AsyncStream { continuation in
            // The first value is yielded under the lock so no newer state can overtake it. This runs while the stream is
            // being built, before anyone can await it, so the yield resumes no consumer.
            state.withLockUnchecked { state in
                state.subscribers[id] = (openAlexID, continuation)
                continuation.yield(state.downloads[openAlexID])
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.withLockUnchecked { $0.subscribers.removeValue(forKey: id) }
            }
        }
    }

    public func download(openAlexID: String) {
        let token = UUID()
        // A job that already reported a failure, or was cancelled (by the user, background expiry or the paper's
        // removal), may still be winding down: Download replaces it instead of being ignored, as on Android, where a
        // cancelled job is no longer active. Its later reports and `finish` carry the old token, so they no longer count.
        let (started, replaced) = state.withLockUnchecked { state -> (Bool, Job?) in
            let current = state.jobs[openAlexID]
            if let current {
                let failed = if case .failed = state.downloads[openAlexID] { true } else { false }
                guard failed || current.cancelled else { return (false, nil) }
            }
            state.jobs[openAlexID] = Job(token: token)
            state.writing[token] = []
            // Counted from here, so a `storesFinished()` in the same turn waits for it; `run` ends the count.
            state.activeStores += 1
            return (true, current)
        }
        replaced?.task?.cancel()
        guard started else { return }
        report(openAlexID, .running(bytes: 0, total: nil), token: token)
        let previous = replaced?.token
        let task = Task { [self] in
            await withTaskGroup(of: Bool.self) { group in
                group.addTask { await self.run(openAlexID, token: token, after: previous); return false }
                // Removing the paper while its PDF downloads cancels the download, so no file outlives the paper.
                group.addTask { await self.waitUntilRemoved(openAlexID); return true }
                // Marked cancelled like any other cancel, so a Download after Undo replaces the job winding down.
                if await group.next() == true { _ = self.cancel(openAlexID, token: token) }
                group.cancelAll()
            }
            finish(openAlexID, token: token)
        }
        // A job that is no longer current (it already finished) or was cancelled meanwhile stops at once.
        let cancelNow = state.withLockUnchecked { state -> Bool in
            guard state.jobs[openAlexID]?.token == token else { return true }
            state.jobs[openAlexID]?.task = task
            return state.jobs[openAlexID]?.cancelled ?? false
        }
        if cancelNow { task.cancel() }
    }

    public func cancelDownload(openAlexID: String) {
        cancel(openAlexID, token: nil)?.cancel()
    }

    public func attach(openAlexID: String, from url: URL) async -> AttachResult {
        beginStore()
        let result = await attachCounted(openAlexID: openAlexID, from: url)
        endStore()
        return result
    }

    private func attachCounted(openAlexID: String, from url: URL) async -> AttachResult {
        await stopDownload(openAlexID: openAlexID)
        guard let paperID = try? await store.paperID(openAlexID: openAlexID) else { return .unreadable }
        let result: AttachResult
        do {
            // The row is set inside `storing` too: a sweep between the move and the row would delete the new file.
            result = try await storing { [store, files, maxBytes, now] in
                let stored: StoreResult
                do {
                    let scoped = url.startAccessingSecurityScopedResource()
                    defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                    stored = try files.store(paperID: paperID, copying: url, maxBytes: maxBytes)
                }
                switch stored {
                case let .stored(size):
                    do {
                        // Removed before the row was set: drop the copy instead of leaving it for the startup sweep.
                        guard try await store.setPdf(paperID: paperID, source: PdfSource.attached.rawValue, size: size, addedAt: now()) else {
                            files.delete(paperID: paperID)
                            return .unreadable
                        }
                    } catch {
                        await Self.deleteUnlessRecorded(paperID: paperID, store: store, files: files)
                        return .unreadable
                    }
                    return .done
                case .notPDF:
                    return .notPDF
                case .tooLarge:
                    return .tooLarge
                }
            }
        } catch {
            return .unreadable
        }
        if result == .done { report(openAlexID, nil, token: nil) }
        return result
    }

    public func remove(openAlexID: String) async throws {
        await stopDownload(openAlexID: openAlexID)
        guard let paperID = try await store.paperID(openAlexID: openAlexID) else { return }
        try await store.clearPdf(paperID: paperID)
        files.delete(paperID: paperID)
    }

    public func setLastPage(openAlexID: String, page: Int) async throws {
        guard let paperID = try await store.paperID(openAlexID: openAlexID) else { return }
        try await store.setPdfLastPage(paperID: paperID, page: page)
    }

    public func pdfFile(openAlexID: String) async -> URL? {
        guard (await store.observePdf(openAlexID: openAlexID).firstElement()).flatMap({ $0 }) != nil,
              let paperID = try? await store.paperID(openAlexID: openAlexID) else { return nil }
        let url = files.file(paperID: paperID)
        return FileManager.default.fileExists(atPath: url.path) ? url : nil
    }

    public func storage() async throws -> PdfStorage {
        try await store.pdfStorage()
    }

    public func deleteDownloaded() async throws {
        for paperID in try await store.downloadedPdfPaperIDs() {
            try await store.clearPdf(paperID: paperID)
            files.delete(paperID: paperID)
        }
    }

    public func discardRemoved(_ removed: RemovedPaper) async {
        guard removed.pdf != nil else { return }
        // Undo put the paper back with its PDF under the same local ID: the file is still in use. A failed read keeps it too.
        guard let inUse = try? await store.pdfPaperIDs(), !inUse.contains(removed.localID) else { return }
        files.delete(paperID: removed.localID)
    }

    public func sweepOrphans() async {
        await gate.sweep { [store, files] in
            // A failed read must not count as "no PDFs": that would delete every file.
            guard let stored = try? await store.pdfPaperIDs() else { return }
            // Rows whose file is gone (a device restore, a lost file) go back to "no PDF", so Details offers it again.
            let missing = stored.filter { !FileManager.default.fileExists(atPath: files.file(paperID: $0).path) }
            for paperID in missing { try? await store.clearPdf(paperID: paperID) }
            files.sweep(keeping: stored.subtracting(missing))
            // Downloaded PDFs stored before this version are marked too; setting it again is harmless.
            for paperID in (try? await store.downloadedPdfPaperIDs()) ?? [] {
                files.setExcludedFromBackup(true, paperID: paperID)
            }
        }
    }

    public func storesFinished() async {
        await withCheckedContinuation { continuation in
            let idle = state.withLockUnchecked { state -> Bool in
                guard state.activeStores > 0 else { return true }
                state.storesFinishedWaiters.append(continuation)
                return false
            }
            if idle { continuation.resume() }
        }
    }

    // MARK: Downloading

    /// - Parameter previous: the replaced download, which may still be writing the same file and row; this one waits
    ///   until it stops (it was cancelled or has failed, so it stops soon) so the two never overlap.
    private func run(_ openAlexID: String, token: UUID, after previous: UUID?) async {
        let grant = await background.begin(name: "Download PDF") { [weak self] in
            _ = self?.cancel(openAlexID, token: token)?.cancel()
        }
        if let previous { await writesEnded(previous) }
        let outcome = await attemptDownload(openAlexID, token: token)
        endWrites(token)
        // Shown before the grant ends: ending it waits for the main actor. Until `finish`, the job is still winding down,
        // and Download after a failure or a cancel replaces it.
        report(openAlexID, outcome, token: token)
        // Nothing is written after this, so the app may suspend the database before the grant ends.
        endStore()
        await grant.end()
    }

    /// Returns once the paper stops being saved; otherwise waits until cancelled. A failed observation (its stream ends
    /// without a nil) doesn't count as a removal.
    private func waitUntilRemoved(_ openAlexID: String) async {
        for await paper in store.observePaper(openAlexID: openAlexID) where paper == nil { return }
        while !Task.isCancelled {
            try? await Task.sleep(for: .seconds(3_600))
        }
    }

    /// The state to show once the download stops: nil when it stored the file, was cancelled, or the paper is gone.
    private func attemptDownload(_ openAlexID: String, token: UUID) async -> DownloadState? {
        guard let row = (await store.observePaper(openAlexID: openAlexID).firstElement()).flatMap({ $0 }) else { return nil }
        guard let stored = row.paper.oaPDFURL?.trimmingCharacters(in: .whitespacesAndNewlines), !stored.isEmpty else {
            return .failed(.noLink)
        }
        let link = upgradeToHTTPS(stored)
        let paperID = row.paper.id
        // A link that isn't a URL is the server's failure, as on Android: the other links may still work.
        var first = Attempt.failed(.http, triesOtherLinks: true, wroteNothing: false)
        if let url = URL(string: link) {
            first = await attempt(url, paperID: paperID, openAlexID: openAlexID, token: token)
        }
        guard case let .failed(reason, triesOtherLinks, _) = first else { return nil }
        guard triesOtherLinks else { return .failed(reason) }

        // The stored link may have gone stale while OpenAlex knows other copies (e.g. arXiv): try those in turn, and keep
        // the first that gives the PDF as the paper's link. A failed lookup reports the first failure.
        report(openAlexID, .running(bytes: 0, total: nil), token: token)
        let others: [String]
        do {
            others = fallbackPDFLinks(try await pdfLinks.pdfLocations(openAlexID: openAlexID), tried: link)
        } catch {
            return Task.isCancelled ? nil : .failed(reason)
        }
        for other in others {
            guard !Task.isCancelled else { return nil }
            guard let otherURL = URL(string: other) else { continue }
            report(openAlexID, .running(bytes: 0, total: nil), token: token)
            switch await attempt(otherURL, paperID: paperID, openAlexID: openAlexID, token: token) {
            case .stored:
                // Outside this task, which may be cancelled by now and would make the write fail. A removed paper keeps
                // no row to update; a failed write keeps the old link.
                let store = store
                _ = await Task { try? await store.setOaPDFURL(paperID: paperID, url: other) }.value
                return nil
            case .stopped:
                return nil
            case let .failed(_, _, wroteNothing):
                if wroteNothing { return Task.isCancelled ? nil : .failed(reason) }
            }
        }
        return Task.isCancelled ? nil : .failed(reason)
    }

    private enum Attempt: Sendable {
        /// The PDF is stored and recorded.
        case stored
        /// Cancelled, or the paper was removed before the file was recorded: nothing to show.
        case stopped
        /// `triesOtherLinks` when the link itself was the problem (not a PDF, an error status), not the connection, the
        /// size or the device. `wroteNothing` when the file or its row couldn't be written, which another link won't change.
        case failed(DownloadFailure, triesOtherLinks: Bool, wroteNothing: Bool)
    }

    /// Downloads `url` into the paper's file and records it.
    private func attempt(_ url: URL, paperID: String, openAlexID: String, token: UUID) async -> Attempt {
        do {
            // The row is set inside `storing` too: a sweep between the move and the row would delete the new file.
            return try await storing { [self] in
                let download = try await downloader.download(url: url)
                report(openAlexID, .running(bytes: 0, total: download.expectedLength), token: token)
                let result = try await files.store(paperID: paperID, chunks: download.chunks, maxBytes: maxBytes) { bytes in
                    report(openAlexID, .running(bytes: bytes, total: download.expectedLength), token: token)
                }
                switch result {
                case let .stored(size):
                    do {
                        // Removed before the row was set: nothing points at the file. A removal after this keeps the file
                        // for Undo; discardRemoved deletes it once the removal is final.
                        if try await !store.setPdf(paperID: paperID, source: PdfSource.downloaded.rawValue, size: size, addedAt: now()) {
                            files.delete(paperID: paperID)
                            return .stopped
                        }
                        files.setExcludedFromBackup(true, paperID: paperID)
                    } catch {
                        // Also how a removal that cancels the download during the write ends: no state, as for any cancel.
                        await Self.deleteUnlessRecorded(paperID: paperID, store: store, files: files)
                        return Task.isCancelled ? .stopped : .failed(.http, triesOtherLinks: false, wroteNothing: true)
                    }
                    return .stored
                case .notPDF:
                    return .failed(.notPDF, triesOtherLinks: true, wroteNothing: false)
                case .tooLarge:
                    return .failed(.tooLarge, triesOtherLinks: false, wroteNothing: false)
                }
            }
        } catch is CancellationError {
            return .stopped
        } catch let failure as NetworkFailure {
            guard !Task.isCancelled else { return .stopped }
            return failure == .connectivity
                ? .failed(.offline, triesOtherLinks: false, wroteNothing: false)
                : .failed(.http, triesOtherLinks: true, wroteNothing: false)
        } catch {
            return Task.isCancelled ? .stopped : .failed(.http, triesOtherLinks: false, wroteNothing: true)
        }
    }

    /// Sets the paper's download state (nil clears it) and tells its observers. With a `token`, only while that download is
    /// still the paper's current one, so a cancelled download winding down never touches the next one's state.
    private func report(_ openAlexID: String, _ newState: DownloadState?, token: UUID?) {
        let targets = state.withLockUnchecked { state -> [AsyncStream<DownloadState?>.Continuation] in
            if let token, state.jobs[openAlexID]?.token != token { return [] }
            state.downloads[openAlexID] = newState
            return state.subscribers.values.filter { $0.openAlexID == openAlexID }.map(\.continuation)
        }
        // Yielded outside the lock: a consumer being cancelled takes the lock in its termination handler.
        targets.forEach { $0.yield(newState) }
    }

    private func finish(_ openAlexID: String, token: UUID) {
        state.withLockUnchecked { state in
            if state.jobs[openAlexID]?.token == token { state.jobs[openAlexID] = nil }
        }
    }

    /// Marks the paper's job (only while it is the one `token` names, when given) cancelled and returns its task for the
    /// caller to cancel outside the lock.
    private func cancel(_ openAlexID: String, token: UUID?) -> Task<Void, Never>? {
        state.withLockUnchecked { state in
            guard let job = state.jobs[openAlexID], token == nil || job.token == token else { return nil }
            state.jobs[openAlexID]?.cancelled = true
            return job.task
        }
    }

    /// Returns once the download `token` names stops writing (at once if it already has).
    private func writesEnded(_ token: UUID) async {
        await withCheckedContinuation { continuation in
            let writing = state.withLockUnchecked { state -> Bool in
                guard state.writing[token] != nil else { return false }
                state.writing[token]?.append(continuation)
                return true
            }
            if !writing { continuation.resume() }
        }
    }

    private func endWrites(_ token: UUID) {
        let waiters = state.withLockUnchecked { $0.writing.removeValue(forKey: token) } ?? []
        waiters.forEach { $0.resume() }
    }

    private func beginStore() {
        state.withLockUnchecked { $0.activeStores += 1 }
    }

    /// Ends one store's count; the last one wakes every `storesFinished()`.
    private func endStore() {
        let waiters = state.withLockUnchecked { state -> [CheckedContinuation<Void, Never>] in
            state.activeStores -= 1
            guard state.activeStores == 0 else { return [] }
            defer { state.storesFinishedWaiters = [] }
            return state.storesFinishedWaiters
        }
        waiters.forEach { $0.resume() }
    }

    /// After a row write threw: deletes the stored file unless the row points at it anyway (the write committed before a
    /// cancel landed, or the paper already had a PDF that this file replaced). A failed read keeps it for the startup sweep.
    /// The read runs outside the caller's task, which is often cancelled and would make every read fail.
    private static func deleteUnlessRecorded(paperID: String, store: PaperStore, files: PdfFileStore) async {
        let recorded = await Task { try? await store.pdfPaperIDs() }.value
        guard let recorded, !recorded.contains(paperID) else { return }
        files.delete(paperID: paperID)
    }

    private func stopDownload(openAlexID: String) async {
        guard let task = cancel(openAlexID, token: nil) else { return }
        task.cancel()
        await task.value
    }

    /// Runs `body`, which writes PDFs into this repository's folder and records them, as one store: never alongside the
    /// startup sweep, and `storesFinished()` waits for it. The backup's restore uses it.
    func withStoreGate<T: Sendable>(_ body: @Sendable () async throws -> T) async throws -> T {
        beginStore()
        defer { endStore() }
        return try await storing(body)
    }

    /// Runs `body`, which writes a PDF and records it, never while the sweep runs, so the sweep can't delete its file.
    private func storing<T: Sendable>(_ body: @Sendable () async throws -> T) async throws -> T {
        await gate.enter()
        do {
            let result = try await body()
            await gate.leave()
            return result
        } catch {
            await gate.leave()
            throw error
        }
    }
}

/// Keeps the startup sweep and PDF stores apart (Android's `sweepLock` and active-store count): stores wait while a sweep
/// runs, and a sweep waits for running stores.
private actor StoreGate {
    private var active = 0
    private var sweeping = false
    private var waitingStores: [CheckedContinuation<Void, Never>] = []
    private var waitingSweep: CheckedContinuation<Void, Never>?

    func enter() async {
        while sweeping {
            await withCheckedContinuation { waitingStores.append($0) }
        }
        active += 1
    }

    func leave() {
        active -= 1
        if active == 0, let sweep = waitingSweep {
            waitingSweep = nil
            sweep.resume()
        }
    }

    func sweep(_ body: @Sendable () async -> Void) async {
        // One sweep at a time: a second one waits like a store.
        while sweeping {
            await withCheckedContinuation { waitingStores.append($0) }
        }
        sweeping = true
        if active > 0 {
            await withCheckedContinuation { waitingSweep = $0 }
        }
        await body()
        sweeping = false
        let waiting = waitingStores
        waitingStores = []
        waiting.forEach { $0.resume() }
    }
}
