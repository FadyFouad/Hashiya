import Foundation
import HashiyaData
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

    public init(source: URL, backup: any LibraryBackup) {
        self.source = source
        self.backup = backup
    }

    /// Opens the file once, however often the view appears.
    public func load() async {
        guard !opened else { return }
        opened = true
        switch await backup.open(source) {
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
        // Not tied to the view: a restore that has started finishes even if the screen goes away.
        Task { [backup] in
            let outcome: RestoreState
            do {
                let result = try await backup.apply(prepared) { progress in
                    Task { @MainActor in
                        if case .applying = self.state { self.state = .applying(progress: progress) }
                    }
                }
                outcome = .done(result)
            } catch let error as BackupError {
                outcome = .failed(error)
            } catch {
                // Never leave the screen on the progress bar.
                outcome = .failed(.writeFailed)
            }
            backup.discard(prepared)
            self.prepared = nil
            self.state = outcome
        }
    }

    /// Discards the prepared copy. Does nothing while a restore is running: it finishes and discards it itself.
    public func cancel() {
        if case .applying = state { return }
        if let prepared { backup.discard(prepared) }
        prepared = nil
    }
}
