import Foundation
import HashiyaData
import HashiyaDiagnostics
import HashiyaModel
import Observation
import os

@Observable
@MainActor
public final class SettingsViewModel {
    /// True while a key of the user's own is stored.
    public internal(set) var usingUserKey = false
    /// The space PDFs use, once `loadStorage()` has run.
    public internal(set) var storage: PdfStorage?

    /// The Backup section's numbers, the export in progress and the last result.
    public internal(set) var backup = BackupState()

    /// The Privacy section's two switches; both start on.
    public internal(set) var crashReportsEnabled: Bool
    public internal(set) var analyticsEnabled: Bool

    @ObservationIgnored private let privacy: PrivacySettings
    @ObservationIgnored private let diagnostics: Diagnostics
    /// Whether the export being saved holds PDFs, as chosen in `confirmExport()`.
    @ObservationIgnored private var exportIncludesPdfs = false
    @ObservationIgnored private let pdfs: any PdfRepository
    @ObservationIgnored private let libraryBackup: any LibraryBackup
    @ObservationIgnored private var exportTask: Task<Void, Never>?
    @ObservationIgnored private let preferences: any UserPreferencesRepository
    @ObservationIgnored private let observations = TaskBag()
    private var storedKey: String?
    private var editedKey: String?

    public init(preferences: any UserPreferencesRepository, pdfs: any PdfRepository, backup: any LibraryBackup,
        privacy: PrivacySettings = PrivacySettings(),
        diagnostics: Diagnostics = .none
    ) {
        self.privacy = privacy
        self.diagnostics = diagnostics
        // Plain assignments, like the key below: reading observable state here would make the creator observe it.
        crashReportsEnabled = privacy.crashReportsEnabled
        analyticsEnabled = privacy.analyticsEnabled
        self.pdfs = pdfs
        libraryBackup = backup
        self.preferences = preferences
        // Seeded at once, so Settings never opens on "Using built-in key" while the stream starts.
        // Only written here, never read: SwiftUI creates this inside the sheet's observation scope, and a read
        // would make the sheet rebuild the view model (losing the typed key) on every stored-key update.
        let key = preferences.currentUserAPIKey
        storedKey = key
        usingUserKey = key != nil
        observations.add(Task { [weak self] in
            for await key in preferences.userAPIKeyUpdates() {
                guard let self else { return }
                self.storedKey = key
                self.usingUserKey = key != nil
            }
        })
    }

    /// The stored key until the user edits the field, then the edited text.
    public var keyInput: String {
        get { editedKey ?? storedKey ?? "" }
        set { editedKey = newValue }
    }

    /// Stores the field (trimmed; blank goes back to the built-in key) and ends the edit.
    public func save() async {
        await store(keyInput)
    }

    /// Removes the stored key and ends the edit.
    public func reset() async {
        await store("")
    }

    /// Reads the storage totals. The view calls this when Settings opens.
    public func loadStorage() async {
        do {
            storage = try await pdfs.storage()
        } catch {
            #if DEBUG
            Logger(subsystem: "com.etatech.hashiya", category: "settings").error("Reading the PDF storage failed")
            #endif
        }
    }

    /// Deletes every downloaded PDF (attached ones stay), then reads the totals again.
    public func deleteDownloaded() async {
        do {
            try await pdfs.deleteDownloaded()
        } catch {
            #if DEBUG
            Logger(subsystem: "com.etatech.hashiya", category: "settings").error("Deleting downloaded PDFs failed")
            #endif
        }
        await loadStorage()
    }

    /// Reads what a backup would hold. The view calls this when Settings appears, so the numbers aren't stale after a restore.
    public func loadBackupSummary() async {
        if let summary = try? await libraryBackup.summary() { backup.summary = summary }
    }

    /// Opens the Export screen's choices. Only from idle: an export being built or waiting to be saved stays.
    public func startExport() {
        guard backup.export == .idle else { return }
        // The Export screen leaves when a message appears, so the same message again must still be a change.
        backup.message = nil
        backup.export = .choosing(includePdfs: false)
    }

    public func setIncludePdfs(_ include: Bool) {
        if case .choosing = backup.export { backup.export = .choosing(includePdfs: include) }
    }

    /// Builds the archive; the state becomes `.readyToSave` (or `.idle` with a message when it fails).
    public func confirmExport() {
        guard case let .choosing(includePdfs) = backup.export else { return }
        exportIncludesPdfs = includePdfs
        backup.export = .building(includePdfs: includePdfs, progress: 0)
        diagnostics.crash.setKey(.backupInProgress, BackupPhase.export)
        // Called off the main actor, so it hops back before touching the state.
        let onProgress: @Sendable (Double) -> Void = { [weak self] progress in
            Task { @MainActor [weak self] in
                guard let self, case let .building(include, _) = self.backup.export else { return }
                self.backup.export = .building(includePdfs: include, progress: progress)
            }
        }
        exportTask = Task { [weak self, libraryBackup, diagnostics] in
            defer { diagnostics.crash.setKey(.backupInProgress, BackupPhase.none) }
            do {
                let file = try await libraryBackup.export(includePdfs: includePdfs, onProgress: onProgress)
                guard let self, !Task.isCancelled else {
                    libraryBackup.discard(file)
                    return
                }
                self.backup.export = .readyToSave(file)
            } catch let error as BackupError {
                guard !Task.isCancelled else { return }
                if error == .writeFailed { diagnostics.crash.record(error, site: .export) }
                self?.backup.export = .idle
                self?.backup.message = .exportFailed(error)
            } catch {
                // Cancelled: `cancelExport` already reset the state. Anything else ends the export with a message.
                guard !Task.isCancelled else { return }
                if !(error is CancellationError) { diagnostics.crash.record(error, site: .unexpectedUiError) }
                self?.backup.export = .idle
                self?.backup.message = .exportFailed(.writeFailed)
            }
        }
    }

    /// Stops a build in progress. Does nothing in any other state.
    public func cancelExport() {
        guard case .building = backup.export else { return }
        exportTask?.cancel()
        exportTask = nil
        backup.export = .idle
    }

    /// What the save panel did with the file. A cancelled save says nothing; a failed one says the export failed.
    public func exportFinished(_ outcome: SaveOutcome) {
        guard case let .readyToSave(file) = backup.export else { return }
        // `.fileMover` moved the file on success; this removes its folder, and the file too when it wasn't moved.
        libraryBackup.discard(file)
        backup.export = .idle
        switch outcome {
        case .saved:
            backup.message = .exported(missingPdfs: file.missingPdfs)
            diagnostics.analytics.log(.export(format: .backup, withPdfs: exportIncludesPdfs))
        case .cancelled: backup.message = nil
        case .failed: backup.message = .exportFailed(.writeFailed)
        }
    }

    public func dismissMessage() {
        backup.message = nil
    }

    public func setCrashReportsEnabled(_ enabled: Bool) {
        crashReportsEnabled = enabled
        privacy.setCrashReportsEnabled(enabled)
        diagnostics.crash.setEnabled(diagnostics.isLive && enabled)
    }

    public func setAnalyticsEnabled(_ enabled: Bool) {
        analyticsEnabled = enabled
        privacy.setAnalyticsEnabled(enabled)
        diagnostics.analytics.setEnabled(diagnostics.isLive && enabled)
    }

    private func store(_ key: String) async {
        do {
            try await preferences.setUserAPIKey(key)
            editedKey = nil
        } catch {
            #if DEBUG
            Logger(subsystem: "com.etatech.hashiya", category: "settings").error("Saving the API key failed")
            #endif
        }
    }
}

/// What the Backup section and the Export screen show.
public struct BackupState: Equatable, Sendable {
    public var summary: BackupSummary?
    public var export: ExportState = .idle
    public var message: BackupMessage?

    public init() {}
}

public enum ExportState: Equatable, Sendable {
    case idle
    case choosing(includePdfs: Bool)
    case building(includePdfs: Bool, progress: Double)
    /// The view presents `.fileMover` for the file; `exportFinished` reports what happened.
    case readyToSave(ExportedFile)
}

/// How the save panel ended.
public enum SaveOutcome: Equatable, Sendable {
    case saved
    case cancelled
    case failed
}

public enum BackupMessage: Equatable, Sendable {
    case exported(missingPdfs: Int)
    case exportFailed(BackupError)
    case exportCancelled
}
