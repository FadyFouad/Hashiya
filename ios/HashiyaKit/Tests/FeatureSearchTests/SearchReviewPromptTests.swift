@testable import FeatureSearch
import HashiyaDiagnostics
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct SearchReviewPromptTests {
    private let review = FakeReviewPrompting()
    private let library = FakeLibraryRepository()
    private let paper = SamplePapers.attention

    private func makeViewModel() -> SearchViewModel {
        SearchViewModel(
            repository: FakeSearchRepository(page: .of([])), lookup: FakePaperLookupRepository(), library: library,
            preferences: FakeUserPreferencesRepository(), diagnostics: .fake(review: review)
        )
    }

    @Test func aSaveFromTheListCountsAndAsks() async {
        let viewModel = makeViewModel()
        await viewModel.toggleSave(paper)
        #expect(review.saves == 1)
        #expect(review.asks == 1)
    }

    @Test func aSaveFromThePreviewAsksWhenThePreviewCloses() async {
        let viewModel = makeViewModel()
        viewModel.selectedPaper = paper
        await viewModel.toggleSave(paper)
        #expect(review.saves == 1)
        #expect(review.asks == 0)
        viewModel.askForReviewAfterPreview()
        #expect(review.asks == 1)
        viewModel.askForReviewAfterPreview()
        #expect(review.asks == 1)
    }

    @Test func closingThePreviewWithoutASaveDoesNotAsk() {
        let viewModel = makeViewModel()
        viewModel.selectedPaper = paper
        viewModel.askForReviewAfterPreview()
        #expect(review.asks == 0)
    }

    @Test func aFailedSaveDoesNotCount() async {
        let viewModel = makeViewModel()
        library.setFailSaves(true)
        await viewModel.toggleSave(paper)
        #expect(review.saves == 0)
        #expect(review.asks == 0)
    }
}
