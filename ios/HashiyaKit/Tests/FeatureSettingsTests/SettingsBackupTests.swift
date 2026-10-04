import Foundation
import HashiyaData
import HashiyaTesting
import Testing
@testable import FeatureSettings

@MainActor struct SettingsBackupTests {
    let backup = FakeLibraryBackup()

    init() {
        backup.summary = BackupSummary(papers: 182, collections: 6, pdfCount: 41, pdfBytes: 238_000_000)
    }

    func viewModel() -> SettingsViewModel {
        SettingsViewModel(preferences: FakeUserPreferencesRepository(), pdfs: FakePdfRepository(), backup: backup)
    }

    func isReadyToSave(_ viewModel: SettingsViewModel) -> Bool {
        if case .readyToSave = viewModel.backup.export { return true }
        return false
    }

    @Test func loadsTheSummary() async {
        let vm = viewModel()
        await vm.loadBackupSummary()
        #expect(vm.backup.summary?.papers == 182)
    }

    @Test func exportsWithoutPdfsByDefaultThenSaves() async {
        let vm = viewModel()
        await vm.loadBackupSummary()
        vm.startExport()
        #expect(vm.backup.export == .choosing(includePdfs: false))
        vm.confirmExport()
        #expect(await eventually { isReadyToSave(vm) })
        #expect(backup.exports == [false])
        vm.exportFinished(.saved)
        #expect(vm.backup.export == .idle)
        #expect(vm.backup.message == .exported(missingPdfs: 0))
        #expect(backup.discardedExports == 1)
    }

    @Test func includePdfsIsPassedThrough() async {
        let vm = viewModel()
        vm.startExport()
        vm.setIncludePdfs(true)
        vm.confirmExport()
        #expect(await eventually { backup.exports == [true] })
    }

    @Test func missingPdfsAreReportedAfterSaving() async {
        backup.missingPdfs = 3
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        #expect(await eventually { isReadyToSave(vm) })
        vm.exportFinished(.saved)
        #expect(vm.backup.message == .exported(missingPdfs: 3))
    }

    @Test func cancellingTheSaveDiscardsTheFile() async {
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        #expect(await eventually { isReadyToSave(vm) })
        vm.exportFinished(.cancelled)
        #expect(vm.backup.export == .idle)
        #expect(vm.backup.message == nil)
        #expect(backup.discardedExports == 1)
    }

    @Test func aFailedSaveSaysTheExportFailed() async {
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        #expect(await eventually { isReadyToSave(vm) })
        vm.exportFinished(.failed)
        #expect(vm.backup.export == .idle)
        #expect(vm.backup.message == .exportFailed(.writeFailed))
        #expect(backup.discardedExports == 1)
    }

    @Test func startingAnExportClearsTheLastMessage() async {
        // The Export screen leaves when a message appears: a repeat of the same failure must still be a change.
        backup.exportFailure = .noSpace
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        #expect(await eventually { vm.backup.message == .exportFailed(.noSpace) })
        vm.startExport()
        #expect(vm.backup.message == nil)
        #expect(vm.backup.export == .choosing(includePdfs: false))
    }

    @Test func cancellingABuildingExportReturnsToIdle() async {
        let gate = AsyncStream<Void>.makeStream()
        backup.exportGate = gate.stream
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        #expect(await eventually { if case .building = vm.backup.export { return true }; return false })
        vm.cancelExport()
        gate.continuation.yield(())
        #expect(vm.backup.export == .idle)
        try? await Task.sleep(for: .milliseconds(50))
        #expect(vm.backup.export == .idle)
        #expect(vm.backup.message == nil)
    }

    @Test func aFailedExportShowsItsReason() async {
        backup.exportFailure = .noSpace
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        #expect(await eventually { vm.backup.message == .exportFailed(.noSpace) })
        #expect(vm.backup.export == .idle)
    }

    @Test func anUnexpectedErrorEndsTheExportWithAMessage() async {
        backup.exportError = NSError(domain: NSCocoaErrorDomain, code: NSFileWriteUnknownError)
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        #expect(await eventually { vm.backup.message == .exportFailed(.writeFailed) })
        #expect(vm.backup.export == .idle)
    }

    @Test func exportOnlyStartsFromIdle() async {
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        #expect(await eventually { isReadyToSave(vm) })
        vm.startExport()
        #expect(isReadyToSave(vm), "a second export replaced the one waiting to be saved")
    }

    @Test func finishingOnlyActsWhileReadyToSave() {
        let vm = viewModel()
        vm.exportFinished(.saved)
        #expect(vm.backup.message == nil)
        #expect(backup.discardedExports == 0)
    }

    @Test func dismissingClearsTheMessage() async {
        backup.exportFailure = .writeFailed
        let vm = viewModel()
        vm.startExport()
        vm.confirmExport()
        #expect(await eventually { vm.backup.message != nil })
        vm.dismissMessage()
        #expect(vm.backup.message == nil)
    }
}
