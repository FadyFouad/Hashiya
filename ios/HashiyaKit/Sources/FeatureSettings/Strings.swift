import HashiyaData
import HashiyaDesignSystem
import HashiyaModel
import SwiftUI

/// This target's strings.
@MainActor
enum L10n {
    static func string(_ key: String) -> String {
        HashiyaStrings.string(key, bundle: .module)
    }

    static func format(_ key: String, _ arguments: any CVarArg...) -> String {
        HashiyaStrings.format(key, bundle: .module, arguments)
    }

    /// "Downloaded PDFs · 2.4 MB · 3 files".
    static func downloadedPdfs(bytes: Int64, count: Int) -> String {
        format("settings.downloadedPdfs", Int64(count), PaperFormat.fileSize(bytes), PaperFormat.number(count))
    }

    /// "Attached PDFs · 1 MB · 1 file".
    static func attachedPdfs(bytes: Int64, count: Int) -> String {
        format("settings.attachedPdfs", Int64(count), PaperFormat.fileSize(bytes), PaperFormat.number(count))
    }

    /// The Delete downloaded PDFs confirmation.
    static func deleteDownloadedMessage(count: Int) -> String {
        format("settings.deleteDownloadedMessage", Int64(count), PaperFormat.number(count))
    }

    /// "182 papers · 6 collections".
    static func backupCounts(papers: Int, collections: Int) -> String {
        format("backup.counts", format("backup.papers", Int64(papers)), format("backup.collections", Int64(collections)))
    }

    /// "41 PDFs, 238 MB".
    static func backupPdfsSize(count: Int, bytes: Int64) -> String {
        format("backup.pdfsSize", Int64(count), PaperFormat.fileSize(bytes))
    }

    /// The banner after an export; nil when there is nothing to say.
    static func backupMessage(_ message: BackupMessage) -> String? {
        switch message {
        case let .exported(missingPdfs):
            missingPdfs == 0 ? string("backup.exported") : format("backup.exportedMissing", Int64(missingPdfs))
        case .exportFailed(.noSpace):
            string("backup.exportFailedSpace")
        case .exportFailed:
            string("backup.exportFailed")
        case .exportCancelled:
            nil
        }
    }

    /// "Backup from 4 Oct 2026".
    static func restoreFromDate(_ exportedAt: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(exportedAt) / 1000)
        let formatted = date.formatted(.dateTime.day().month().year().locale(HashiyaLanguage.locale))
        return format("restore.fromDate", formatted)
    }

    /// "182 papers · 6 collections · 41 PDFs".
    static func restoreCounts(_ preview: RestorePreview) -> String {
        format(
            "restore.counts",
            format("backup.papers", Int64(preview.papers)),
            format("backup.collections", Int64(preview.collections)),
            format("restore.pdfs", Int64(preview.pdfs))
        )
    }

    static func restoreNewPapers(_ count: Int) -> String {
        format("restore.newPapers", Int64(count))
    }

    static func restoreExistingPapers(_ count: Int) -> String {
        format("restore.existingPapers", Int64(count))
    }

    static func restoreSkippedPapers(_ count: Int) -> String {
        format("restore.skippedPapers", Int64(count))
    }

    static func restoreDoneBody(_ result: RestoreResult) -> String {
        format(
            "restore.doneBody",
            Int64(result.papersAdded),
            Int64(result.notesAdded),
            Int64(result.collectionsCreated),
            Int64(result.pdfsAdded)
        )
    }

    static func restoreDoneMissingPdfs(_ count: Int) -> String {
        format("restore.doneMissingPdfs", Int64(count))
    }

    static func restoreDoneSkipped(_ count: Int) -> String {
        format("restore.doneSkipped", Int64(count))
    }

    /// Why a file can't be restored.
    static func restoreInvalid(_ reason: OpenFailure) -> String {
        switch reason {
        case .notABackup: string("restore.notBackup")
        case .newerFormat: string("restore.newer")
        case .damaged: string("restore.damaged")
        case .unreadable: string("restore.unreadable")
        }
    }

    /// Why a restore failed.
    static func restoreFailed(_ error: BackupError) -> String {
        switch error {
        case .noSpace: string("restore.failedSpace")
        case .busy: string("restore.failedBusy")
        case .writeFailed, .unreadable: string("restore.failed")
        }
    }
}
