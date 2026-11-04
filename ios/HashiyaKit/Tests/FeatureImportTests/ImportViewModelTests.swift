import Foundation
@testable import FeatureImport
import HashiyaModel
import HashiyaTesting
import Testing

/// Ported from Android's `ImportUiStateTest.kt` and `ImportViewModelTest.kt`.
@MainActor
struct ImportViewModelTests {
    private func job(_ state: ImportState, failure: ImportFailure? = nil) -> ImportJob {
        ImportJob(id: 7, fileName: "thesis.bib", state: state, collectionName: "thesis", failure: failure)
    }

    @Test func eachJobStateHasItsScreen() {
        #expect(ImportScreenState.make(job: nil, reading: false, readFailed: false) == .start)
        #expect(ImportScreenState.make(job: job(.done), reading: true, readFailed: false) == .reading)
        #expect(ImportScreenState.make(job: job(.done), reading: false, readFailed: true) == .failed(.unreadable))
        #expect(ImportScreenState.make(job: job(.analyzing), reading: false, readFailed: false) == .analyzing(job(.analyzing)))
        #expect(ImportScreenState.make(job: job(.ready), reading: false, readFailed: false) == .ready(job(.ready)))
        #expect(ImportScreenState.make(job: job(.importing), reading: false, readFailed: false) == .importing(job(.importing)))
        #expect(ImportScreenState.make(job: job(.done), reading: false, readFailed: false) == .done(job(.done)))
        #expect(ImportScreenState.make(job: job(.cancelled), reading: false, readFailed: false) == .cancelled(job(.cancelled)))
        #expect(ImportScreenState.make(job: job(.failed, failure: .tooLarge), reading: false, readFailed: false) == .failed(.tooLarge))
    }

    private func observed(_ repository: FakeImportRepository) async -> ImportViewModel {
        let viewModel = ImportViewModel(repository: repository)
        Task { await viewModel.observe() }
        _ = await eventually { viewModel.state != .reading }
        return viewModel
    }

    @Test func aChosenFileIsReadAndStarted() async throws {
        let repository = FakeImportRepository()
        let viewModel = await observed(repository)
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).bib")
        try Data("@misc{k, title = {T}}".utf8).write(to: url)
        await viewModel.open(url: url)
        #expect(repository.starts == [url.lastPathComponent])
        #expect(await eventually { if case .analyzing = viewModel.state { true } else { false } })
    }

    @Test func aFileThatCanNotBeReadFailsWithoutAJob() async {
        let repository = FakeImportRepository()
        let viewModel = await observed(repository)
        await viewModel.open(url: URL(fileURLWithPath: "/nonexistent/\(UUID().uuidString).ris"))
        #expect(viewModel.state == .failed(.unreadable))
        #expect(repository.starts.isEmpty)
        await viewModel.dismiss()
        #expect(viewModel.state == .start)
    }

    @Test func confirmCancelAndDismissGoToTheRepository() async {
        let repository = FakeImportRepository(job: job(.ready))
        let viewModel = await observed(repository)
        _ = await eventually { viewModel.state == .ready(job(.ready)) }
        viewModel.addToCollection = false
        await viewModel.confirm()
        #expect(repository.confirms == [.init(jobID: 7, addToCollection: false)])
        #expect(await eventually { if case .importing = viewModel.state { true } else { false } })
        await viewModel.cancel()
        #expect(repository.cancels == [7])
        #expect(await eventually { if case .cancelled = viewModel.state { true } else { false } })
        await viewModel.dismiss()
        #expect(repository.dismissals == [7])
        #expect(await eventually { viewModel.state == .start })
    }

    @Test func aNewPreviewStartsWithTheCollectionSwitchOn() async {
        let repository = FakeImportRepository(job: job(.ready))
        let viewModel = await observed(repository)
        _ = await eventually { viewModel.state == .ready(job(.ready)) }
        #expect(viewModel.addToCollection)
        viewModel.addToCollection = false
        var next = job(.ready)
        next.id = 8
        repository.setJob(next)
        #expect(await eventually { viewModel.state == .ready(next) })
        #expect(viewModel.addToCollection)
    }

    @Test func onlyBibAndRisFilesOpenImport() {
        #expect(ImportFileTypes.opens(URL(fileURLWithPath: "/Inbox/My thesis.BIB")))
        #expect(ImportFileTypes.opens(URL(fileURLWithPath: "/tmp/export.ris")))
        #expect(!ImportFileTypes.opens(URL(fileURLWithPath: "/Inbox/library.hashiya")))
        #expect(!ImportFileTypes.opens(URL(fileURLWithPath: "/tmp/notes.txt")))
    }

    @Test func eachFailureHasItsMessage() {
        #expect(ImportView.messageKey(.unsupported) == "import.failedUnsupported")
        #expect(ImportView.messageKey(.tooLarge) == "import.failedTooLarge")
        #expect(ImportView.messageKey(.unreadable) == "import.failedUnreadable")
        #expect(ImportView.messageKey(.nothingToImport) == "import.failedNothing")
    }
}
