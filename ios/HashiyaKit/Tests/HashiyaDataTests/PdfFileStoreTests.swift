import Foundation
@testable import HashiyaData
import Testing

/// Mirrors Android's `PdfFileStoreTest`.
struct PdfFileStoreTests {
    private let directory: URL
    private let store: PdfFileStore

    init() throws {
        directory = FileManager.default.temporaryDirectory.appending(path: "pdfs-\(UUID().uuidString)", directoryHint: .isDirectory)
        store = PdfFileStore(directory: directory)
    }

    private static let pdf = Data("%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n".utf8)

    /// A stream of `parts`, each one chunk; `failingAfter` ends it with an error after that many chunks.
    private func chunks(_ parts: [Data], failingAfter: Int? = nil) -> AsyncThrowingStream<Data, Error> {
        AsyncThrowingStream { continuation in
            for (index, part) in parts.enumerated() {
                if index == failingAfter {
                    continuation.finish(throwing: URLError(.networkConnectionLost))
                    return
                }
                continuation.yield(part)
            }
            continuation.finish()
        }
    }

    private func names() -> [String] {
        ((try? FileManager.default.contentsOfDirectory(atPath: directory.path)) ?? []).sorted()
    }

    private func write(_ name: String, _ data: Data = Data("x".utf8)) throws {
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try data.write(to: directory.appending(path: name))
    }

    @Test func storesAPdfUnderThePaperIDAndReturnsItsSize() async throws {
        let result = try await store.store(paperID: "local-1", chunks: chunks([Self.pdf]), maxBytes: 1_000) { _ in }

        #expect(result == .stored(size: Int64(Self.pdf.count)))
        #expect(try Data(contentsOf: store.file(paperID: "local-1")) == Self.pdf)
        #expect(names() == ["local-1.pdf"])
    }

    @Test func aFileCanBeExcludedFromBackupAndIncludedAgain() throws {
        try write("local-1.pdf")

        store.setExcludedFromBackup(true, paperID: "local-1")
        #expect(store.isExcludedFromBackup(paperID: "local-1"))

        store.setExcludedFromBackup(false, paperID: "local-1")
        #expect(!store.isExcludedFromBackup(paperID: "local-1"))
    }

    @Test func excludingAMissingFileDoesNothing() {
        store.setExcludedFromBackup(true, paperID: "local-9")

        #expect(!store.isExcludedFromBackup(paperID: "local-9"))
    }

    @Test func reportsTheBytesCopiedSoFar() async throws {
        var progress: [Int64] = []
        _ = try await store.store(paperID: "local-1", chunks: chunks([Self.pdf.prefix(10), Self.pdf.dropFirst(10)]), maxBytes: 1_000) {
            progress.append($0)
        }

        #expect(progress == [10, Int64(Self.pdf.count)])
    }

    @Test func acceptsAShortPreambleBeforeTheHeader() async throws {
        let body = Data(repeating: 0x20, count: 100) + Self.pdf

        #expect(try await store.store(paperID: "local-1", chunks: chunks([body]), maxBytes: 10_000) { _ in } == .stored(size: Int64(body.count)))
    }

    @Test func rejectsAFileWithoutTheHeaderAndLeavesNothing() async throws {
        let html = Data("<!DOCTYPE html><html><body>Sign in</body></html>".utf8)

        #expect(try await store.store(paperID: "local-1", chunks: chunks([html]), maxBytes: 10_000) { _ in } == .notPDF)
        #expect(names().isEmpty)
    }

    @Test func rejectsAHeaderThatOnlyAppearsAfterTheFirst1024Bytes() async throws {
        let body = Data(repeating: 0x20, count: 1_024) + Self.pdf

        #expect(try await store.store(paperID: "local-1", chunks: chunks([body]), maxBytes: 10_000) { _ in } == .notPDF)
        #expect(names().isEmpty)
    }

    @Test func stopsReadingALargeNonPdfSoonAfterItsStart() async throws {
        var progress: [Int64] = []
        let parts = (0..<100).map { _ in Data(repeating: 0x41, count: 1_024) }

        let result = try await store.store(paperID: "local-1", chunks: chunks(parts), maxBytes: 1_000_000) { progress.append($0) }

        #expect(result == .notPDF)
        #expect(progress.isEmpty)
    }

    @Test func stopsAtTheSizeLimitAndLeavesNothing() async throws {
        let body = Self.pdf + Data(repeating: 0x20, count: 200)

        #expect(try await store.store(paperID: "local-1", chunks: chunks([body]), maxBytes: 100) { _ in } == .tooLarge)
        #expect(names().isEmpty)
    }

    @Test func aFileExactlyAtTheLimitIsStored() async throws {
        let result = try await store.store(paperID: "local-1", chunks: chunks([Self.pdf]), maxBytes: Int64(Self.pdf.count)) { _ in }

        #expect(result == .stored(size: Int64(Self.pdf.count)))
    }

    @Test func storingAgainReplacesTheFile() async throws {
        _ = try await store.store(paperID: "local-1", chunks: chunks([Self.pdf]), maxBytes: 1_000) { _ in }
        let second = Self.pdf + Data("% second\n".utf8)

        _ = try await store.store(paperID: "local-1", chunks: chunks([second]), maxBytes: 1_000) { _ in }

        #expect(try Data(contentsOf: store.file(paperID: "local-1")) == second)
        #expect(names() == ["local-1.pdf"])
    }

    @Test func aRejectedReplacementKeepsTheCurrentFile() async throws {
        _ = try await store.store(paperID: "local-1", chunks: chunks([Self.pdf]), maxBytes: 1_000) { _ in }

        _ = try await store.store(paperID: "local-1", chunks: chunks([Data("<html></html>".utf8)]), maxBytes: 1_000) { _ in }

        #expect(try Data(contentsOf: store.file(paperID: "local-1")) == Self.pdf)
        #expect(names() == ["local-1.pdf"])
    }

    @Test func aStreamThatBreaksOffLeavesNoPartFile() async throws {
        await #expect(throws: URLError.self) {
            try await store.store(paperID: "local-1", chunks: chunks([Self.pdf, Self.pdf], failingAfter: 1), maxBytes: 1_000) { _ in }
        }
        #expect(names().isEmpty)
    }

    @Test func aCancelledStoreKeepsNothing() async throws {
        let (stream, continuation) = AsyncThrowingStream<Data, Error>.makeStream()
        let task = Task { try await store.store(paperID: "local-1", chunks: stream, maxBytes: 1_000) { _ in } }
        continuation.yield(Self.pdf)
        task.cancel()

        await #expect(throws: CancellationError.self) { try await task.value }
        #expect(names().isEmpty)
    }

    @Test func copyingAFileStoresIt() throws {
        let source = FileManager.default.temporaryDirectory.appending(path: "source-\(UUID().uuidString).pdf")
        try Self.pdf.write(to: source)

        #expect(try store.store(paperID: "local-1", copying: source, maxBytes: 1_000) == .stored(size: Int64(Self.pdf.count)))
        #expect(try Data(contentsOf: store.file(paperID: "local-1")) == Self.pdf)
    }

    @Test func deleteRemovesTheFile() async throws {
        _ = try await store.store(paperID: "local-1", chunks: chunks([Self.pdf]), maxBytes: 1_000) { _ in }

        store.delete(paperID: "local-1")

        #expect(names().isEmpty)
    }

    @Test func sweepKeepsListedIDsAndRemovesTheRest() throws {
        try write("local-1.pdf")
        try write("local-2.pdf")
        try write("notes.txt")

        store.sweep(keeping: ["local-1"])

        #expect(names() == ["local-1.pdf", "notes.txt"])
    }

    @Test func sweepRemovesPartFiles() throws {
        try write("local-1.pdf")
        try write("local-1-\(UUID().uuidString).part")

        store.sweep(keeping: ["local-1"])

        #expect(names() == ["local-1.pdf"])
    }

    @Test func stageKeepsAPartFileAndCommitMovesItIntoPlace() throws {
        let source = directory.appending(path: "in.pdf")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("%PDF-1.4 hello".utf8).write(to: source)
        guard case let .staged(url, size) = try store.stage(prefix: "restore", copying: source, maxBytes: 1024) else {
            Issue.record("not staged")
            return
        }
        #expect(url.lastPathComponent.hasSuffix(".part"))
        #expect(size == 14)
        #expect(!FileManager.default.fileExists(atPath: store.file(paperID: "p1").path))

        try store.commit(staged: url, paperID: "p1")

        #expect(!FileManager.default.fileExists(atPath: url.path))
        #expect(try String(contentsOf: store.file(paperID: "p1"), encoding: .utf8) == "%PDF-1.4 hello")
    }

    @Test func stageRejectsANonPdfAndLeavesNothing() throws {
        let source = directory.appending(path: "page.html")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("<html>".utf8).write(to: source)
        #expect(try store.stage(prefix: "restore", copying: source, maxBytes: 1024) == .notPDF)
        #expect(try FileManager.default.contentsOfDirectory(atPath: directory.path) == ["page.html"])
    }

    @Test func sweepDeletesUncommittedStagedFiles() throws {
        let source = directory.appending(path: "in.pdf")
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        try Data("%PDF-1.4".utf8).write(to: source)
        guard case let .staged(url, _) = try store.stage(prefix: "restore", copying: source, maxBytes: 1024) else {
            Issue.record("not staged")
            return
        }
        #expect(FileManager.default.fileExists(atPath: url.path))
        store.sweep(keeping: [])
        #expect(!FileManager.default.fileExists(atPath: url.path))
    }

    @Test func sweepWithNoFolderYetDoesNothing() {
        store.sweep(keeping: [])

        #expect(!FileManager.default.fileExists(atPath: directory.path))
    }
}
