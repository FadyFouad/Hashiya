import Foundation
import GRDB
@testable import HashiyaData
import HashiyaDatabase
import HashiyaDiagnostics
import HashiyaModel
import HashiyaNetwork
import HashiyaTesting
import os
import Testing

/// A scripted downloader: serves `body` for any URL, or throws `failure`. Records each URL; a set `gate` holds every
/// download until it opens, so a test can see one running or cancel it.
private final class ScriptedDownloader: PdfDownloading, @unchecked Sendable {
    private struct State {
        var body = GRDBPdfRepositoryTests.pdf
        var failure: NetworkFailure?
        /// Per-link bodies and failures, used before `body` and `failure`.
        var bodies: [String: Data] = [:]
        var failures: [String: NetworkFailure] = [:]
        var gate: [CheckedContinuation<Void, Never>]?
        /// When set, only this link waits for the gate.
        var gatedURL: String?
        /// Non-nil while bodies are held after their first byte: the waiting bodies.
        var bodyGate: [CheckedContinuation<Void, Never>]?
        var urls: [URL] = []
    }

    private let state = OSAllocatedUnfairLock<State>(uncheckedState: State())

    var urls: [URL] { state.withLockUnchecked { $0.urls } }
    func setBody(_ body: Data) { state.withLockUnchecked { $0.body = body } }
    func setFailure(_ failure: NetworkFailure?) { state.withLockUnchecked { $0.failure = failure } }
    func setBody(_ body: Data, for url: String) { state.withLockUnchecked { $0.bodies[url] = body } }
    func setFailure(_ failure: NetworkFailure, for url: String) { state.withLockUnchecked { $0.failures[url] = failure } }
    /// Holds only `url`'s downloads until `release()`.
    func hold(only url: String) { state.withLockUnchecked { $0.gatedURL = url; if $0.gate == nil { $0.gate = [] } } }
    func hold() { state.withLockUnchecked { if $0.gate == nil { $0.gate = [] } } }

    func release() {
        let waiting = state.withLockUnchecked { state -> [CheckedContinuation<Void, Never>] in
            defer { state.gate = nil }
            return state.gate ?? []
        }
        waiting.forEach { $0.resume() }
    }

    /// From now on each body sends its first byte, then waits until `releaseBodies()` to send the rest.
    func holdBodies() { state.withLockUnchecked { if $0.bodyGate == nil { $0.bodyGate = [] } } }

    func releaseBodies() {
        let waiting = state.withLockUnchecked { state -> [CheckedContinuation<Void, Never>] in
            defer { state.bodyGate = nil }
            return state.bodyGate ?? []
        }
        waiting.forEach { $0.resume() }
    }

    private func waitForBodies() async {
        await withCheckedContinuation { continuation in
            let held = state.withLockUnchecked { state -> Bool in
                guard state.bodyGate != nil else { return false }
                state.bodyGate?.append(continuation)
                return true
            }
            if !held { continuation.resume() }
        }
    }

    func download(url: URL) async throws -> PdfDownload {
        let held = state.withLockUnchecked { state -> Bool in
            state.urls.append(url)
            return state.gate != nil && (state.gatedURL == nil || state.gatedURL == url.absoluteString)
        }
        if held {
            await withCheckedContinuation { continuation in
                let stillHeld = state.withLockUnchecked { state -> Bool in
                    guard state.gate != nil else { return false }
                    state.gate?.append(continuation)
                    return true
                }
                if !stillHeld { continuation.resume() }
            }
        }
        try Task.checkCancellation()
        let (body, failure) = state.withLockUnchecked {
            ($0.bodies[url.absoluteString] ?? $0.body, $0.failures[url.absoluteString] ?? $0.failure)
        }
        if let failure { throw failure }
        let chunks = AsyncThrowingStream<Data, Error> { continuation in
            let task = Task {
                continuation.yield(body.prefix(1))
                await self.waitForBodies()
                continuation.yield(body.dropFirst())
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
        return PdfDownload(chunks: chunks, expectedLength: Int64(body.count))
    }
}

/// OpenAlex's locations for any work, or `failure`; `hold()` keeps every lookup waiting until `release()`.
private final class ScriptedPdfLinks: OpenAlexPdfLinksService, @unchecked Sendable {
    private struct State {
        var locations: [NetworkLocation] = []
        var failure: NetworkFailure?
        var gate: [CheckedContinuation<Void, Never>]?
        var requests: [String] = []
    }

    private let state = OSAllocatedUnfairLock<State>(uncheckedState: State())

    var requests: [String] { state.withLockUnchecked { $0.requests } }
    func setLocations(_ locations: [NetworkLocation]) { state.withLockUnchecked { $0.locations = locations } }
    func setFailure(_ failure: NetworkFailure?) { state.withLockUnchecked { $0.failure = failure } }
    func hold() { state.withLockUnchecked { if $0.gate == nil { $0.gate = [] } } }

    func release() {
        let waiting = state.withLockUnchecked { state -> [CheckedContinuation<Void, Never>] in
            defer { state.gate = nil }
            return state.gate ?? []
        }
        waiting.forEach { $0.resume() }
    }

    func pdfLocations(openAlexID: String) async throws -> [NetworkLocation] {
        let held = state.withLockUnchecked { state -> Bool in
            state.requests.append(openAlexID)
            return state.gate != nil
        }
        if held {
            await withCheckedContinuation { continuation in
                let stillHeld = state.withLockUnchecked { state -> Bool in
                    guard state.gate != nil else { return false }
                    state.gate?.append(continuation)
                    return true
                }
                if !stillHeld { continuation.resume() }
            }
        }
        try Task.checkCancellation()
        let (locations, failure) = state.withLockUnchecked { ($0.locations, $0.failure) }
        if let failure { throw failure }
        return locations
    }
}

/// Background time a test can end early, as iOS does before suspending the app. `holdEnds` keeps every `end()` waiting
/// until `releaseEnds`, so a finished download stays winding down (as `UIKitBackgroundTime.end` does while it waits for
/// the main actor).
private final class ManualBackgroundTime: BackgroundTimeGranting, @unchecked Sendable {
    private struct State {
        var handlers: [@Sendable () -> Void] = []
        var ended = 0
        var heldEnds: [CheckedContinuation<Void, Never>]?
    }

    private let state = OSAllocatedUnfairLock<State>(uncheckedState: State())

    var endCount: Int { state.withLockUnchecked { $0.ended } }
    var grants: Int { state.withLockUnchecked { $0.handlers.count } }
    var waitingEnds: Int { state.withLockUnchecked { $0.heldEnds?.count ?? 0 } }

    func begin(name: String, onExpiry: @escaping @Sendable () -> Void) async -> any BackgroundTimeToken {
        state.withLockUnchecked { $0.handlers.append(onExpiry) }
        return Token(owner: self)
    }

    func expire() {
        state.withLockUnchecked { $0.handlers }.forEach { $0() }
    }

    func holdEnds() { state.withLockUnchecked { if $0.heldEnds == nil { $0.heldEnds = [] } } }

    func releaseEnds() {
        let waiting = state.withLockUnchecked { state -> [CheckedContinuation<Void, Never>] in
            defer { state.heldEnds = nil }
            return state.heldEnds ?? []
        }
        waiting.forEach { $0.resume() }
    }

    fileprivate func end() async {
        await withCheckedContinuation { continuation in
            let held = state.withLockUnchecked { state -> Bool in
                guard state.heldEnds != nil else { return false }
                state.heldEnds?.append(continuation)
                return true
            }
            if !held { continuation.resume() }
        }
        state.withLockUnchecked { $0.ended += 1 }
    }

    private struct Token: BackgroundTimeToken {
        let owner: ManualBackgroundTime
        func end() async { await owner.end() }
    }
}

/// Mirrors Android's `RoomPdfRepositoryTest`.
struct GRDBPdfRepositoryTests {
    static let pdf = Data("%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n".utf8)
    static let html = Data("<!DOCTYPE html><html><body>Sign in to read this article</body></html>".utf8)

    private let queue: DatabaseQueue
    private let store: PaperStore
    private let library: GRDBLibraryRepository
    private let directory: URL
    private let files: PdfFileStore
    private let downloader = ScriptedDownloader()
    private let pdfLinks = ScriptedPdfLinks()
    private let background = ManualBackgroundTime()
    private let repository: GRDBPdfRepository

    init() throws {
        let clock = OSAllocatedUnfairLock(initialState: Int64(0))
        let ids = OSAllocatedUnfairLock(initialState: 0)
        queue = try HashiyaDatabase.openInMemory()
        store = PaperStore(writer: queue)
        library = GRDBLibraryRepository(
            store: store,
            now: { clock.withLock { $0 += 1; return $0 } },
            newID: { ids.withLock { $0 += 1; return "local-\($0)" } }
        )
        directory = FileManager.default.temporaryDirectory.appending(path: "pdfs-\(UUID().uuidString)", directoryHint: .isDirectory)
        files = PdfFileStore(directory: directory)
        repository = GRDBPdfRepository(
            store: store,
            files: files,
            downloader: downloader,
            pdfLinks: pdfLinks,
            background: background,
            now: { 1_000 },
            maxBytes: 10_000
        )
    }

    private func paper(_ id: String, pdfURL: String? = nil) -> Paper {
        Paper(
            openAlexID: id,
            title: "Deep nets",
            authors: [Author(name: "Jane Doe")],
            year: 2020,
            venue: "Nature",
            isOpenAccess: true,
            openAccessPDFURL: pdfURL ?? "https://arxiv.org/pdf/\(id)"
        )
    }

    /// The first value of `stream` matching `predicate`, or nil after 5 s.
    private func firstValue<T: Sendable>(_ stream: AsyncStream<T>, where predicate: @escaping @Sendable (T) -> Bool) async -> T? {
        await withTaskGroup(of: T?.self) { group in
            group.addTask {
                for await value in stream where predicate(value) { return value }
                return nil
            }
            group.addTask {
                try? await Task.sleep(for: .seconds(5))
                return nil
            }
            let first = await group.next() ?? nil
            group.cancelAll()
            return first
        }
    }

    private func awaitStored(_ openAlexID: String) async -> PaperPdf? {
        await firstValue(repository.observePdf(openAlexID: openAlexID)) { $0 != nil } ?? nil
    }

    private func awaitDownload(_ openAlexID: String, _ predicate: @escaping @Sendable (DownloadState?) -> Bool) async -> DownloadState?? {
        await firstValue(repository.observeDownload(openAlexID: openAlexID), where: predicate)
    }

    private func names() -> [String] {
        ((try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []).sorted()
    }

    private func temporaryFile(_ data: Data) throws -> URL {
        let url = FileManager.default.temporaryDirectory.appending(path: "attach-\(UUID().uuidString).pdf")
        try data.write(to: url)
        return url
    }

    @Test func downloadsTheOpenAccessPdfAndRecordsIt() async throws {
        try await library.save(paper("W1"))

        repository.download(openAlexID: "W1")

        #expect(await awaitStored("W1") == PaperPdf(source: .downloaded, sizeBytes: Int64(Self.pdf.count), addedAt: 1_000, lastPage: 0))
        #expect(downloader.urls == [URL(string: "https://arxiv.org/pdf/W1")!])
        #expect(try Data(contentsOf: #require(await repository.pdfFile(openAlexID: "W1"))) == Self.pdf)
        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(names() == ["local-1.pdf"])
    }

    @Test func anHttpLinkIsDownloadedOverHttps() async throws {
        try await library.save(paper("W1", pdfURL: "http://arxiv.org/pdf/1612.03928"))
        try await library.save(paper("W2", pdfURL: "HTTP://jsrse.edu.iq:8080/download/285/301"))

        repository.download(openAlexID: "W1")
        _ = await awaitStored("W1")
        repository.download(openAlexID: "W2")
        _ = await awaitStored("W2")

        #expect(downloader.urls.map(\.absoluteString) == ["https://arxiv.org/pdf/1612.03928", "https://jsrse.edu.iq:8080/download/285/301"])
    }

    @Test func showsRunningWhileTheRequestIsOpen() async throws {
        try await library.save(paper("W1"))
        downloader.hold()

        repository.download(openAlexID: "W1")

        #expect(await awaitDownload("W1") { $0 != nil } == .some(.running(bytes: 0, total: nil)))
        downloader.release()
        _ = await awaitStored("W1")
    }

    @Test func anHtmlBodyIsNotAPdf() async throws {
        try await library.save(paper("W1"))
        downloader.setBody(Self.html)

        repository.download(openAlexID: "W1")

        #expect(await awaitDownload("W1") { if case .failed = $0 { true } else { false } } == .some(.failed(.notPDF)))
        #expect(await repository.pdfFile(openAlexID: "W1") == nil)
        #expect(names().isEmpty)
    }

    @Test func aPdfOverTheLimitIsTooLarge() async throws {
        try await library.save(paper("W1"))
        downloader.setBody(Self.pdf + Data(repeating: 0x20, count: 20_000))

        repository.download(openAlexID: "W1")

        #expect(await awaitDownload("W1") { if case .failed = $0 { true } else { false } } == .some(.failed(.tooLarge)))
        #expect(names().isEmpty)
    }

    @Test func beingOfflineIsReported() async throws {
        try await library.save(paper("W1"))
        downloader.setFailure(.connectivity)

        repository.download(openAlexID: "W1")

        #expect(await awaitDownload("W1") { if case .failed = $0 { true } else { false } } == .some(.failed(.offline)))
    }

    @Test func anErrorStatusIsAnHttpFailure() async throws {
        try await library.save(paper("W1"))
        downloader.setFailure(.http(code: 403, usedUserKey: false))

        repository.download(openAlexID: "W1")

        #expect(await awaitDownload("W1") { if case .failed = $0 { true } else { false } } == .some(.failed(.http)))
    }

    @Test func aPaperWithoutALinkReportsNoLink() async throws {
        try await library.save(Paper(openAlexID: "W1", title: "Deep nets"))

        repository.download(openAlexID: "W1")

        #expect(await awaitDownload("W1") { if case .failed = $0 { true } else { false } } == .some(.failed(.noLink)))
        #expect(downloader.urls.isEmpty)
    }

    @Test func aSecondTapWhileDownloadingStartsNothing() async throws {
        try await library.save(paper("W1"))
        downloader.hold()

        repository.download(openAlexID: "W1")
        #expect(await eventually { downloader.urls.count == 1 })
        repository.download(openAlexID: "W1")
        downloader.release()
        _ = await awaitStored("W1")

        #expect(downloader.urls.count == 1)
    }

    @Test func aFailedDownloadCanBeTriedAgain() async throws {
        try await library.save(paper("W1"))
        downloader.setFailure(.connectivity)
        // The failed job reports Failed, then stays active while it winds down (its background time hasn't ended yet).
        background.holdEnds()
        repository.download(openAlexID: "W1")
        _ = await awaitDownload("W1") { if case .failed = $0 { true } else { false } }
        #expect(await eventually { background.waitingEnds == 1 })

        downloader.setFailure(nil)
        repository.download(openAlexID: "W1")

        #expect(await awaitStored("W1") != nil)
        #expect(downloader.urls.count == 2)
        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        background.releaseEnds()
        #expect(await eventually { background.endCount == 2 })
    }

    @Test func cancellingClearsTheStateAndLeavesNoFile() async throws {
        try await library.save(paper("W1"))
        downloader.hold()
        repository.download(openAlexID: "W1")
        #expect(await eventually { downloader.urls.count == 1 })

        repository.cancelDownload(openAlexID: "W1")
        downloader.release()

        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(await repository.pdfFile(openAlexID: "W1") == nil)
        #expect(names().isEmpty)
    }

    @Test func backgroundExpiryCancelsAndCleansUp() async throws {
        try await library.save(paper("W1"))
        // Expiry interrupts a store mid-write: its `.part` file exists.
        downloader.holdBodies()
        repository.download(openAlexID: "W1")
        #expect(await eventually { names().contains { $0.hasSuffix(".part") } && background.grants == 1 })

        // The expired job stays winding down (its background time hasn't ended yet) while Download is tapped again.
        background.holdEnds()
        background.expire()

        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(await eventually { background.waitingEnds == 1 })
        #expect(await repository.pdfFile(openAlexID: "W1") == nil)
        #expect(names().isEmpty)

        // The cancelled job no longer counts: the paper can be downloaded again at once.
        downloader.releaseBodies()
        repository.download(openAlexID: "W1")
        #expect(await awaitStored("W1") != nil)
        #expect(names() == ["local-1.pdf"])
        #expect(downloader.urls.count == 2)
        background.releaseEnds()
        #expect(await eventually { background.endCount == 2 })
    }

    @Test func aCancelledDownloadCanBeStartedAgainWhileItWindsDown() async throws {
        try await library.save(paper("W1"))
        downloader.holdBodies()
        background.holdEnds()
        repository.download(openAlexID: "W1")
        #expect(await eventually { names().contains { $0.hasSuffix(".part") } })

        repository.cancelDownload(openAlexID: "W1")
        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(await eventually { background.waitingEnds == 1 })

        downloader.releaseBodies()
        repository.download(openAlexID: "W1")

        #expect(await awaitStored("W1") != nil)
        #expect(downloader.urls.count == 2)
        background.releaseEnds()
        #expect(await eventually { background.endCount == 2 })
    }

    @Test func aDownloadReplacingACancelledOneWaitsUntilItStopsWriting() async throws {
        try await library.save(paper("W1"))
        // The cancelled request doesn't stop until the gate opens: it may still write the paper's file.
        downloader.hold()
        repository.download(openAlexID: "W1")
        #expect(await eventually { downloader.urls.count == 1 })
        repository.cancelDownload(openAlexID: "W1")

        repository.download(openAlexID: "W1")

        #expect(!(await eventually(timeout: .milliseconds(300)) { downloader.urls.count == 2 }))
        downloader.release()
        #expect(await awaitStored("W1") != nil)
        #expect(downloader.urls.count == 2)
        #expect(names() == ["local-1.pdf"])
    }

    @Test func aFinishedDownloadEndsItsBackgroundTime() async throws {
        try await library.save(paper("W1"))

        repository.download(openAlexID: "W1")
        _ = await awaitStored("W1")

        #expect(await eventually { background.grants == 1 && background.endCount == 1 })
    }

    @Test func removingAPaperCancelsItsDownload() async throws {
        try await library.save(paper("W1"))
        // The body never finishes on its own: only cancelling ends the store.
        downloader.holdBodies()
        repository.download(openAlexID: "W1")
        #expect(await eventually { names().contains { $0.hasSuffix(".part") } })

        _ = try await library.remove(openAlexID: "W1")

        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(await eventually { names().isEmpty })
        downloader.releaseBodies()
    }

    @Test func aPaperPutBackWhileItsCancelledDownloadWindsDownCanBeDownloadedAgain() async throws {
        try await library.save(paper("W1"))
        downloader.holdBodies()
        background.holdEnds()
        repository.download(openAlexID: "W1")
        #expect(await eventually { names().contains { $0.hasSuffix(".part") } })
        let removed = try #require(try await library.remove(openAlexID: "W1"))
        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(await eventually { background.waitingEnds == 1 })

        try await library.restore(removed)
        downloader.releaseBodies()
        repository.download(openAlexID: "W1")

        #expect(await awaitStored("W1") != nil)
        #expect(downloader.urls.count == 2)
        background.releaseEnds()
        #expect(await eventually { background.endCount == 2 })
    }

    @Test func attachStoresTheFileAsAttached() async throws {
        try await library.save(paper("W1"))

        #expect(await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf)) == .done)

        #expect(await awaitStored("W1") == PaperPdf(source: .attached, sizeBytes: Int64(Self.pdf.count), addedAt: 1_000))
        #expect(names() == ["local-1.pdf"])
    }

    @Test func attachingANonPdfKeepsTheCurrentPdf() async throws {
        try await library.save(paper("W1"))
        _ = await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf))

        #expect(await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.html)) == .notPDF)

        #expect(try Data(contentsOf: #require(await repository.pdfFile(openAlexID: "W1"))) == Self.pdf)
    }

    @Test func attachingAFileOverTheLimitIsTooLarge() async throws {
        try await library.save(paper("W1"))

        let result = await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf + Data(repeating: 0x20, count: 20_000)))

        #expect(result == .tooLarge)
        #expect(names().isEmpty)
    }

    @Test func attachingAnUnreadableFileFails() async throws {
        try await library.save(paper("W1"))
        let missing = FileManager.default.temporaryDirectory.appending(path: "missing-\(UUID().uuidString).pdf")

        #expect(await repository.attach(openAlexID: "W1", from: missing) == .unreadable)
    }

    // MARK: Crash reports

    /// A repository on this suite's database, reporting to `crash`, keeping its PDFs in `files`.
    private func reportingRepository(_ crash: FakeCrashReporting, files: PdfFileStore? = nil) -> GRDBPdfRepository {
        GRDBPdfRepository(
            store: store, files: files ?? self.files, downloader: downloader, background: background, crash: crash,
            now: { 1_000 }, maxBytes: 10_000
        )
    }

    private func awaitFailure(_ repository: GRDBPdfRepository, _ openAlexID: String) async -> DownloadState?? {
        await firstValue(repository.observeDownload(openAlexID: openAlexID)) { if case .failed = $0 { true } else { false } }
    }

    @Test func aDownloadWhoseFileCantBeWrittenIsReported() async throws {
        try await library.save(paper("W1"))
        // A file where the folder should be: creating the folder fails, which the store reports as `PdfWriteError`.
        let blocked = FileManager.default.temporaryDirectory.appending(path: "blocked-\(UUID().uuidString)")
        try Data().write(to: blocked)
        defer { try? FileManager.default.removeItem(at: blocked) }
        let crash = FakeCrashReporting()
        let repository = reportingRepository(crash, files: PdfFileStore(directory: blocked))

        repository.download(openAlexID: "W1")

        #expect(await awaitFailure(repository, "W1") != nil)
        #expect(crash.records.map(\.site) == [.pdfStore])
        #expect(crash.records.first?.type.hasSuffix("PdfWriteError") == true)
    }

    @Test func aDownloadWhoseRowCantBeWrittenIsReported() async throws {
        let crash = FakeCrashReporting()
        try await withOnDiskPools {
            let disk = try onDisk(crash: crash)
            defer { try? FileManager.default.removeItem(at: disk.folder) }
            try await disk.library.save(paper("W1"))
            downloader.holdBodies()
            disk.repository.download(openAlexID: "W1")
            #expect(await eventually { names().contains { $0.hasSuffix(".part") } })

            HashiyaDatabase.suspend()
            downloader.releaseBodies()
            #expect(await firstValue(disk.repository.observeDownload(openAlexID: "W1")) { if case .running = $0 { false } else { true } } == .some(.failed(.http)))
            HashiyaDatabase.resume()
        }
        #expect(crash.records.map(\.site) == [.pdfStore])
    }

    @Test func aNotPdfOrTooLargeDownloadIsNotReported() async throws {
        try await library.save(paper("W1"))
        try await library.save(paper("W2"))
        let crash = FakeCrashReporting()
        let repository = reportingRepository(crash)

        downloader.setBody(Self.html, for: "https://arxiv.org/pdf/W1")
        repository.download(openAlexID: "W1")
        #expect(await awaitFailure(repository, "W1") == .some(.failed(.notPDF)))

        downloader.setBody(Self.pdf + Data(repeating: 0x20, count: 20_000), for: "https://arxiv.org/pdf/W2")
        repository.download(openAlexID: "W2")
        #expect(await awaitFailure(repository, "W2") == .some(.failed(.tooLarge)))

        #expect(crash.records.isEmpty)
    }

    @Test func aFailedConnectionIsNotReported() async throws {
        try await library.save(paper("W1"))
        downloader.setFailure(.connectivity)
        let crash = FakeCrashReporting()
        let repository = reportingRepository(crash)

        repository.download(openAlexID: "W1")

        #expect(await awaitFailure(repository, "W1") == .some(.failed(.offline)))
        #expect(crash.records.isEmpty)
    }

    @Test func anAttachWhoseCopyFailsIsReported() async throws {
        try await library.save(paper("W1"))
        let crash = FakeCrashReporting()
        let missing = FileManager.default.temporaryDirectory.appending(path: "missing-\(UUID().uuidString).pdf")

        #expect(await reportingRepository(crash).attach(openAlexID: "W1", from: missing) == .unreadable)

        #expect(crash.records.map(\.site) == [.pdfStore])
    }

    @Test func anAttachWhoseRowCantBeWrittenIsReported() async throws {
        let crash = FakeCrashReporting()
        try await withOnDiskPools {
            let disk = try onDisk(crash: crash)
            defer { try? FileManager.default.removeItem(at: disk.folder) }
            try await disk.library.save(paper("W1"))

            HashiyaDatabase.suspend()
            #expect(await disk.repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf)) == .unreadable)
            HashiyaDatabase.resume()
        }
        #expect(crash.records.map(\.site) == [.pdfStore])
    }

    @Test func aNotPdfOrTooLargeAttachIsNotReported() async throws {
        try await library.save(paper("W1"))
        let crash = FakeCrashReporting()
        let repository = reportingRepository(crash)

        #expect(await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.html)) == .notPDF)
        #expect(await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf + Data(repeating: 0x20, count: 20_000))) == .tooLarge)

        #expect(crash.records.isEmpty)
    }

    @Test func attachingToAnUnsavedPaperFails() async throws {
        #expect(await repository.attach(openAlexID: "W9", from: try temporaryFile(Self.pdf)) == .unreadable)
        #expect(names().isEmpty)
    }

    @Test func attachingCancelsARunningDownload() async throws {
        try await library.save(paper("W1"))
        downloader.hold()
        repository.download(openAlexID: "W1")
        #expect(await eventually { downloader.urls.count == 1 })

        let attach = Task { await repository.attach(openAlexID: "W1", from: try! temporaryFile(Self.pdf)) }
        downloader.release()

        #expect(await attach.value == .done)
        #expect(await awaitStored("W1")?.source == .attached)
    }

    @Test func replacingAPdfStartsAgainOnTheFirstPage() async throws {
        try await library.save(paper("W1"))
        _ = await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf))
        try await repository.setLastPage(openAlexID: "W1", page: 7)

        _ = await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf + Data("% v2\n".utf8)))

        #expect(await firstValue(repository.observePdf(openAlexID: "W1")) { $0?.sizeBytes == Int64(Self.pdf.count + 5) }??.lastPage == 0)
    }

    @Test func setLastPageIsStored() async throws {
        try await library.save(paper("W1"))
        _ = await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf))

        try await repository.setLastPage(openAlexID: "W1", page: 12)

        #expect(await firstValue(repository.observePdf(openAlexID: "W1")) { $0?.lastPage == 12 } != nil)
    }

    @Test func removeClearsTheRowAndDeletesTheFile() async throws {
        try await library.save(paper("W1"))
        _ = await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf))

        try await repository.remove(openAlexID: "W1")

        #expect(await firstValue(repository.observePdf(openAlexID: "W1")) { $0 == nil } != nil)
        #expect(names().isEmpty)
    }

    @Test func pdfFileIsNilWithoutAPdf() async throws {
        try await library.save(paper("W1"))

        #expect(await repository.pdfFile(openAlexID: "W1") == nil)
        #expect(await repository.pdfFile(openAlexID: "W9") == nil)
    }

    @Test func undoKeepsTheFileAndDiscardDeletesIt() async throws {
        try await library.save(paper("W1"))
        _ = await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf))

        let removed = try #require(try await library.remove(openAlexID: "W1"))
        #expect(removed.pdf?.source == .attached)
        #expect(names() == ["local-1.pdf"])
        try await library.restore(removed)
        #expect(await awaitStored("W1")?.source == .attached)
        #expect(try Data(contentsOf: #require(await repository.pdfFile(openAlexID: "W1"))) == Self.pdf)

        // Undo put it back: discarding the old removal must keep the file in use.
        await repository.discardRemoved(removed)
        #expect(names() == ["local-1.pdf"])

        // A final removal: the file goes.
        let final = try #require(try await library.remove(openAlexID: "W1"))
        await repository.discardRemoved(final)
        #expect(names().isEmpty)
    }

    @Test func aRemovalRightAfterADownloadKeepsTheFileForUndo() async throws {
        try await library.save(paper("W1"))
        repository.download(openAlexID: "W1")
        #expect(await awaitStored("W1")?.source == .downloaded)

        let removed = try #require(try await library.remove(openAlexID: "W1"))
        #expect(removed.pdf?.source == .downloaded)
        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(names() == ["local-1.pdf"])

        try await library.restore(removed)
        #expect(await awaitStored("W1")?.source == .downloaded)
        #expect(try Data(contentsOf: #require(await repository.pdfFile(openAlexID: "W1"))) == Self.pdf)

        let final = try #require(try await library.remove(openAlexID: "W1"))
        await repository.discardRemoved(final)
        #expect(names().isEmpty)
    }

    @Test func discardingAPaperSavedAgainUnderANewIDDeletesTheOldFile() async throws {
        try await library.save(paper("W1"))
        _ = await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf))
        let removed = try #require(try await library.remove(openAlexID: "W1"))
        try await library.save(paper("W1"))

        await repository.discardRemoved(removed)

        #expect(names().isEmpty)
    }

    @Test func discardingWithoutAPdfDoesNothing() async throws {
        try await library.save(paper("W1"))
        let removed = try #require(try await library.remove(openAlexID: "W1"))

        await repository.discardRemoved(removed)

        #expect(names().isEmpty)
    }

    @Test func deleteDownloadedKeepsAttachedFiles() async throws {
        try await library.save(paper("W1"))
        try await library.save(paper("W2"))
        repository.download(openAlexID: "W1")
        _ = await awaitStored("W1")
        _ = await repository.attach(openAlexID: "W2", from: try temporaryFile(Self.pdf))

        try await repository.deleteDownloaded()

        #expect(await firstValue(repository.observePdf(openAlexID: "W1")) { $0 == nil } != nil)
        #expect(await awaitStored("W2")?.source == .attached)
        #expect(names() == ["local-2.pdf"])
    }

    @Test func storageSumsBySource() async throws {
        try await library.save(paper("W1"))
        try await library.save(paper("W2"))
        repository.download(openAlexID: "W1")
        _ = await awaitStored("W1")
        _ = await repository.attach(openAlexID: "W2", from: try temporaryFile(Self.pdf + Data("% more\n".utf8)))

        let size = Int64(Self.pdf.count)
        #expect(try await repository.storage() == PdfStorage(downloadedBytes: size, downloadedCount: 1, attachedBytes: size + 7, attachedCount: 1))
    }

    @Test func theSweepWaitsForAStoreInFlight() async throws {
        try await library.save(paper("W1"))
        downloader.holdBodies()
        repository.download(openAlexID: "W1")
        #expect(await eventually { names().contains { $0.hasSuffix(".part") } })

        let sweep = Task { await repository.sweepOrphans() }
        try await Task.sleep(for: .milliseconds(100))
        downloader.releaseBodies()
        await sweep.value

        #expect(await awaitStored("W1") != nil)
        #expect(names() == ["local-1.pdf"])
    }

    @Test func sweepOrphansDeletesFilesWithoutARow() async throws {
        try await library.save(paper("W1"))
        _ = await repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf))
        try Self.pdf.write(to: directory.appending(path: "ghost.pdf"))
        try Data("half a download".utf8).write(to: directory.appending(path: "local-1-\(UUID().uuidString).part"))

        await repository.sweepOrphans()

        #expect(names() == ["local-1.pdf"])
    }

    private func savedPaperID(_ openAlexID: String) async throws -> String {
        try await library.save(paper(openAlexID))
        return try #require(await store.paperID(openAlexID: openAlexID))
    }

    @Test func sweepClearsRowsWhoseFileIsGone() async throws {
        let w1 = try await savedPaperID("W1")
        let w2 = try await savedPaperID("W2")
        try await store.setPdf(paperID: w1, source: "downloaded", size: 10, addedAt: 1)
        try await store.setPdf(paperID: w2, source: "attached", size: 10, addedAt: 1)
        try FileManager.default.createDirectory(at: files.directory, withIntermediateDirectories: true)
        try Data("%PDF-1.4".utf8).write(to: files.file(paperID: w2))

        await repository.sweepOrphans()

        #expect(try await store.pdfPaperIDs() == [w2])
        #expect(FileManager.default.fileExists(atPath: files.file(paperID: w2).path))
    }

    @Test func sweepMarksDownloadedPdfsExcludedFromBackup() async throws {
        let w1 = try await savedPaperID("W1")
        let w2 = try await savedPaperID("W2")
        try FileManager.default.createDirectory(at: files.directory, withIntermediateDirectories: true)
        for id in [w1, w2] { try Data("%PDF-1.4".utf8).write(to: files.file(paperID: id)) }
        try await store.setPdf(paperID: w1, source: "downloaded", size: 8, addedAt: 1)
        try await store.setPdf(paperID: w2, source: "attached", size: 8, addedAt: 1)

        await repository.sweepOrphans()

        #expect(files.isExcludedFromBackup(paperID: w1))
        #expect(!files.isExcludedFromBackup(paperID: w2))
    }

    @Test func aDownloadedPdfIsExcludedFromBackupWhenStored() async throws {
        try await library.save(paper("W1"))

        repository.download(openAlexID: "W1")

        #expect(await awaitStored("W1") != nil)
        #expect(files.isExcludedFromBackup(paperID: "local-1"))
    }

    // MARK: Background suspension

    /// The app's setup: an on-disk pool that refuses writes while the database is suspended (`HashiyaDatabase.suspend()`),
    /// sharing this suite's downloader, background time and PDF folder. Call inside `withOnDiskPools`.
    private func onDisk(crash: any CrashReporting = NoCrashReporting()) throws -> (store: PaperStore, library: GRDBLibraryRepository, repository: GRDBPdfRepository, folder: URL) {
        let folder = FileManager.default.temporaryDirectory.appending(path: "db-\(UUID().uuidString)", directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        let store = try PaperStore.open(at: folder.appending(path: "hashiya.sqlite"))
        let ids = OSAllocatedUnfairLock(initialState: 0)
        let library = GRDBLibraryRepository(store: store, newID: { ids.withLock { $0 += 1; return "local-\($0)" } })
        let repository = GRDBPdfRepository(store: store, files: files, downloader: downloader, background: background, crash: crash, now: { 1_000 }, maxBytes: 10_000)
        return (store, library, repository, folder)
    }

    /// Whether `operation` returns within `timeout`.
    private func returns(within timeout: Duration = .seconds(3), _ operation: @escaping @Sendable () async -> Void) async -> Bool {
        await withTaskGroup(of: Bool.self) { group in
            group.addTask {
                await operation()
                return true
            }
            group.addTask {
                try? await Task.sleep(for: timeout)
                return false
            }
            let first = await group.next() ?? false
            group.cancelAll()
            return first
        }
    }

    /// RootView's background sequence: it waits for the stores, then suspends the database. Call inside `withOnDiskPools`.
    private func backgroundSuspension(_ repository: GRDBPdfRepository) -> (task: Task<Void, Never>, suspended: @Sendable () -> Bool) {
        let suspended = OSAllocatedUnfairLock(initialState: false)
        let task = Task {
            await repository.storesFinished()
            HashiyaDatabase.suspend()
            suspended.withLock { $0 = true }
        }
        return (task, { suspended.withLock { $0 } })
    }

    @Test func aDownloadFinishingAfterTheAppLeftIsStoredBeforeTheDatabaseSuspends() async throws {
        try await withOnDiskPools {
            let disk = try onDisk()
            defer { try? FileManager.default.removeItem(at: disk.folder) }
            try await disk.library.save(paper("W1"))
            downloader.holdBodies()
            disk.repository.download(openAlexID: "W1")
            #expect(await eventually { names().contains { $0.hasSuffix(".part") } })

            let suspension = backgroundSuspension(disk.repository)
            try await Task.sleep(for: .milliseconds(100))
            #expect(!suspension.suspended())
            downloader.releaseBodies()
            await suspension.task.value

            // The download stopped before the suspend: nothing is running, nothing failed.
            let stopped = await firstValue(disk.repository.observeDownload(openAlexID: "W1")) {
                if case .running = $0 { false } else { true }
            }
            #expect(stopped == .some(nil))
            HashiyaDatabase.resume()
            #expect(try await disk.store.pdfPaperIDs() == ["local-1"])
            #expect(names() == ["local-1.pdf"])
        }
    }

    @Test func storesFinishedWaitsForAGateStore() async throws {
        let release = AsyncStream<Void>.makeStream()
        let running = Task { [repository] in
            try await repository.withStoreGate {
                for await _ in release.stream { break }
            }
        }
        try await Task.sleep(for: .milliseconds(50))
        let finished = OSAllocatedUnfairLock(initialState: false)
        let waiter = Task { [repository] in
            await repository.storesFinished()
            finished.withLock { $0 = true }
        }
        try await Task.sleep(for: .milliseconds(50))
        #expect(!finished.withLock { $0 })
        release.continuation.yield(())
        try await running.value
        await waiter.value
        #expect(finished.withLock { $0 })
    }

    @Test func storesFinishedReturnsAtOnceWhenNothingRuns() async throws {
        try await library.save(paper("W1"))
        repository.download(openAlexID: "W1")
        _ = await awaitStored("W1")

        #expect(await returns { await repository.storesFinished() })
    }

    @Test func storesFinishedWaitsForARunningDownload() async throws {
        try await library.save(paper("W1"))
        downloader.holdBodies()
        repository.download(openAlexID: "W1")
        #expect(await eventually { names().contains { $0.hasSuffix(".part") } })

        let waited = OSAllocatedUnfairLock(initialState: false)
        let wait = Task {
            await repository.storesFinished()
            waited.withLock { $0 = true }
        }
        try await Task.sleep(for: .milliseconds(100))
        #expect(!waited.withLock { $0 })
        downloader.releaseBodies()

        #expect(await returns { await wait.value })
        // It returned once the row was set.
        #expect(try await store.pdfPaperIDs() == ["local-1"])
    }

    @Test func storesFinishedReturnsOnceADownloadIsCancelled() async throws {
        try await library.save(paper("W1"))
        downloader.holdBodies()
        repository.download(openAlexID: "W1")
        #expect(await eventually { names().contains { $0.hasSuffix(".part") } })
        let wait = Task { await repository.storesFinished() }

        repository.cancelDownload(openAlexID: "W1")

        #expect(await returns { await wait.value })
        downloader.releaseBodies()
    }

    @Test func storesFinishedReturnsOnceADownloadFailsWithoutWaitingForItsBackgroundTime() async throws {
        try await library.save(paper("W1"))
        downloader.setFailure(.connectivity)
        downloader.hold()
        background.holdEnds()
        repository.download(openAlexID: "W1")
        #expect(await eventually { downloader.urls.count == 1 })
        let waited = OSAllocatedUnfairLock(initialState: false)
        let wait = Task {
            await repository.storesFinished()
            waited.withLock { $0 = true }
        }
        try await Task.sleep(for: .milliseconds(100))
        #expect(!waited.withLock { $0 })

        downloader.release()

        #expect(await returns { await wait.value })
        #expect(await awaitDownload("W1") { if case .failed = $0 { true } else { false } } == .some(.failed(.offline)))
        background.releaseEnds()
    }

    @Test func storesFinishedWaitsForADownloadStartedWhileItWaits() async throws {
        try await library.save(paper("W1"))
        try await library.save(paper("W2"))
        downloader.holdBodies()
        repository.download(openAlexID: "W1")
        #expect(await eventually { names().count { $0.hasSuffix(".part") } == 1 })
        let waited = OSAllocatedUnfairLock(initialState: false)
        let wait = Task {
            await repository.storesFinished()
            waited.withLock { $0 = true }
        }
        repository.download(openAlexID: "W2")
        #expect(await eventually { names().count { $0.hasSuffix(".part") } == 2 })

        repository.cancelDownload(openAlexID: "W1")
        try await Task.sleep(for: .milliseconds(100))
        #expect(!waited.withLock { $0 })
        downloader.releaseBodies()

        #expect(await returns { await wait.value })
        #expect(await awaitStored("W2") != nil)
    }

    @Test func aRefusedRowForAPaperWithAPdfKeepsTheFileItPointsAt() async throws {
        try await withOnDiskPools {
            let disk = try onDisk()
            defer { try? FileManager.default.removeItem(at: disk.folder) }
            try await disk.library.save(paper("W1"))
            #expect(await disk.repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf)) == .done)
            downloader.holdBodies()
            disk.repository.download(openAlexID: "W1")
            #expect(await eventually { names().contains { $0.hasSuffix(".part") } })

            // The download replaces the file, then its row write is refused: the row still points at that file.
            HashiyaDatabase.suspend()
            downloader.releaseBodies()
            let stopped = await firstValue(disk.repository.observeDownload(openAlexID: "W1")) {
                if case .running = $0 { false } else { true }
            }
            #expect(stopped == .some(.failed(.http)))
            HashiyaDatabase.resume()

            #expect(try await disk.store.pdfPaperIDs() == ["local-1"])
            #expect(await disk.repository.pdfFile(openAlexID: "W1") != nil)
        }
    }

    @Test func aRefusedAttachLeavesNoFile() async throws {
        try await withOnDiskPools {
            let disk = try onDisk()
            defer { try? FileManager.default.removeItem(at: disk.folder) }
            try await disk.library.save(paper("W1"))

            HashiyaDatabase.suspend()
            #expect(await disk.repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf)) == .unreadable)
            HashiyaDatabase.resume()

            #expect(names().isEmpty)
        }
    }

    @Test func aRefusedAttachForAPaperWithAPdfKeepsTheFileItsRowPointsAt() async throws {
        try await withOnDiskPools {
            let disk = try onDisk()
            defer { try? FileManager.default.removeItem(at: disk.folder) }
            try await disk.library.save(paper("W1"))
            #expect(await disk.repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf)) == .done)

            HashiyaDatabase.suspend()
            #expect(await disk.repository.attach(openAlexID: "W1", from: try temporaryFile(Self.pdf + Data("% v2\n".utf8))) == .unreadable)
            HashiyaDatabase.resume()

            #expect(await disk.repository.pdfFile(openAlexID: "W1") != nil)
        }
    }

    // MARK: Other links when the stored one fails

    private static let badLink = "https://langtaosha.org.cn/index.php/lts/preprint/download/10/108"
    private static let arxivLink = "https://arxiv.org/pdf/1706.03762"
    private static let arxiv = NetworkSource(displayName: "arXiv (Cornell University)", type: "repository")

    private func location(_ url: String?, source: NetworkSource? = nil, isOA: Bool = true) -> NetworkLocation {
        NetworkLocation(pdfURL: url, source: source, isOA: isOA)
    }

    private func storedLink(_ openAlexID: String) async throws -> String? {
        try await queue.read { db in try String.fetchOne(db, sql: "SELECT oa_pdf_url FROM papers WHERE open_alex_id = ?", arguments: [openAlexID]) }
    }

    private func awaitFailure(_ openAlexID: String) async -> DownloadState?? {
        await awaitDownload(openAlexID) { if case .failed = $0 { true } else { false } }
    }

    @Test func aBadStoredLinkFallsBackToArxivAndKeepsTheLinkThatWorked() async throws {
        try await library.save(paper("W1", pdfURL: Self.badLink))
        downloader.setBody(Self.html, for: Self.badLink)
        pdfLinks.setLocations([
            location(nil, isOA: false),
            location(Self.badLink),
            location("https://repository.example/attention.pdf"),
            location("http://arxiv.org/pdf/1706.03762", source: Self.arxiv),
            location(nil, source: Self.arxiv),
        ])

        repository.download(openAlexID: "W1")

        #expect(await awaitStored("W1") == PaperPdf(source: .downloaded, sizeBytes: Int64(Self.pdf.count), addedAt: 1_000, lastPage: 0))
        #expect(downloader.urls.map(\.absoluteString) == [Self.badLink, Self.arxivLink])
        #expect(pdfLinks.requests == ["W1"])
        // The link is replaced after the PDF is recorded, before the download ends.
        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(try await storedLink("W1") == Self.arxivLink)
        #expect(names() == ["local-1.pdf"])
    }

    @Test func aLinkThatWorksTheFirstTimeLooksUpNothing() async throws {
        try await library.save(paper("W1"))

        repository.download(openAlexID: "W1")
        _ = await awaitStored("W1")

        #expect(pdfLinks.requests.isEmpty)
        #expect(try await storedLink("W1") == "https://arxiv.org/pdf/W1")
    }

    @Test func whenEveryLinkFailsTheFirstFailureIsReportedAfterAtMostThreeMore() async throws {
        try await library.save(paper("W1", pdfURL: Self.badLink))
        downloader.setFailure(.http(code: 404, usedUserKey: false), for: Self.badLink)
        downloader.setBody(Self.html)
        pdfLinks.setLocations((1...5).map { location("https://mirror\($0).example/a.pdf") })

        repository.download(openAlexID: "W1")

        #expect(await awaitFailure("W1") == .some(.failed(.http)))
        #expect(downloader.urls.map(\.absoluteString) == [Self.badLink] + (1...3).map { "https://mirror\($0).example/a.pdf" })
        #expect(try await storedLink("W1") == Self.badLink)
        #expect(await repository.pdfFile(openAlexID: "W1") == nil)
        #expect(names().isEmpty)
    }

    @Test func aFallbackLinkThatAlsoFailsMovesOnToTheNext() async throws {
        try await library.save(paper("W1", pdfURL: Self.badLink))
        downloader.setBody(Self.html, for: Self.badLink)
        downloader.setFailure(.connectivity, for: Self.arxivLink)
        pdfLinks.setLocations([location("https://repository.example/attention.pdf"), location(Self.arxivLink, source: Self.arxiv)])

        repository.download(openAlexID: "W1")

        #expect(await awaitStored("W1") != nil)
        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(downloader.urls.map(\.absoluteString) == [Self.badLink, Self.arxivLink, "https://repository.example/attention.pdf"])
        #expect(try await storedLink("W1") == "https://repository.example/attention.pdf")
    }

    @Test func beingOfflineLooksUpNoOtherLinks() async throws {
        try await library.save(paper("W1", pdfURL: Self.badLink))
        downloader.setFailure(.connectivity)
        pdfLinks.setLocations([location(Self.arxivLink, source: Self.arxiv)])

        repository.download(openAlexID: "W1")

        #expect(await awaitFailure("W1") == .some(.failed(.offline)))
        #expect(pdfLinks.requests.isEmpty)
        #expect(downloader.urls.map(\.absoluteString) == [Self.badLink])
    }

    @Test func aPdfOverTheLimitLooksUpNoOtherLinks() async throws {
        try await library.save(paper("W1", pdfURL: Self.badLink))
        downloader.setBody(Self.pdf + Data(repeating: 0x20, count: 20_000))
        pdfLinks.setLocations([location(Self.arxivLink, source: Self.arxiv)])

        repository.download(openAlexID: "W1")

        #expect(await awaitFailure("W1") == .some(.failed(.tooLarge)))
        #expect(pdfLinks.requests.isEmpty)
        #expect(downloader.urls.map(\.absoluteString) == [Self.badLink])
    }

    @Test func aFailedLookupReportsTheFirstFailure() async throws {
        try await library.save(paper("W1", pdfURL: Self.badLink))
        downloader.setBody(Self.html)
        pdfLinks.setFailure(.connectivity)

        repository.download(openAlexID: "W1")

        #expect(await awaitFailure("W1") == .some(.failed(.notPDF)))
        #expect(pdfLinks.requests == ["W1"])
        #expect(downloader.urls.map(\.absoluteString) == [Self.badLink])
        #expect(names().isEmpty)
    }

    @Test func cancellingDuringTheLookupStopsAndLeavesNoFile() async throws {
        try await library.save(paper("W1", pdfURL: Self.badLink))
        downloader.setBody(Self.html)
        pdfLinks.setLocations([location(Self.arxivLink, source: Self.arxiv)])
        pdfLinks.hold()
        repository.download(openAlexID: "W1")
        #expect(await eventually { pdfLinks.requests.count == 1 })

        repository.cancelDownload(openAlexID: "W1")
        pdfLinks.release()

        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(await eventually { background.endCount == 1 })
        #expect(downloader.urls.map(\.absoluteString) == [Self.badLink])
        #expect(await repository.pdfFile(openAlexID: "W1") == nil)
        #expect(names().isEmpty)
    }

    @Test func cancellingDuringAFallbackDownloadStopsAndLeavesNoFile() async throws {
        try await library.save(paper("W1", pdfURL: Self.badLink))
        downloader.setBody(Self.html, for: Self.badLink)
        downloader.hold(only: Self.arxivLink)
        pdfLinks.setLocations([location(Self.arxivLink, source: Self.arxiv), location("https://repository.example/attention.pdf")])
        repository.download(openAlexID: "W1")
        #expect(await eventually { downloader.urls.count == 2 })

        repository.cancelDownload(openAlexID: "W1")
        downloader.release()

        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(await eventually { background.endCount == 1 })
        #expect(downloader.urls.map(\.absoluteString) == [Self.badLink, Self.arxivLink])
        #expect(await repository.pdfFile(openAlexID: "W1") == nil)
        #expect(try await storedLink("W1") == Self.badLink)
        #expect(names().isEmpty)
    }

    @Test func backgroundExpiryDuringAFallbackDownloadCancelsAndCleansUp() async throws {
        try await library.save(paper("W1", pdfURL: Self.badLink))
        downloader.setBody(Self.html, for: Self.badLink)
        downloader.hold(only: Self.arxivLink)
        pdfLinks.setLocations([location(Self.arxivLink, source: Self.arxiv)])
        repository.download(openAlexID: "W1")
        #expect(await eventually { downloader.urls.count == 2 })

        background.expire()
        downloader.release()

        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        // One grant covers the stored link, the lookup and every other link.
        #expect(await eventually { background.endCount == 1 })
        #expect(background.grants == 1)
        #expect(await repository.pdfFile(openAlexID: "W1") == nil)
        #expect(names().isEmpty)
    }

    @Test func removingThePaperDuringAFallbackDownloadLeavesNothing() async throws {
        try await library.save(paper("W1", pdfURL: Self.badLink))
        downloader.setBody(Self.html, for: Self.badLink)
        downloader.hold(only: Self.arxivLink)
        pdfLinks.setLocations([location(Self.arxivLink, source: Self.arxiv)])
        repository.download(openAlexID: "W1")
        #expect(await eventually { downloader.urls.count == 2 })

        _ = try await library.remove(openAlexID: "W1")
        downloader.release()

        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(await eventually { background.endCount == 1 })
        #expect(try await store.paperID(openAlexID: "W1") == nil)
        #expect(names().isEmpty)
    }

    @Test func storesFinishedWaitsForTheWholeFallback() async throws {
        try await library.save(paper("W1", pdfURL: Self.badLink))
        downloader.setBody(Self.html, for: Self.badLink)
        pdfLinks.setLocations([location(Self.arxivLink, source: Self.arxiv)])
        pdfLinks.hold()
        repository.download(openAlexID: "W1")
        #expect(await eventually { pdfLinks.requests.count == 1 })

        let finished = OSAllocatedUnfairLock(initialState: false)
        let waiter = Task { [repository] in
            await repository.storesFinished()
            finished.withLock { $0 = true }
        }
        #expect(!(await eventually(timeout: .milliseconds(300)) { finished.withLock { $0 } }))

        pdfLinks.release()
        #expect(await returns { await waiter.value })
        #expect(try await storedLink("W1") == Self.arxivLink)
    }

    @Test func aFallbackDownloadCanBeTriedAgainAfterItFails() async throws {
        try await library.save(paper("W1", pdfURL: Self.badLink))
        downloader.setBody(Self.html)
        pdfLinks.setLocations([location(Self.arxivLink, source: Self.arxiv)])
        repository.download(openAlexID: "W1")
        #expect(await awaitFailure("W1") == .some(.failed(.notPDF)))

        downloader.setBody(Self.pdf, for: Self.arxivLink)
        repository.download(openAlexID: "W1")

        #expect(await awaitStored("W1") != nil)
        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(downloader.urls.map(\.absoluteString) == [Self.badLink, Self.arxivLink, Self.badLink, Self.arxivLink])
        #expect(try await storedLink("W1") == Self.arxivLink)
    }
}
