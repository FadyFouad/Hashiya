import Foundation
import Testing
@testable import FeatureSettings

struct OpenedBackupQueueTests {
    func backup(_ path: String) -> OpenedBackup {
        OpenedBackup(openedURL: URL(fileURLWithPath: path))!
    }

    @Test func onlyHashiyaFilesAreAccepted() {
        #expect(OpenedBackup(openedURL: URL(fileURLWithPath: "/tmp/a.pdf")) == nil)
        #expect(OpenedBackup(openedURL: URL(fileURLWithPath: "/tmp/a.HASHIYA")) != nil)
    }

    @Test func onlyInboxCopiesAreDeleted() {
        #expect(backup("/var/Documents/Inbox/a.hashiya").deletesWhenDone)
        #expect(!backup("/var/Documents/a.hashiya").deletesWhenDone)
    }

    @Test func launchClearsOnlyStaleInboxFiles() throws {
        let inbox = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        try FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        defer {
            try? FileManager.default.removeItem(at: inbox)
        }
        let file = inbox.appending(path: "Hashiya-library.hashiya")
        try Data("zip".utf8).write(to: file)

        // Just delivered (a file opened at cold launch): the restore about to read it keeps it.
        OpenedBackup.removeStaleInboxFiles(in: inbox, now: .now)
        #expect(FileManager.default.fileExists(atPath: file.path))

        OpenedBackup.removeStaleInboxFiles(in: inbox, now: .now + 10 * 60)
        #expect(!FileManager.default.fileExists(atPath: file.path))
    }

    @Test func clearingAMissingInboxDoesNothing() {
        let inbox = FileManager.default.temporaryDirectory.appending(path: UUID().uuidString, directoryHint: .isDirectory)
        OpenedBackup.removeStaleInboxFiles(in: inbox, now: .now)
        #expect(!FileManager.default.fileExists(atPath: inbox.path))
    }

    @Test func presentsAtOnceWhenNothingIsInTheWay() {
        var queue = OpenedBackupQueue()
        let a = backup("/Inbox/a.hashiya")
        queue.enqueue(a)
        #expect(queue.next(isPresenting: false, settingsOpen: false, restoreApplying: false) == .present(a))
        #expect(queue.pending.isEmpty)
    }

    @Test func waitsBehindAShowingRestoreThenTakesItsTurn() {
        var queue = OpenedBackupQueue()
        let b = backup("/Inbox/b.hashiya")
        queue.enqueue(b)
        #expect(queue.next(isPresenting: true, settingsOpen: false, restoreApplying: false) == .wait)
        #expect(queue.next(isPresenting: false, settingsOpen: false, restoreApplying: false) == .present(b))
    }

    @Test func neverInterruptsARunningRestore() {
        var queue = OpenedBackupQueue()
        queue.enqueue(backup("/Inbox/b.hashiya"))
        #expect(queue.next(isPresenting: false, settingsOpen: true, restoreApplying: true) == .wait)
        #expect(queue.pending.count == 1)
    }

    @Test func closesSettingsFirstAndKeepsTheFileQueued() {
        var queue = OpenedBackupQueue()
        let b = backup("/Inbox/b.hashiya")
        queue.enqueue(b)
        #expect(queue.next(isPresenting: false, settingsOpen: true, restoreApplying: false) == .closeSettings)
        #expect(queue.next(isPresenting: false, settingsOpen: false, restoreApplying: false) == .present(b))
    }

    @Test func filesAreShownInTheOrderTheyArrived() {
        var queue = OpenedBackupQueue()
        let a = backup("/Inbox/a.hashiya")
        let b = backup("/Inbox/b.hashiya")
        queue.enqueue(a)
        queue.enqueue(b)
        #expect(queue.next(isPresenting: false, settingsOpen: false, restoreApplying: false) == .present(a))
        #expect(queue.next(isPresenting: false, settingsOpen: false, restoreApplying: false) == .present(b))
    }
}
