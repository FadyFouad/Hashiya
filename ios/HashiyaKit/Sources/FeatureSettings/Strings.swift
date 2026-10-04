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
}

