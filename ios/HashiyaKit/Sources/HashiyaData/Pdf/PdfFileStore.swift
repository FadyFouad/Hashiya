import Darwin
import Foundation
import HashiyaDatabase

/// What storing a PDF did.
public enum StoreResult: Equatable, Sendable {
    case stored(size: Int64)
    /// No `%PDF-` in the first 1024 bytes: a web page, a login wall, another file type.
    case notPDF
    /// Over the size limit; nothing was kept.
    case tooLarge
}

/// Writing the file failed (the disk is full, or the move didn't happen). Not a network failure, so callers never mistake it
/// for the source failing.
public struct PdfWriteError: Error, Sendable {}

/// Owns one folder of PDFs named `<paperID>.pdf`. Callers run one store per paper at a time.
public struct PdfFileStore: Sendable {
    /// The largest PDF the app stores; a download or attach past it stops and keeps nothing.
    public static let maxPdfBytes: Int64 = 100 * 1024 * 1024
    /// Suffix of the files a store writes before moving them into place; the sweep removes any left behind.
    static let partSuffix = ".part"
    /// PDF readers accept the `%PDF-` header anywhere in the first 1024 bytes, after a short preamble.
    static let headerWindow = 1024
    private static let chunkSize = 64 * 1024
    private static let header = Array("%PDF-".utf8)

    public let directory: URL

    public init(directory: URL) {
        self.directory = directory
    }

    /// The folder next to the library database in the App Group container. The Share Extension never uses it.
    public static func live(folderName: String = "pdfs") throws -> PdfFileStore {
        let database = try HashiyaDatabase.sharedDatabaseURL()
        return PdfFileStore(directory: database.deletingLastPathComponent().appending(path: folderName, directoryHint: .isDirectory))
    }

    public func file(paperID: String) -> URL {
        directory.appending(path: "\(paperID).pdf", directoryHint: .notDirectory)
    }

    /// Copies `chunks` to a `.part` file, checks it is a PDF and at most `maxBytes`, then moves it over `<paperID>.pdf`.
    /// A rejected, failed or cancelled store leaves the current file as it was and no `.part` file. `onProgress` gets the
    /// bytes copied so far. Errors from `chunks` are rethrown; write failures throw `PdfWriteError`; cancelling throws
    /// `CancellationError`.
    public func store(
        paperID: String,
        chunks: AsyncThrowingStream<Data, Error>,
        maxBytes: Int64,
        onProgress: (Int64) -> Void
    ) async throws -> StoreResult {
        let writer = try PartWriter(directory: directory, paperID: paperID)
        do {
            for try await chunk in chunks {
                if let verdict = try writer.append(chunk, maxBytes: maxBytes) {
                    writer.discard()
                    return verdict
                }
                onProgress(writer.total)
            }
            // Cancelling ends the stream early without an error; a partial file must never be stored.
            try Task.checkCancellation()
            return try writer.finish(into: file(paperID: paperID))
        } catch {
            writer.discard()
            throw error
        }
    }

    /// `store` for a local file, such as one picked in Files. Read errors from `source` are rethrown.
    public func store(paperID: String, copying source: URL, maxBytes: Int64) throws -> StoreResult {
        let reader = try FileHandle(forReadingFrom: source)
        defer { try? reader.close() }
        let writer = try PartWriter(directory: directory, paperID: paperID)
        do {
            while let chunk = try reader.read(upToCount: Self.chunkSize), !chunk.isEmpty {
                if let verdict = try writer.append(chunk, maxBytes: maxBytes) {
                    writer.discard()
                    return verdict
                }
            }
            return try writer.finish(into: file(paperID: paperID))
        } catch {
            writer.discard()
            throw error
        }
    }

    public func delete(paperID: String) {
        try? FileManager.default.removeItem(at: file(paperID: paperID))
    }

    /// Deletes every PDF whose paper ID isn't in `keep`, and every `.part` file a store never finished. Other files stay.
    /// It deletes a `.part` file that is still being written too, so it must not run while a store is in flight:
    /// `GRDBPdfRepository.sweepOrphans` keeps the two apart.
    public func sweep(keeping keep: Set<String>) {
        guard let names = try? FileManager.default.contentsOfDirectory(atPath: directory.path) else { return }
        for name in names {
            let orphan = name.hasSuffix(Self.partSuffix) || (name.hasSuffix(".pdf") && !keep.contains(String(name.dropLast(4))))
            if orphan { try? FileManager.default.removeItem(at: directory.appending(path: name)) }
        }
    }

    static func hasHeader(_ head: Data) -> Bool {
        let bytes = [UInt8](head)
        guard bytes.count >= header.count else { return false }
        return (0...(bytes.count - header.count)).contains { start in bytes[start..<(start + header.count)].elementsEqual(header) }
    }
}

/// One `.part` file being written.
private final class PartWriter {
    private let url: URL
    private let handle: FileHandle
    private var head = Data()
    private(set) var total: Int64 = 0

    init(directory: URL, paperID: String) throws {
        let url = directory.appending(path: "\(paperID)-\(UUID().uuidString)\(PdfFileStore.partSuffix)", directoryHint: .notDirectory)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            guard FileManager.default.createFile(atPath: url.path, contents: nil) else { throw PdfWriteError() }
            handle = try FileHandle(forWritingTo: url)
        } catch {
            throw PdfWriteError()
        }
        self.url = url
    }

    /// Nil to go on; a verdict to stop: too large, or 1024 bytes without the header.
    func append(_ chunk: Data, maxBytes: Int64) throws -> StoreResult? {
        total += Int64(chunk.count)
        if total > maxBytes { return .tooLarge }
        if head.count < PdfFileStore.headerWindow {
            head.append(chunk.prefix(PdfFileStore.headerWindow - head.count))
            // A web page is usually small, but a large non-PDF shouldn't be read to the end before it is rejected.
            if head.count == PdfFileStore.headerWindow, !PdfFileStore.hasHeader(head) { return .notPDF }
        }
        do {
            try handle.write(contentsOf: chunk)
        } catch {
            throw PdfWriteError()
        }
        return nil
    }

    func finish(into target: URL) throws -> StoreResult {
        do {
            // Without this, a power loss just after the move can leave an empty or partial `<paperID>.pdf`.
            try handle.synchronize()
            try handle.close()
        } catch {
            throw PdfWriteError()
        }
        guard PdfFileStore.hasHeader(head) else {
            discard()
            return .notPDF
        }
        // rename(2) replaces the target atomically within one folder.
        guard rename(url.path, target.path) == 0 else { throw PdfWriteError() }
        return .stored(size: total)
    }

    func discard() {
        try? handle.close()
        try? FileManager.default.removeItem(at: url)
    }
}
