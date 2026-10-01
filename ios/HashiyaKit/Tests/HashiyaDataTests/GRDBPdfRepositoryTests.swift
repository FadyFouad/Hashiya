import Foundation
import GRDB
@testable import HashiyaData
import HashiyaDatabase
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
        var gate: [CheckedContinuation<Void, Never>]?
        /// Non-nil while bodies are held after their first byte: the waiting bodies.
        var bodyGate: [CheckedContinuation<Void, Never>]?
        var urls: [URL] = []
    }

    private let state = OSAllocatedUnfairLock<State>(uncheckedState: State())

    var urls: [URL] { state.withLockUnchecked { $0.urls } }
    func setBody(_ body: Data) { state.withLockUnchecked { $0.body = body } }
    func setFailure(_ failure: NetworkFailure?) { state.withLockUnchecked { $0.failure = failure } }
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
        let (body, failure) = state.withLockUnchecked { ($0.body, $0.failure) }
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
        downloader.hold()
        repository.download(openAlexID: "W1")
        #expect(await eventually { downloader.urls.count == 1 && background.grants == 1 })

        background.expire()
        downloader.release()

        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(await repository.pdfFile(openAlexID: "W1") == nil)
        #expect(await eventually { background.endCount == 1 })
        #expect(!names().contains { $0.hasSuffix(".part") || $0.hasSuffix(".pdf") })
    }

    @Test func aFinishedDownloadEndsItsBackgroundTime() async throws {
        try await library.save(paper("W1"))

        repository.download(openAlexID: "W1")
        _ = await awaitStored("W1")

        #expect(await eventually { background.grants == 1 && background.endCount == 1 })
    }

    @Test func removingAPaperCancelsItsDownload() async throws {
        try await library.save(paper("W1"))
        downloader.hold()
        repository.download(openAlexID: "W1")
        #expect(await eventually { downloader.urls.count == 1 })

        _ = try await library.remove(openAlexID: "W1")
        downloader.release()

        #expect(await awaitDownload("W1") { $0 == nil } != nil)
        #expect(names().isEmpty)
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
}
