import Foundation
import HashiyaData
import os

/// A scripted backup, recording every call. Tests set what each call returns; `exportGate` holds an export until they let it go.
public final class FakeLibraryBackup: LibraryBackup {
    private struct State {
        var summary = BackupSummary(papers: 0, collections: 0, pdfCount: 0, pdfBytes: 0)
        var exportFailure: BackupError?
        var missingPdfs = 0
        var openResult = OpenResult.failed(.notABackup)
        var applyResult = RestoreResult(papersAdded: 0, notesAdded: 0, collectionsCreated: 0, pdfsAdded: 0, pdfsMissing: 0, papersSkipped: 0)
        var applyFailure: BackupError?
        var exportGate: AsyncStream<Void>?
        var exports: [Bool] = []
        var discardedExports = 0
        var opened: [URL] = []
        var applied: [PreparedBackup] = []
        var discardedBackups: [PreparedBackup] = []
    }

    private let state = OSAllocatedUnfairLock<State>(uncheckedState: State())

    public init() {}

    public var summary: BackupSummary {
        get { state.withLock { $0.summary } }
        set { state.withLock { $0.summary = newValue } }
    }

    /// When set, `export` throws it.
    public var exportFailure: BackupError? {
        get { state.withLock { $0.exportFailure } }
        set { state.withLock { $0.exportFailure = newValue } }
    }

    /// The `missingPdfs` of every exported file.
    public var missingPdfs: Int {
        get { state.withLock { $0.missingPdfs } }
        set { state.withLock { $0.missingPdfs = newValue } }
    }

    /// What `open` returns.
    public var openResult: OpenResult {
        get { state.withLock { $0.openResult } }
        set { state.withLock { $0.openResult = newValue } }
    }

    /// What `apply` returns.
    public var applyResult: RestoreResult {
        get { state.withLock { $0.applyResult } }
        set { state.withLock { $0.applyResult = newValue } }
    }

    /// When set, `apply` throws it.
    public var applyFailure: BackupError? {
        get { state.withLock { $0.applyFailure } }
        set { state.withLock { $0.applyFailure = newValue } }
    }

    /// When set, `export` waits for one value from it (or for its cancellation) before it returns.
    public var exportGate: AsyncStream<Void>? {
        get { state.withLock { $0.exportGate } }
        set { state.withLock { $0.exportGate = newValue } }
    }

    /// The `includePdfs` of every export, in order.
    public var exports: [Bool] { state.withLock { $0.exports } }
    public var discardedExports: Int { state.withLock { $0.discardedExports } }
    public var opened: [URL] { state.withLock { $0.opened } }
    public var applied: [PreparedBackup] { state.withLock { $0.applied } }
    public var discardedBackups: [PreparedBackup] { state.withLock { $0.discardedBackups } }

    /// A backup for `openResult` and `apply`.
    public static func preparedBackup() -> PreparedBackup {
        PreparedBackup.forTesting(url: URL(fileURLWithPath: "/fake-backup/open-1/backup.hashiya"))
    }

    public func summary() async throws -> BackupSummary {
        state.withLock { $0.summary }
    }

    public func export(includePdfs: Bool, onProgress: @escaping @Sendable (Double) -> Void) async throws -> ExportedFile {
        state.withLock { $0.exports.append(includePdfs) }
        onProgress(0.5)
        if let gate = state.withLock({ $0.exportGate }) {
            var values = gate.makeAsyncIterator()
            _ = await values.next()
        }
        try Task.checkCancellation()
        let (failure, missing) = state.withLock { ($0.exportFailure, $0.missingPdfs) }
        if let failure { throw failure }
        let fileName = "Hashiya-library-2026-10-04.hashiya"
        return ExportedFile(url: URL(fileURLWithPath: "/fake-backup/export-1/\(fileName)"), fileName: fileName, missingPdfs: missing)
    }

    public func discard(_ exported: ExportedFile) {
        state.withLock { $0.discardedExports += 1 }
    }

    public func open(_ source: URL) async -> OpenResult {
        state.withLock { state in
            state.opened.append(source)
            return state.openResult
        }
    }

    public func apply(_ backup: PreparedBackup, onProgress: @escaping @Sendable (Double) -> Void) async throws -> RestoreResult {
        state.withLock { $0.applied.append(backup) }
        onProgress(1)
        let (failure, result) = state.withLock { ($0.applyFailure, $0.applyResult) }
        if let failure { throw failure }
        return result
    }

    public func discard(_ backup: PreparedBackup) {
        state.withLock { $0.discardedBackups.append(backup) }
    }
}
