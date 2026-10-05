import Foundation
import HashiyaData
import HashiyaDiagnostics
import Observation

public enum RestoreState: Equatable, Sendable {
    case loading
    case invalid(OpenFailure)
    case preview(RestorePreview)
    case applying(progress: Double)
    case done(RestoreResult)
    case failed(BackupError)
}

/// The Restore screen: opens a backup file, shows what restoring it would do, then merges it into the library.
@Observable @MainActor public final class RestoreViewModel {
    public internal(set) var state: RestoreState = .loading
    @ObservationIgnored private let source: URL
    @ObservationIgnored private let backup: any LibraryBackup
    @ObservationIgnored private var prepared: PreparedBackup?
    @ObservationIgnored private var opened = false
    /// Set by `cancel`: a copy that `open` prepares afterwards is discarded at once.
    @ObservationIgnored private var cancelled = false
    @ObservationIgnored private let onSourceRead: () -> Void
    @ObservationIgnored private let diagnostics: Diagnostics

    /// `onSourceRead` runs once `open` has made its own copy of `source` (or given up on it), so the caller can delete
    /// a source it no longer needs.
    public init(source: URL, backup: any LibraryBackup, onSourceRead: @escaping () -> Void = {},
                diagnostics: Diagnostics = .none) {
        self.source = source
        self.backup = backup
        self.onSourceRead = onSourceRead
        self.diagnostics = diagnostics
    }

    /// Opens the file once, however often the view appears.
    public func load() async {
        guard !opened else { return }
        opened = true
        let result = await backup.open(source)
        onSourceRead()
        // The screen went away while the file was being read: nothing will restore or discard this copy.
        if cancelled || Task.isCancelled {
            if case let .ready(prepared, _) = result { backup.discard(prepared) }
            return
        }
        switch result {
        case let .ready(prepared, preview):
            self.prepared = prepared
            state = .preview(preview)
        case let .failed(reason):
            state = .invalid(reason)
        }
    }

    public func confirm() {
        guard case .preview = state, let prepared else { return }
        state = .applying(progress: 0)
        diagnostics.crash.setKey(.backupInProgress, BackupPhase.restore)
        // Not tied to the view: a restore that has started finishes even if the screen goes away.
        Task { [backup, diagnostics] in
            let outcome: RestoreState
            do {
                let result = try await backup.apply(prepared) { progress in
                    Task { @MainActor in
                        if case .applying = self.state { self.state = .applying(progress: progress) }
                    }
                }
                outcome = .done(result)
            } catch let error as BackupError {
                if error == .writeFailed || error == .unreadable { diagnostics.crash.record(error, site: .restore) }
                outcome = .failed(error)
            } catch {
                if !(error is CancellationError) { diagnostics.crash.record(error, site: .unexpectedUiError) }
                // Never leave the screen on the progress bar.
                outcome = .failed(.writeFailed)
            }
            backup.discard(prepared)
            self.prepared = nil
            diagnostics.crash.setKey(.backupInProgress, BackupPhase.none)
            self.state = outcome
        }
    }

    /// Discards the prepared copy, or the one a `load` still running prepares. Does nothing while a restore is running:
    /// it finishes and discards it itself.
    public func cancel() {
        if case .applying = state { return }
        cancelled = true
        if let prepared { backup.discard(prepared) }
        prepared = nil
    }
}
