import Foundation

/// Where an export's `.bib` file is written before it is shared. Only the latest export is kept.
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

    /// Deletes earlier files in the directory, then writes `bibtex` as UTF-8 to `<name>.bib` atomically, and returns its URL.
    public func write(_ bibtex: String, name: String) throws -> URL {
        let files = FileManager.default
        try files.createDirectory(at: directory, withIntermediateDirectories: true)
        for earlier in try files.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil) {
            try files.removeItem(at: earlier)
        }
        let url = directory.appendingPathComponent(name + ".bib", isDirectory: false)
        try Data(bibtex.utf8).write(to: url, options: .atomic)
        return url
    }

    private static let unsafe: Set<Unicode.Scalar> = ["/", "\\", ":", "*", "?", "\"", "<", ">", "|"]

    /// "hashiya-library" for the whole library; otherwise the collection's name with characters files can't hold (`/ \ : * ? " < >
    /// |` and ASCII control characters, as Android's `\p{Cntrl}`) replaced by "-", trimmed, and "collection" when nothing is left.
    /// No extension: `write` adds ".bib".
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
}
