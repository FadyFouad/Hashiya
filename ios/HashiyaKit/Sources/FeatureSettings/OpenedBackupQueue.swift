import Foundation

/// A `.hashiya` file another app handed over, waiting to be shown in Restore.
public struct OpenedBackup: Identifiable, Equatable, Sendable {
    public let id = UUID()
    public let url: URL
    /// The system's copy in Documents/Inbox: not needed once the restore (which works on its own copy) is over.
    public let deletesWhenDone: Bool

    /// Nil for a file that isn't a `.hashiya` backup.
    public init?(openedURL url: URL) {
        guard url.pathExtension.lowercased() == "hashiya" else { return nil }
        self.url = url
        deletesWhenDone = url.pathComponents.contains("Inbox")
    }

    public func removeInboxCopy() {
        if deletesWhenDone { try? FileManager.default.removeItem(at: url) }
    }

    /// Where the system puts the files other apps hand over.
    public static var inbox: URL {
        URL.documentsDirectory.appending(path: "Inbox", directoryHint: .isDirectory)
    }

    /// Deletes what an earlier run left in Inbox (the app quit before a restore read its file, or a queued file was
    /// never shown). The app calls this at launch; a file added in the last `age` seconds may be the one this launch is
    /// about to show, so it stays.
    public static func removeStaleInboxFiles(in inbox: URL = inbox, olderThan age: TimeInterval = 5 * 60, now: Date = .now) {
        let keys: Set<URLResourceKey> = [
            .creationDateKey, .contentModificationDateKey, .attributeModificationDateKey, .addedToDirectoryDateKey,
        ]
        guard let files = try? FileManager.default.contentsOfDirectory(at: inbox, includingPropertiesForKeys: Array(keys)) else {
            return
        }
        for file in files {
            guard let values = try? file.resourceValues(forKeys: keys) else { continue }
            // The newest of its dates: a copied file can keep the dates of its original.
            let dates = [values.creationDate, values.contentModificationDate, values.attributeModificationDate, values.addedToDirectoryDate]
            if let newest = dates.compactMap({ $0 }).max(), now.timeIntervalSince(newest) > age {
                try? FileManager.default.removeItem(at: file)
            }
        }
    }
}

/// Files opened in a window, shown one at a time. An incoming file never interrupts a restore that is running, and
/// never replaces a Restore the user is looking at: it waits its turn.
public struct OpenedBackupQueue: Equatable, Sendable {
    public enum Step: Equatable, Sendable {
        case wait
        /// Settings is in the way: close it, then ask again when it has gone.
        case closeSettings
        case present(OpenedBackup)
    }

    public private(set) var pending: [OpenedBackup] = []

    public init() {}

    public mutating func enqueue(_ backup: OpenedBackup) {
        pending.append(backup)
    }

    /// What to do now. `isPresenting`: a Restore sheet is showing. `restoreApplying`: a restore anywhere in the window is
    /// running.
    public mutating func next(isPresenting: Bool, settingsOpen: Bool, restoreApplying: Bool) -> Step {
        guard !pending.isEmpty, !isPresenting, !restoreApplying else { return .wait }
        if settingsOpen { return .closeSettings }
        return .present(pending.removeFirst())
    }
}
