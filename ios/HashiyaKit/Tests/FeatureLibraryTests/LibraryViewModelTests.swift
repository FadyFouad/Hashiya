@testable import FeatureLibrary
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct LibraryViewModelTests {
    @Test func anEmptyLibraryLoadsEmpty() async {
        let viewModel = LibraryViewModel(library: FakeLibraryRepository())
        #expect(await eventually { viewModel.isLoaded })
        #expect(viewModel.papers.isEmpty)
    }

    @Test func papersAreNewestFirst() async {
        let viewModel = LibraryViewModel(library: FakeLibraryRepository(saved: [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention]))
        #expect(await eventually { viewModel.papers == [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention] })
    }

    @Test func newSavesAppearAtTheTop() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let viewModel = LibraryViewModel(library: library)
        #expect(await eventually { viewModel.isLoaded })

        try await library.save(SamplePapers.bert)
        #expect(await eventually { viewModel.papers == [SamplePapers.bert, SamplePapers.attention] })
    }

    @Test func selectingOpensThePreviewAndDismissingClosesIt() async {
        let viewModel = LibraryViewModel(library: FakeLibraryRepository(saved: [SamplePapers.attention]))
        #expect(await eventually { viewModel.isLoaded })

        viewModel.select(SamplePapers.attention)
        #expect(viewModel.selectedPaper == SamplePapers.attention)
        viewModel.selectedPaperID = nil
        #expect(viewModel.selectedPaper == nil)
    }

    @Test func removingOffersUndoAndClosesThePreview() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.bert, SamplePapers.attention])
        let viewModel = LibraryViewModel(library: library)
        #expect(await eventually { viewModel.isLoaded })
        viewModel.select(SamplePapers.attention)

        await viewModel.remove(SamplePapers.attention)

        #expect(viewModel.selectedPaperID == nil)
        #expect(viewModel.pendingUndo?.paper == SamplePapers.attention)
        #expect(await eventually { viewModel.papers == [SamplePapers.bert] })
    }

    @Test func theSheetClosesWhenThePaperDisappears() async throws {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let viewModel = LibraryViewModel(library: library)
        #expect(await eventually { viewModel.isLoaded })
        viewModel.select(SamplePapers.attention)

        _ = try await library.remove(openAlexID: SamplePapers.attention.openAlexID)

        #expect(await eventually { viewModel.selectedPaperID == nil })
        #expect(viewModel.selectedPaper == nil)
    }

    @Test func undoRestoresThePaperInPlace() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention])
        let viewModel = LibraryViewModel(library: library)
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.remove(SamplePapers.bert)
        #expect(await eventually { viewModel.papers == [SamplePapers.vit, SamplePapers.attention] })
        await viewModel.undo()

        #expect(viewModel.pendingUndo == nil)
        #expect(await eventually { viewModel.papers == [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention] })
    }

    @Test func twoQuickRemovalsKeepOnlyTheLatest() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.vit, SamplePapers.bert, SamplePapers.attention])
        let viewModel = LibraryViewModel(library: library)
        #expect(await eventually { viewModel.papers.count == 3 })

        await viewModel.remove(SamplePapers.bert)
        await viewModel.remove(SamplePapers.vit)
        #expect(viewModel.pendingUndo?.paper == SamplePapers.vit)

        await viewModel.undo()
        #expect(await eventually { viewModel.papers == [SamplePapers.vit, SamplePapers.attention] })
    }

    @Test func anExpiredUndoForgetsThePaper() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        let viewModel = LibraryViewModel(library: library)
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
        let viewModel = LibraryViewModel(library: library)
        #expect(await eventually { viewModel.isLoaded })

        await viewModel.remove(SamplePapers.attention)

        #expect(viewModel.pendingUndo == nil)
        #expect(viewModel.papers == [SamplePapers.attention])
    }
}
