import Foundation
import HashiyaData
import HashiyaDiagnostics
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

    @Test func theSourceIsReleasedOnceOpenHasMadeItsCopy() async {
        let gate = AsyncStream<Void>.makeStream()
        backup.openGate = gate.stream
        backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), preview)
        var released = 0
        let vm = RestoreViewModel(source: source, backup: backup, onSourceRead: { released += 1 })
        let loading = Task { await vm.load() }
        #expect(await eventually { backup.opened == [source] })
        #expect(released == 0, "released while open was still copying it")
        gate.continuation.yield(())
        await loading.value
        #expect(released == 1)
        await vm.load()
        #expect(released == 1)
    }

    @Test func theSourceIsReleasedWhenItCantBeRestored() async {
        backup.openResult = .failed(.damaged)
        var released = 0
        let vm = RestoreViewModel(source: source, backup: backup, onSourceRead: { released += 1 })
        await vm.load()
        #expect(released == 1)
    }

    @Test func aCopyPreparedAfterCancellingIsDiscarded() async {
        let gate = AsyncStream<Void>.makeStream()
        backup.openGate = gate.stream
        backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), preview)
        let vm = RestoreViewModel(source: source, backup: backup)
        let loading = Task { await vm.load() }
        #expect(await eventually { backup.opened == [source] })
        vm.cancel()
        gate.continuation.yield(())
        await loading.value
        #expect(backup.discardedBackups.count == 1)
        #expect(vm.state == .loading)
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

    // MARK: Crash reports

    private func restoreViewModel(applyError: (any Error)?, diagnostics: Diagnostics) -> RestoreViewModel {
        backup.openResult = .ready(FakeLibraryBackup.preparedBackup(), preview)
        if let error = applyError as? BackupError { backup.applyFailure = error } else { backup.applyError = applyError }
        return RestoreViewModel(source: source, backup: backup, diagnostics: diagnostics)
    }

    private func confirmAndWait(_ viewModel: RestoreViewModel) async {
        await viewModel.load()
        viewModel.confirm()
        #expect(await eventually { if case .applying = viewModel.state { false } else { true } })
    }

    @Test(arguments: [BackupError.writeFailed, .unreadable])
    func aWriteOrReadFailureIsReported(error: BackupError) async {
        let crash = FakeCrashReporting()
        let viewModel = restoreViewModel(applyError: error, diagnostics: .fake(crash: crash))
        await confirmAndWait(viewModel)
        #expect(crash.records.map(\.site) == [.restore])
        #expect(crash.keys[.backupInProgress] == "none")
    }

    @Test(arguments: [BackupError.noSpace, .busy])
    func noSpaceAndBusyAreNotReported(error: BackupError) async {
        let crash = FakeCrashReporting()
        let viewModel = restoreViewModel(applyError: error, diagnostics: .fake(crash: crash))
        await confirmAndWait(viewModel)
        #expect(crash.records.isEmpty)
    }

    @Test func anUnexpectedErrorIsReportedAsAnUIError() async {
        struct Odd: Error {}
        let crash = FakeCrashReporting()
        let viewModel = restoreViewModel(applyError: Odd(), diagnostics: .fake(crash: crash))
        await confirmAndWait(viewModel)
        #expect(crash.records.map(\.site) == [.unexpectedUiError])
        #expect(viewModel.state == .failed(.writeFailed))
    }

    @Test func aRestoreSetsAndClearsTheBackupKey() async {
        let crash = FakeCrashReporting()
        let viewModel = restoreViewModel(applyError: nil, diagnostics: .fake(crash: crash))
        await viewModel.load()
        viewModel.confirm()
        #expect(crash.keys[.backupInProgress] == "restore")
        #expect(await eventually { if case .done = viewModel.state { true } else { false } })
        #expect(crash.keys[.backupInProgress] == "none")
    }
}
