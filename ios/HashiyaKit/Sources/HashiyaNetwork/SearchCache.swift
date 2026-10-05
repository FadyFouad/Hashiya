import CryptoKit
import Foundation
import os

/// Search and filter-list responses on disk: kept 24 hours, at most 5 MB, least recently used removed first. Each file
/// is named after a hash of the request without its key and starts with the time it was saved; its modification date
/// is when it was last used. Lives in Caches, so the system may clear it and it is never backed up.
public final class SearchCache: @unchecked Sendable {
    private let directory: URL
    private let maxBytes: Int
    private let lifetime: TimeInterval
    private let now: @Sendable () -> Date
    private let lock = OSAllocatedUnfairLock()
    private let files = FileManager.default

    public init(
        directory: URL,
        maxBytes: Int = 5_000_000,
        lifetime: TimeInterval = 24 * 60 * 60,
        now: @escaping @Sendable () -> Date = { Date() }
    ) {
        self.directory = directory
        self.maxBytes = maxBytes
        self.lifetime = lifetime
        self.now = now
    }

    /// `Caches/OpenAlexSearch`.
    public static func live() -> SearchCache? {
        FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first
            .map { SearchCache(directory: $0.appending(path: "OpenAlexSearch", directoryHint: .isDirectory)) }
    }

    /// The request without `api_key`, parameters sorted, so the same search hits whichever route sent it.
    public static func key(path: String, query: [(name: String, value: String)]) -> String {
        let parameters = query
            .filter { $0.name != "api_key" }
            .sorted { ($0.name, $0.value) < ($1.name, $1.value) }
            .map { "\($0.name)=\($0.value)" }
            .joined(separator: "&")
        return path + "?" + parameters
    }

    /// The stored body, if it is younger than the lifetime; marks it used.
    public func data(for key: String) -> Data? {
        lock.withLock {
            let file = fileURL(key)
            guard let contents = try? Data(contentsOf: file), contents.count >= 8 else { return nil }
            let savedAt = contents.prefix(8).withUnsafeBytes { $0.loadUnaligned(as: Double.self) }
            let date = now()
            let age = date.timeIntervalSince1970 - savedAt
            guard age >= 0, age < lifetime else {
                try? files.removeItem(at: file)
                return nil
            }
            try? files.setAttributes([.modificationDate: date], ofItemAtPath: file.path)
            return Data(contents.dropFirst(8))
        }
    }

    public func store(_ data: Data, for key: String) {
        lock.withLock {
            try? files.createDirectory(at: directory, withIntermediateDirectories: true)
            let date = now()
            var savedAt = date.timeIntervalSince1970
            let header = withUnsafeBytes(of: &savedAt) { Data($0) }
            let file = fileURL(key)
            guard (try? (header + data).write(to: file, options: .atomic)) != nil else { return }
            try? files.setAttributes([.modificationDate: date], ofItemAtPath: file.path)
            trim()
        }
    }

    /// Inside the lock: removes the least recently used files until the total fits.
    private func trim() {
        let keys: [URLResourceKey] = [.fileSizeKey, .contentModificationDateKey]
        guard let urls = try? files.contentsOfDirectory(at: directory, includingPropertiesForKeys: keys) else { return }
        var entries = urls.map { url -> (url: URL, size: Int, used: Date) in
            let values = try? url.resourceValues(forKeys: Set(keys))
            return (url, values?.fileSize ?? 0, values?.contentModificationDate ?? .distantPast)
        }
        var total = entries.reduce(0) { $0 + $1.size }
        entries.sort { $0.used < $1.used }
        for entry in entries where total > maxBytes {
            try? files.removeItem(at: entry.url)
            total -= entry.size
        }
    }

    private func fileURL(_ key: String) -> URL {
        let hash = SHA256.hash(data: Data(key.utf8)).map { String(format: "%02x", $0) }.joined()
        return directory.appending(path: hash)
    }
}
