@testable import FeatureLibrary
import Foundation
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct LibraryViewModelTests {
    private let sleeper = ManualSleeper()

    private func makeViewModel(_ library: FakeLibraryRepository) -> LibraryViewModel {
        LibraryViewModel(
            library: library,
            collections: FakeCollectionsRepository(library: library),
            citations: FakeCitationRepository(),
            exportFiles: ExportFiles(directory: FileManager.default.temporaryDirectory.appendingPathComponent("library-tests-\(UUID().uuidString)")),
            share: { _ in },
            sleep: sleeper.sleep
        )
    }

    private func counts(_ toRead: Int, _ reading: Int, _ read: Int) -> [ReadingStatus: Int] {
        [.toRead: toRead, .reading: reading, .read: read]
    }

    private func ids(_ viewModel: LibraryViewModel) -> [String] {
        viewModel.papers.map(\.paper.openAlexID)
    }

    /// Types `text` and lets the debounce elapse.
    private func type(_ text: String, into viewModel: LibraryViewModel) async {
        viewModel.updateText(text)
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(300))
    }

    /// Every state `viewModel` goes through from now on.
    private func recordStates(of viewModel: LibraryViewModel) -> StateRecorder {
        let recorder = StateRecorder()
        viewModel.stateObserver = { recorder.states.append($0) }
        return recorder
    }

    @Test func anEmptyLibraryIsEmpty() async {
        let viewModel = makeViewModel(FakeLibraryRepository())
        #expect(viewModel.state == .loading)
        #expect(await eventually { viewModel.state == .empty })
        #expect(viewModel.isLoaded)
    }

    @Test func papersAreNewestFirstWithTheirStatusesAndCounts() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention], statuses: [SamplePapers.bert.openAlexID: .reading])
        let viewModel = makeViewModel(library)

        let filter = LibraryFilter(query: "", status: nil, counts: counts(2, 1, 0))
        #expect(await eventually {
            viewModel.state == .papers([
                LibraryPaper(paper: SamplePapers.vit, status: .toRead),
                LibraryPaper(paper: SamplePapers.bert, status: .reading),
                LibraryPaper(paper: SamplePapers.attention, status: .toRead),
            ], filter)
        })
        #expect(filter.total == 3)
    }

    @Test func newSavesAppearAtTheTop() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 1 })

        try await library.save(SamplePapers.bert)
        #expect(await eventually { ids(viewModel) == [SamplePapers.bert.openAlexID, SamplePapers.attention.openAlexID] })
    }

    @Test func typingSearchesAfter300Milliseconds() async throws {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))
        #expect(await eventually { viewModel.papers.count == 3 })

        viewModel.updateText("bert")
        await sleeper.waitForSleeper()
        sleeper.advance(by: .milliseconds(299))
        try await Task.sleep(for: .milliseconds(20))
        #expect(viewModel.appliedQuery == "")
        #expect(viewModel.papers.count == 3)

        sleeper.advance(by: .milliseconds(1))
        #expect(await eventually { ids(viewModel) == [SamplePapers.bert.openAlexID] })
        #expect(viewModel.appliedQuery == "bert")
        #expect(viewModel.filter?.query == "bert")
    }

    @Test func theSearchKeyAppliesAtOnce() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))
        #expect(await eventually { viewModel.papers.count == 3 })

        viewModel.updateText("vaswani")
        viewModel.submitNow()

        #expect(viewModel.appliedQuery == "vaswani")
        #expect(await eventually { ids(viewModel) == [SamplePapers.attention.openAlexID] })
        #expect(sleeper.pendingCount == 0)
    }

    @Test func emptyingTheTextAppliesAtOnce() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))
        await type("vaswani", into: viewModel)
        #expect(await eventually { viewModel.papers.count == 1 })

        viewModel.updateText("")

        #expect(viewModel.appliedQuery == "")
        #expect(await eventually { viewModel.papers.count == 3 })
        #expect(sleeper.pendingCount == 0)
    }

    @Test func aChipAndASearchCombine() async {
        let library = FakeLibraryRepository(saved: SamplePapers.all, statuses: [SamplePapers.bert.openAlexID: .reading])
        let viewModel = makeViewModel(library)
        await type("transf", into: viewModel)
        #expect(await eventually { viewModel.papers.count == 3 })

        viewModel.setStatusFilter(.reading)

        #expect(viewModel.status == .reading)
        #expect(await eventually {
            viewModel.state == .papers(
                [LibraryPaper(paper: SamplePapers.bert, status: .reading)],
                LibraryFilter(query: "transf", status: .reading, counts: counts(2, 1, 0))
            )
        })
    }

    @Test func noMatchesWhenTheLibraryHasPapersButNoneMatch() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))
        await type("nothing like this", into: viewModel)

        #expect(await eventually {
            viewModel.state == .noMatches(LibraryFilter(query: "nothing like this", status: nil, counts: counts(0, 0, 0)))
        })

        viewModel.updateText("")
        viewModel.setStatusFilter(.read)
        #expect(await eventually { viewModel.state == .noMatches(LibraryFilter(query: "", status: .read, counts: counts(3, 0, 0))) })
    }

    @Test func clearSearchAndFiltersResetsBoth() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))
        viewModel.setStatusFilter(.read)
        await type("vaswani", into: viewModel)
        #expect(await eventually { viewModel.appliedQuery == "vaswani" })
        #expect(await eventually { viewModel.filter?.counts == counts(1, 0, 0) })

        viewModel.clearSearchAndFilters()

        #expect(viewModel.text == "")
        #expect(viewModel.appliedQuery == "")
        #expect(viewModel.status == nil)
        #expect(await eventually { viewModel.papers.count == 3 })
    }

    /// The pause has ended but the debounced search hasn't run yet: Clear still wins.
    @Test func clearingRightAfterThePauseEndsKeepsTheSearchCleared() async throws {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))
        #expect(await eventually { viewModel.papers.count == 3 })

        await type("vaswani", into: viewModel)
        viewModel.clearSearchAndFilters()
        try await Task.sleep(for: .milliseconds(50))

        #expect(viewModel.appliedQuery == "")
        #expect(viewModel.papers.count == 3)
    }

    @Test func restoresTheTextAndChipFromSceneStorageOnce() async {
        let library = FakeLibraryRepository(saved: SamplePapers.all, statuses: [SamplePapers.attention.openAlexID: .reading])
        let viewModel = makeViewModel(library)

        viewModel.restore(text: "attention", status: "reading")
        viewModel.restore(text: "", status: "")

        #expect(viewModel.text == "attention")
        #expect(viewModel.appliedQuery == "attention")
        #expect(viewModel.status == .reading)
        #expect(viewModel.storedStatus == "reading")
        #expect(await eventually { ids(viewModel) == [SamplePapers.attention.openAlexID] })
        #expect(sleeper.pendingCount == 0)
    }

    @Test func anUnknownStoredChipRestoresAll() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: SamplePapers.all))

        viewModel.restore(text: "", status: "archived")

        #expect(viewModel.status == nil)
        #expect(viewModel.storedStatus == "")
        #expect(await eventually { viewModel.papers.count == 3 })
    }

    @Test func aStatusChangeUpdatesTheListAndTheCounts() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: [SamplePapers.bert, SamplePapers.attention]))
        #expect(await eventually { viewModel.papers.count == 2 })

        await viewModel.setStatus(of: SamplePapers.attention, to: .reading)

        #expect(await eventually {
            viewModel.state == .papers([
                LibraryPaper(paper: SamplePapers.bert, status: .toRead),
                LibraryPaper(paper: SamplePapers.attention, status: .reading),
            ], LibraryFilter(query: "", status: nil, counts: counts(1, 1, 0)))
        })
    }

    @Test func aFailedStatusChangeShowsTheMessageAndKeepsTheStoredStatus() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        library.setFailStatusUpdates(true)
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 1 })

        await viewModel.setStatus(of: SamplePapers.attention, to: .read)

        #expect(viewModel.message == .statusUpdateFailed)
        #expect(viewModel.papers == [LibraryPaper(paper: SamplePapers.attention, status: .toRead)])
    }

    @Test func removingOffersUndo() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.bert, SamplePapers.attention])
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 2 })

        await viewModel.remove(SamplePapers.attention)

        #expect(viewModel.pendingUndo?.paper == SamplePapers.attention)
        #expect(await eventually { ids(viewModel) == [SamplePapers.bert.openAlexID] })
    }

    /// Remove on Details hands the paper back by its ID; Undo restores it with its status and notes.
    @Test func removingByIDOffersUndoThatRestoresTheNotes() async {
        let notes = PaperNotes(summary: "Kept")
        let library = FakeLibraryRepository(
            saved: [SamplePapers.bert, SamplePapers.attention],
            statuses: [SamplePapers.attention.openAlexID: .reading],
            notes: [SamplePapers.attention.openAlexID: notes]
        )
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 2 })

        await viewModel.remove(openAlexID: SamplePapers.attention.openAlexID)
        #expect(viewModel.pendingUndo?.notes == notes)
        #expect(await eventually { viewModel.papers.count == 1 })

        await viewModel.undo()

        #expect(await eventually { viewModel.papers.contains(LibraryPaper(paper: SamplePapers.attention, status: .reading)) })
        #expect(library.notes(of: SamplePapers.attention.openAlexID) == notes)
    }

    @Test func removingTheLastPaperDuringASearchShowsEmpty() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: [SamplePapers.attention]))
        viewModel.setStatusFilter(.toRead)
        await type("attention", into: viewModel)
        #expect(await eventually { viewModel.appliedQuery == "attention" && viewModel.papers.count == 1 })

        await viewModel.remove(SamplePapers.attention)

        #expect(await eventually { viewModel.state == .empty })
    }

    @Test func removingAndRestoringTheOnlyPaperNeverShowsNoMatches() async {
        let viewModel = makeViewModel(FakeLibraryRepository(saved: [SamplePapers.attention]))
        viewModel.setStatusFilter(.toRead)
        await type("attention", into: viewModel)
        #expect(await eventually { viewModel.appliedQuery == "attention" && viewModel.papers.count == 1 })
        let recorder = recordStates(of: viewModel)

        await viewModel.remove(SamplePapers.attention)
        #expect(await eventually { viewModel.state == .empty })
        await viewModel.undo()
        #expect(await eventually { viewModel.papers.count == 1 })

        #expect(!recorder.states.isEmpty)
        #expect(!recorder.states.contains { if case .noMatches = $0 { true } else { false } })
    }

    @Test func undoRestoresThePaperInPlaceWithItsStatus() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention], statuses: [SamplePapers.bert.openAlexID: .reading])
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.remove(SamplePapers.bert)
        #expect(await eventually { ids(viewModel) == [SamplePapers.vit.openAlexID, SamplePapers.attention.openAlexID] })
        await viewModel.undo()

        #expect(viewModel.pendingUndo == nil)
        #expect(await eventually {
            viewModel.papers == [
                LibraryPaper(paper: SamplePapers.vit, status: .toRead),
                LibraryPaper(paper: SamplePapers.bert, status: .reading),
                LibraryPaper(paper: SamplePapers.attention, status: .toRead),
            ]
        })
    }

    @Test func twoQuickRemovalsKeepOnlyTheLatest() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention])
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.remove(SamplePapers.bert)
        await viewModel.remove(SamplePapers.vit)
        #expect(viewModel.pendingUndo?.paper == SamplePapers.vit)

        await viewModel.undo()
        #expect(await eventually { ids(viewModel) == [SamplePapers.vit.openAlexID, SamplePapers.attention.openAlexID] })
    }

    @Test func anExpiredUndoForgetsThePaper() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.isLoaded })

        await viewModel.remove(SamplePapers.attention)
        viewModel.undoExpired()
        await viewModel.undo()

        #expect(viewModel.pendingUndo == nil)
        #expect(library.savedPapers.isEmpty)
    }

    @Test func aFailedRemoveChangesNothing() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        library.setFailRemoves(true)
        let viewModel = makeViewModel(library)
        #expect(await eventually { viewModel.isLoaded })

        await viewModel.remove(SamplePapers.attention)

        #expect(viewModel.pendingUndo == nil)
        #expect(ids(viewModel) == [SamplePapers.attention.openAlexID])
    }
}

@MainActor
private final class StateRecorder {
    var states: [LibraryState] = []
}
