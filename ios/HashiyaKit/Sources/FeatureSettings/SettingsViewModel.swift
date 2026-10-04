import Foundation
import HashiyaData
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

    @ObservationIgnored private let pdfs: any PdfRepository
    @ObservationIgnored private let libraryBackup: any LibraryBackup
    @ObservationIgnored private var exportTask: Task<Void, Never>?
    @ObservationIgnored private let preferences: any UserPreferencesRepository
    @ObservationIgnored private let observations = TaskBag()
    private var storedKey: String?
    private var editedKey: String?

    public init(preferences: any UserPreferencesRepository, pdfs: any PdfRepository, backup: any LibraryBackup) {
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
        backup.export = .choosing(includePdfs: false)
    }

    public func setIncludePdfs(_ include: Bool) {
        if case .choosing = backup.export { backup.export = .choosing(includePdfs: include) }
    }

    /// Builds the archive; the state becomes `.readyToSave` (or `.idle` with a message when it fails).
    public func confirmExport() {
        guard case let .choosing(includePdfs) = backup.export else { return }
        backup.export = .building(includePdfs: includePdfs, progress: 0)
        // Called off the main actor, so it hops back before touching the state.
        let onProgress: @Sendable (Double) -> Void = { [weak self] progress in
            Task { @MainActor [weak self] in
                guard let self, case let .building(include, _) = self.backup.export else { return }
                self.backup.export = .building(includePdfs: include, progress: progress)
            }
        }
        exportTask = Task { [weak self, libraryBackup] in
            do {
                let file = try await libraryBackup.export(includePdfs: includePdfs, onProgress: onProgress)
                guard let self, !Task.isCancelled else {
                    libraryBackup.discard(file)
                    return
                }
                self.backup.export = .readyToSave(file)
            } catch let error as BackupError {
                guard !Task.isCancelled else { return }
                self?.backup.export = .idle
                self?.backup.message = .exportFailed(error)
            } catch {
                // Cancelled: `cancelExport` already reset the state.
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

    /// What the save panel did with the file; `saved` is false when it was cancelled or failed.
    public func exportFinished(saved: Bool) {
        guard case let .readyToSave(file) = backup.export else { return }
        // `.fileMover` moved the file on success; this removes its folder, and the file too when it wasn't moved.
        libraryBackup.discard(file)
        backup.export = .idle
        backup.message = saved ? .exported(missingPdfs: file.missingPdfs) : nil
    }

    public func dismissMessage() {
        backup.message = nil
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

public enum BackupMessage: Equatable, Sendable {
    case exported(missingPdfs: Int)
    case exportFailed(BackupError)
    case exportCancelled
}
