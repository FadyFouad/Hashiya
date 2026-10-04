import Foundation
import HashiyaData
import HashiyaTesting
import Testing
@testable import FeatureSettings

@MainActor struct RestoreViewModelTests {
    let backup = FakeLibraryBackup()
    let preview = RestorePreview(exportedAt: 1, papers: 182, collections: 6, pdfs: 41, newPapers: 150, existingPapers: 31, papersSkipped: 1)
    let source = URL(fileURLWithPath: "/tmp/backup.hashiya")

    @Test func opensTheFileAndShowsThePreview() async {
        backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), preview)
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        #expect(backup.opened == [source])
        #expect(vm.state == .preview(preview))
    }

    @Test func opensOnlyOnce() async {
        backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), preview)
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        await vm.load()
        #expect(backup.opened == [source])
    }

    @Test func showsWhyAFileCantBeRestored() async {
        backup.openResult = .failed(.newerFormat)
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        #expect(vm.state == .invalid(.newerFormat))
    }

    @Test func confirmAppliesAndShowsTheResult() async {
        backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), preview)
        backup.applyResult = RestoreResult(papersAdded: 150, notesAdded: 3, collectionsCreated: 6, pdfsAdded: 38, pdfsMissing: 3, papersSkipped: 1)
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        vm.confirm()
        #expect(await eventually { if case .done = vm.state { return true }; return false })
        #expect(vm.state == .done(backup.applyResult))
        #expect(backup.discardedBackups.count == 1)
    }

    @Test func aRefusedRestoreSaysAnotherIsRunning() async {
        backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), preview)
        backup.applyFailure = .busy
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        vm.confirm()
        #expect(await eventually { vm.state == .failed(.busy) })
        #expect(backup.discardedBackups.count == 1)
    }

    @Test func confirmOutsideThePreviewDoesNothing() async {
        backup.openResult = .failed(.damaged)
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        vm.confirm()
        #expect(backup.applied.isEmpty)
    }

    @Test func cancelDiscards() async {
        backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), preview)
        let vm = RestoreViewModel(source: source, backup: backup)
        await vm.load()
        vm.cancel()
        #expect(backup.discardedBackups.count == 1)
    }
}
