import Foundation
import HashiyaModel

/// Where an export's file (`.bib` or `.rtf`) is written before it is shared. Only the latest export is kept.
public struct ExportFiles: Sendable {
    public let directory: URL

    public init(directory: URL) {
        self.directory = directory
    }

    /// `Caches/exports`: the system may clear it, and nothing there needs a backup.
    public static var live: ExportFiles {
        let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        return ExportFiles(directory: caches.appendingPathComponent("exports", isDirectory: true))
    }

    /// Deletes earlier files in the directory, then writes `contents` as UTF-8 to `fileName` atomically, and returns its URL.
    public func write(_ contents: String, fileName: String) throws -> URL {
        let files = FileManager.default
        try files.createDirectory(at: directory, withIntermediateDirectories: true)
        for earlier in try files.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) {
            try files.removeItem(at: earlier)
        }
        let url = directory.appendingPathComponent(fileName, isDirectory: false)
        try Data(contents.utf8).write(to: url, options: .atomic)
        return url
    }

    private static let unsafe: Set<Unicode.Scalar> = ["/", "\\", ":", "*", "?", "\"", "<", ">", "|"]

    /// "hashiya-library" for the whole library; otherwise the collection's name with characters files can't hold (`/ \ : * ? " < >
    /// |` and ASCII control characters, as Android's `\p{Cntrl}`) replaced by "-", trimmed, and "collection" when nothing is left.
    /// No extension.
    public static func fileName(collectionName: String?) -> String {
        guard let collectionName else { return "hashiya-library" }
        var safe = String.UnicodeScalarView()
        for scalar in collectionName.unicodeScalars {
            let control = scalar.value < 0x20 || scalar.value == 0x7F
            safe.append(unsafe.contains(scalar) || control ? "-" : scalar)
        }
        let trimmed = String(safe).trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? "collection" : trimmed
    }

    /// `Thesis.bib`, `Thesis – APA.rtf`, `Thesis – IEEE.rtf` (en dash with spaces), with the base name of `fileName(collectionName:)`.
    public static func fileName(collectionName: String?, style: CitationStyle) -> String {
        let base = fileName(collectionName: collectionName)
        switch style {
        case .bibtex: return base + ".bib"
        case .apa: return base + " \u{2013} APA.rtf"
        case .ieee: return base + " \u{2013} IEEE.rtf"
        }
    }
}
