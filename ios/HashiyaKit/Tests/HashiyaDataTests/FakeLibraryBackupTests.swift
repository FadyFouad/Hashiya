import Foundation
import HashiyaData
import HashiyaTesting
import Testing

struct FakeLibraryBackupTests {
    @Test func recordsExportsAndReturnsTheConfiguredFile() async throws {
        let fake = FakeLibraryBackup()
        fake.missingPdfs = 2
        let file = try await fake.export(includePdfs: true) { _ in }
        #expect(fake.exports == [true])
        #expect(file.missingPdfs == 2)
        #expect(file.url.lastPathComponent == file.fileName)

        fake.discard(file)
        #expect(fake.discardedExports == 1)
    }

    @Test func anExportThrowsTheConfiguredFailure() async {
        let fake = FakeLibraryBackup()
        fake.exportFailure = .noSpace
        await #expect(throws: BackupError.noSpace) {
            try await fake.export(includePdfs: false) { _ in }
        }
        #expect(fake.exports == [false])
    }

    @Test func anExportWaitsForItsGate() async throws {
        let fake = FakeLibraryBackup()
        let gate = AsyncStream<Void>.makeStream()
        fake.exportGate = gate.stream
        let task = Task { try await fake.export(includePdfs: false) { _ in } }
        try await Task.sleep(for: .milliseconds(30))
        #expect(fake.exports == [false])

        gate.continuation.yield(())
        let file = try await task.value
        #expect(file.fileName.hasSuffix(".hashiya"))
    }

    @Test func aCancelledGatedExportThrowsCancellation() async {
        let fake = FakeLibraryBackup()
        let gate = AsyncStream<Void>.makeStream()
        fake.exportGate = gate.stream
        let task = Task { try await fake.export(includePdfs: false) { _ in } }
        try? await Task.sleep(for: .milliseconds(30))
        task.cancel()
        gate.continuation.yield(())
        await #expect(throws: CancellationError.self) { try await task.value }
    }

    @Test func recordsOpensAndApplies() async throws {
        let fake = FakeLibraryBackup()
        let backup = FakeLibraryBackup.preparedBackup()
        let preview = RestorePreview(exportedAt: nil, papers: 3, collections: 1, pdfs: 0, newPapers: 3, existingPapers: 0, papersSkipped: 0)
        fake.openResult = .ready(backup, preview)
        fake.applyResult = RestoreResult(papersAdded: 3, notesAdded: 1, collectionsCreated: 1, pdfsAdded: 0, pdfsMissing: 0, papersSkipped: 0)

        let source = URL(fileURLWithPath: "/tmp/a.hashiya")
        #expect(await fake.open(source) == .ready(backup, preview))
        let result = try await fake.apply(backup) { _ in }
        #expect(result.papersAdded == 3)
        fake.discard(backup)

        #expect(fake.opened == [source])
        #expect(fake.applied == [backup])
        #expect(fake.discardedBackups == [backup])
    }

    @Test func anApplyThrowsTheConfiguredFailure() async {
        let fake = FakeLibraryBackup()
        fake.applyFailure = .busy
        await #expect(throws: BackupError.busy) {
            try await fake.apply(FakeLibraryBackup.preparedBackup()) { _ in }
        }
    }

    @Test func theSummaryIsWhatTestsSet() async throws {
        let fake = FakeLibraryBackup()
        fake.summary = BackupSummary(papers: 4, collections: 2, pdfCount: 1, pdfBytes: 10)
        #expect(try await fake.summary().papers == 4)
    }
}
