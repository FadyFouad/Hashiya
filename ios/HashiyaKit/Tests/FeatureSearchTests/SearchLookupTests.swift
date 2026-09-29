@testable import FeatureSearch
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

/// ID mode: DOIs, arXiv IDs and links typed or pasted into Search; and the Arabic-marks rule for keywords.
@MainActor
struct SearchLookupTests {
    private let library = FakeLibraryRepository()
    private let preferences = FakeUserPreferencesRepository()
    private let search = FakeSearchRepository(page: .of(SamplePapers.all))
    private let bertTitle = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"

    private func makeViewModel(_ lookup: FakePaperLookupRepository) -> SearchViewModel {
        SearchViewModel(repository: search, lookup: lookup, library: library, preferences: preferences)
    }

    /// Types `text` and presses the keyboard's Search key.
    private func type(_ text: String, into viewModel: SearchViewModel) async {
        viewModel.updateText(text)
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
    }

    @Test func textOfOnlyArabicMarksIsIdleAndMarksAreNotSent() async {
        let viewModel = makeViewModel(FakePaperLookupRepository())
        await type("ـــ", into: viewModel)
        #expect(viewModel.phase == .idle)
        await type("\u{064E}", into: viewModel)
        #expect(viewModel.phase == .idle)
        #expect(search.calls.isEmpty)

        await type("التَّعلُّم", into: viewModel)

        #expect(search.calls.map(\.query.text) == ["التعلم"])
        #expect(viewModel.text == "التَّعلُّم")
        #expect(viewModel.phase == .results)
    }

    @Test func aPastedDOILinkIsLookedUpNotSearched() async {
        let lookup = FakePaperLookupRepository(results: [.doi("10.1038/nature14539"): .found(SamplePapers.bert)])
        let viewModel = makeViewModel(lookup)

        await type("https://doi.org/10.1038/nature14539", into: viewModel)

        #expect(lookup.lookups == [.doi("10.1038/nature14539")])
        #expect(search.calls.isEmpty)
        #expect(viewModel.lookup == .found(SamplePapers.bert))
    }

    @Test func itShowsLookingUntilTheLookupFinishes() async {
        let lookup = FakePaperLookupRepository(results: [.arxiv("1706.03762"): .found(SamplePapers.attention)])
        lookup.hold()
        let viewModel = makeViewModel(lookup)
        viewModel.updateText("1706.03762")
        viewModel.submitNow()
        #expect(await eventually { lookup.lookups == [.arxiv("1706.03762")] })
        #expect(viewModel.lookup == .looking(.arxiv("1706.03762")))

        lookup.release()
        await viewModel.waitForPendingWork()

        #expect(viewModel.lookup == .found(SamplePapers.attention))
    }

    @Test func textContainingADOIIsAKeywordSearch() async {
        let lookup = FakePaperLookupRepository()
        let viewModel = makeViewModel(lookup)

        await type("a study of 10.1038/nature14539", into: viewModel)

        #expect(search.calls.map(\.query.text) == ["a study of 10.1038/nature14539"])
        #expect(lookup.lookups.isEmpty)
        #expect(viewModel.lookup == nil)
    }

    @Test func aLinkWithoutAnIDSendsNothingThenKeywordsWorkAgain() async {
        let lookup = FakePaperLookupRepository()
        let viewModel = makeViewModel(lookup)

        await type("https://ieeexplore.ieee.org/document/1234567", into: viewModel)

        #expect(viewModel.lookup == .noIDInLink)
        #expect(lookup.lookups.isEmpty)
        #expect(search.calls.isEmpty)

        await type("bert", into: viewModel)

        #expect(viewModel.lookup == nil)
        #expect(search.calls.map(\.query.text) == ["bert"])
        #expect(viewModel.phase == .results)
    }

    @Test func aNewerLookupReplacesAnOlderOne() async {
        let lookup = FakePaperLookupRepository(results: [
            .arxiv("1706.03762"): .found(SamplePapers.attention),
            .doi("10.18653/v1/n19-1423"): .found(SamplePapers.bert),
        ])
        lookup.hold()
        let viewModel = makeViewModel(lookup)
        viewModel.updateText("1706.03762")
        viewModel.submitNow()
        #expect(await eventually { lookup.lookups.count == 1 })
        viewModel.updateText("10.18653/v1/n19-1423")
        viewModel.submitNow()
        #expect(await eventually { lookup.lookups.count == 2 })

        lookup.release()
        await viewModel.waitForPendingWork()

        #expect(viewModel.lookup == .found(SamplePapers.bert))
    }

    @Test func retryRerunsTheLookup() async {
        let lookup = FakePaperLookupRepository(otherwise: .failed(.offline))
        let viewModel = makeViewModel(lookup)
        await type("1706.03762", into: viewModel)
        #expect(viewModel.lookup == .failed(.offline))

        lookup.setResult(.found(SamplePapers.attention), for: .arxiv("1706.03762"))
        viewModel.retry()
        await viewModel.waitForPendingWork()

        #expect(viewModel.lookup == .found(SamplePapers.attention))
        #expect(lookup.lookups == [.arxiv("1706.03762"), .arxiv("1706.03762")])
    }

    @Test func anAPIKeyChangeRerunsTheLookup() async throws {
        let lookup = FakePaperLookupRepository(otherwise: .failed(.invalidUserKey))
        let viewModel = makeViewModel(lookup)
        await type("10.1038/nature14539", into: viewModel)
        #expect(viewModel.lookup == .failed(.invalidUserKey))

        lookup.setResult(.found(SamplePapers.bert), for: .doi("10.1038/nature14539"))
        try await preferences.setUserAPIKey("fixed-key")

        #expect(await eventually { viewModel.lookup == .found(SamplePapers.bert) })
        #expect(lookup.lookups.count == 2)
        #expect(search.calls.isEmpty)
    }

    @Test func notFoundOffersArxivsTitleAndSearchForSubmitsIt() async {
        let lookup = FakePaperLookupRepository(results: [.arxiv("1810.04805"): .notFound(arxivTitle: bertTitle)])
        let viewModel = makeViewModel(lookup)
        await type("1810.04805", into: viewModel)
        #expect(viewModel.lookup == .notFound(.arxiv("1810.04805"), searchTitle: bertTitle))

        viewModel.searchTitle(bertTitle)
        await viewModel.waitForPendingWork()

        #expect(viewModel.text == bertTitle)
        #expect(viewModel.lookup == nil)
        #expect(search.calls.map(\.query.text) == [bertTitle])
    }

    @Test func theSameIDAgainDoesNotLookUpAgain() async {
        let lookup = FakePaperLookupRepository(results: [.arxiv("1706.03762"): .found(SamplePapers.attention)])
        let viewModel = makeViewModel(lookup)
        await type("1706.03762", into: viewModel)
        await type("arXiv:1706.03762v2", into: viewModel)

        #expect(lookup.lookups == [.arxiv("1706.03762")])
    }

    @Test func aRestoredIDIsLookedUpAtOnce() async {
        let lookup = FakePaperLookupRepository(results: [.arxiv("1706.03762"): .found(SamplePapers.attention)])
        let viewModel = makeViewModel(lookup)

        viewModel.restore(text: "1706.03762", query: SearchQuery(text: ""))
        await viewModel.waitForPendingWork()

        #expect(viewModel.lookup == .found(SamplePapers.attention))
    }

    @Test func startFreshClearsTextChipsAndLookupAndRequestsFocusOnce() async {
        let lookup = FakePaperLookupRepository(results: [.arxiv("1706.03762"): .found(SamplePapers.attention)])
        let viewModel = makeViewModel(lookup)
        viewModel.setSort(.newest)
        viewModel.setOpenAccessOnly(true)
        await type("1706.03762", into: viewModel)

        viewModel.startFresh(focus: true)

        #expect(viewModel.text == "")
        #expect(viewModel.query == SearchQuery(text: ""))
        #expect(viewModel.lookup == nil)
        #expect(viewModel.phase == .idle)
        #expect(viewModel.focusRequested)

        viewModel.focusHandled()
        #expect(!viewModel.focusRequested)
    }

    @Test func savingFromTheFoundPreviewUsesTheLibrary() async {
        let lookup = FakePaperLookupRepository(results: [.arxiv("1706.03762"): .found(SamplePapers.attention)])
        let viewModel = makeViewModel(lookup)
        await type("1706.03762", into: viewModel)

        await viewModel.toggleSave(SamplePapers.attention)

        #expect(await eventually { viewModel.isSaved(SamplePapers.attention) })
        #expect(library.savedPapers == [SamplePapers.attention])
    }
}
