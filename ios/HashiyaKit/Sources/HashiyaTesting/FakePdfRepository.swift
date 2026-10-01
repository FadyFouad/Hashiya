import Foundation
import HashiyaData
import HashiyaModel
import os

/// Scripted PDFs, recording every call. Tests drive what a download does with `setDownload` and `setPdf`.
public final class FakePdfRepository: PdfRepository {
    public struct Failure: Error {}

    private struct State: DeferredYields {
        var pdfs: [String: PaperPdf] = [:]
        var downloadStates: [String: DownloadState] = [:]
        var attachResult = AttachResult.done
        var storage = PdfStorage(downloadedBytes: 0, downloadedCount: 0, attachedBytes: 0, attachedCount: 0)
        var files: [String: URL] = [:]
        var failWrites = false
        var downloads: [String] = []
        var cancels: [String] = []
        var attaches: [(openAlexID: String, url: URL)] = []
        var removals: [String] = []
        var lastPages: [(openAlexID: String, page: Int)] = []
        var discarded: [String] = []
        var deleteDownloadedCalls = 0
        var sweeps = 0
        var pdfSubscriptions: [UUID: (openAlexID: String, continuation: AsyncStream<PaperPdf?>.Continuation)] = [:]
        var downloadSubscriptions: [UUID: (openAlexID: String, continuation: AsyncStream<DownloadState?>.Continuation)] = [:]
        var pending: [@Sendable () -> Void] = []

        mutating func publishPdf(_ openAlexID: String) {
            let value = pdfs[openAlexID]
            for subscription in pdfSubscriptions.values where subscription.openAlexID == openAlexID {
                let continuation = subscription.continuation
                pending.append { continuation.yield(value) }
            }
        }

        mutating func publishDownload(_ openAlexID: String) {
            let value = downloadStates[openAlexID]
            for subscription in downloadSubscriptions.values where subscription.openAlexID == openAlexID {
                let continuation = subscription.continuation
                pending.append { continuation.yield(value) }
            }
        }
    }

    private let state = OSAllocatedUnfairLock<State>(uncheckedState: State())

    public init() {}

    public var downloads: [String] { state.update { $0.downloads } }
    public var cancels: [String] { state.update { $0.cancels } }
    public var attaches: [(openAlexID: String, url: URL)] { state.update { $0.attaches } }
    public var removals: [String] { state.update { $0.removals } }
    public var lastPages: [(openAlexID: String, page: Int)] { state.update { $0.lastPages } }
    /// OpenAlex IDs of the removed papers passed to `discardRemoved`, in order.
    public var discarded: [String] { state.update { $0.discarded } }
    public var deleteDownloadedCalls: Int { state.update { $0.deleteDownloadedCalls } }
    public var sweeps: Int { state.update { $0.sweeps } }

    /// Sets (nil clears) the paper's PDF and re-emits.
    public func setPdf(_ openAlexID: String, _ pdf: PaperPdf?) {
        state.update { state in
            state.pdfs[openAlexID] = pdf
            state.publishPdf(openAlexID)
        }
    }

    /// Sets (nil clears) the paper's download state and re-emits.
    public func setDownload(_ openAlexID: String, _ download: DownloadState?) {
        state.update { state in
            state.downloadStates[openAlexID] = download
            state.publishDownload(openAlexID)
        }
    }

    /// What every `attach` returns; `.done` also stores a 1 KB attached PDF.
    public func setAttachResult(_ result: AttachResult) { state.update { $0.attachResult = result } }
    public func setStorage(_ storage: PdfStorage) { state.update { $0.storage = storage } }
    /// The file `pdfFile` returns for the paper while it has a PDF.
    public func setFile(_ url: URL, for openAlexID: String) { state.update { $0.files[openAlexID] = url } }
    /// When true, `remove`, `setLastPage`, `storage` and `deleteDownloaded` throw `Failure`.
    public func setFailWrites(_ fail: Bool) { state.update { $0.failWrites = fail } }

    public func observePdf(openAlexID: String) -> AsyncStream<PaperPdf?> {
        let id = UUID()
        return AsyncStream { continuation in
            state.update { state in
                state.pdfSubscriptions[id] = (openAlexID, continuation)
                continuation.yield(state.pdfs[openAlexID])
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.update { $0.pdfSubscriptions.removeValue(forKey: id) }
            }
        }
    }

    public func observeDownload(openAlexID: String) -> AsyncStream<DownloadState?> {
        let id = UUID()
        return AsyncStream { continuation in
            state.update { state in
                state.downloadSubscriptions[id] = (openAlexID, continuation)
                continuation.yield(state.downloadStates[openAlexID])
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.update { $0.downloadSubscriptions.removeValue(forKey: id) }
            }
        }
    }

    /// Records the call; tests drive what follows with `setDownload` and `setPdf`.
    public func download(openAlexID: String) {
        state.update { $0.downloads.append(openAlexID) }
    }

    public func cancelDownload(openAlexID: String) {
        state.update { state in
            state.cancels.append(openAlexID)
            state.downloadStates[openAlexID] = nil
            state.publishDownload(openAlexID)
        }
    }

    public func attach(openAlexID: String, from url: URL) async -> AttachResult {
        state.update { state in
            state.attaches.append((openAlexID, url))
            if state.attachResult == .done {
                state.downloadStates[openAlexID] = nil
                state.pdfs[openAlexID] = PaperPdf(source: .attached, sizeBytes: 1_024, addedAt: 0)
                state.publishDownload(openAlexID)
                state.publishPdf(openAlexID)
            }
            return state.attachResult
        }
    }

    public func remove(openAlexID: String) async throws {
        try state.update { state in
            if state.failWrites { throw Failure() }
            state.removals.append(openAlexID)
            state.pdfs[openAlexID] = nil
            state.publishPdf(openAlexID)
        }
    }

    public func setLastPage(openAlexID: String, page: Int) async throws {
        try state.update { state in
            if state.failWrites { throw Failure() }
            state.lastPages.append((openAlexID, page))
            if var pdf = state.pdfs[openAlexID] {
                pdf.lastPage = page
                state.pdfs[openAlexID] = pdf
                state.publishPdf(openAlexID)
            }
        }
    }

    /// While a PDF is set: the file from `setFile`, or a placeholder path, so tests that never read it needn't set one.
    public func pdfFile(openAlexID: String) async -> URL? {
        state.update { state in
            guard state.pdfs[openAlexID] != nil else { return nil }
            return state.files[openAlexID] ?? URL(fileURLWithPath: "/fake-pdfs/\(openAlexID).pdf")
        }
    }

    public func storage() async throws -> PdfStorage {
        try state.update { state in
            if state.failWrites { throw Failure() }
            return state.storage
        }
    }

    /// Drops downloaded PDFs and zeroes the downloaded numbers in `storage`; attached ones stay.
    public func deleteDownloaded() async throws {
        try state.update { state in
            if state.failWrites { throw Failure() }
            state.deleteDownloadedCalls += 1
            for (id, pdf) in state.pdfs where pdf.source == .downloaded {
                state.pdfs[id] = nil
                state.publishPdf(id)
            }
            state.storage = PdfStorage(
                downloadedBytes: 0,
                downloadedCount: 0,
                attachedBytes: state.storage.attachedBytes,
                attachedCount: state.storage.attachedCount
            )
        }
    }

    public func discardRemoved(_ removed: RemovedPaper) async {
        state.update { $0.discarded.append(removed.paper.openAlexID) }
    }

    public func sweepOrphans() async {
        state.update { $0.sweeps += 1 }
    }
}
