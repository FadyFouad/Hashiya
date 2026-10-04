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
