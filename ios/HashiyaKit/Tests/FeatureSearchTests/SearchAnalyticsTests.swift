@testable import FeatureSearch
import HashiyaData
import HashiyaDiagnostics
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct SearchAnalyticsTests {
    private let analytics = FakeAnalytics()

    private func viewModel(
        _ repository: FakeSearchRepository,
        lookup: FakePaperLookupRepository = FakePaperLookupRepository(),
        library: FakeLibraryRepository = FakeLibraryRepository(),
        key: String? = nil
    ) -> SearchViewModel {
        SearchViewModel(
            repository: repository, lookup: lookup, library: library,
            preferences: FakeUserPreferencesRepository(key: key), diagnostics: .fake(analytics: analytics)
        )
    }

    private func submit(_ text: String, _ viewModel: SearchViewModel) async {
        viewModel.updateText(text)
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
    }

    @Test func aKeywordSearchSendsItsKindRouteResultsAndCategory() async {
        var page = SearchPage.of(SamplePapers.all, total: 48_210, next: "c2")
        page.route = .shared
        page.category = .ai
        let viewModel = viewModel(FakeSearchRepository(page: page))
        await submit("attention is all you need", viewModel)
        #expect(analytics.events.first == .search(kind: .keyword, hasFilters: false, route: .shared, results: .over200, category: .ai))
    }

    @Test func aSearchWithoutARouteUsesTheOwnKeyState() async {
        let viewModel = viewModel(FakeSearchRepository(page: .of([])), key: "my-key")
        await submit("bert", viewModel)
        #expect(analytics.events == [.search(kind: .keyword, hasFilters: false, route: .user, results: .zero, category: .unknown)])
    }

    @Test func aFilteredSearchSaysSo() async {
        let viewModel = viewModel(FakeSearchRepository(page: .of([], total: 0)))
        viewModel.setOpenAccessOnly(true)
        await submit("bert", viewModel)
        #expect(analytics.events.contains(.search(kind: .keyword, hasFilters: true, route: .shared, results: .zero, category: .unknown)))
    }

    @Test func noEventCarriesTheSearchText() async {
        let keywordViewModel = viewModel(FakeSearchRepository(page: .of(SamplePapers.all, total: 3)))
        await submit("Zebrafish Quokka retrieval", keywordViewModel)
        #expect(analytics.events.count == 1)

        let lookupViewModel = viewModel(
            FakeSearchRepository(page: .of([])),
            lookup: FakePaperLookupRepository(otherwise: .found(SamplePapers.attention))
        )
        await submit("10.1038/nature14539", lookupViewModel)
        #expect(analytics.events.count == 2)

        let text = analytics.events.map { "\($0.name) \($0.parameters)" }.joined()
        for word in ["Zebrafish", "Quokka", "retrieval", "10.1038", "nature14539", "Attention"] { #expect(!text.contains(word)) }
    }

    @Test func furtherPagesAndThePageCapAreCounted() async {
        let repository = FakeSearchRepository(maxPagesPerQuery: 2) { _, cursor in
            .of([SamplePapers.all[cursor == nil ? 0 : 1]], total: 500, next: "next-\(cursor ?? "first")")
        }
        let viewModel = viewModel(repository)
        await submit("bert", viewModel)
        viewModel.loadMore()
        await viewModel.waitForPendingWork()
        #expect(analytics.events.contains(.searchMore(page: 2)))
        #expect(analytics.events.contains(.searchLimitReached(.pageCap)))
    }

    @Test func theDailyLimitIsCounted() async {
        let viewModel = viewModel(FakeSearchRepository { _, _ in throw SearchError.dailyLimit(resetAt: .distantFuture) })
        await submit("bert", viewModel)
        #expect(analytics.events == [.searchLimitReached(.daily)])
    }

    @Test func aDOILookupIsASearchOfKindDOI() async {
        let lookup = FakePaperLookupRepository(otherwise: .found(SamplePapers.attention))
        let viewModel = viewModel(FakeSearchRepository(page: .of([])), lookup: lookup)
        await submit("10.1038/nature14539", viewModel)
        #expect(analytics.events == [.search(kind: .doi, hasFilters: false, route: .shared, results: .upTo25, category: nil)])
    }

    @Test func anArxivLookupWithAKeyAndALinkLookupSayWhich() async {
        let lookup = FakePaperLookupRepository(otherwise: .notFound(arxivTitle: nil))
        let viewModel = viewModel(FakeSearchRepository(page: .of([])), lookup: lookup, key: "my-key")
        await submit("arXiv:1706.03762", viewModel)
        await submit("https://arxiv.org/abs/1707.03762", viewModel)
        #expect(analytics.events == [
            .search(kind: .arxiv, hasFilters: false, route: .user, results: .zero, category: nil),
            .search(kind: .link, hasFilters: false, route: .user, results: .zero, category: nil),
        ])
    }

    @Test func aFailedLookupSendsNothing() async {
        let lookup = FakePaperLookupRepository(otherwise: .failed(.offline))
        let viewModel = viewModel(FakeSearchRepository(page: .of([])), lookup: lookup)
        await submit("10.48550/arXiv.1706.03762", viewModel)
        #expect(analytics.events.isEmpty)
    }

    @Test func savingFromResultsAndFromALookupSayWhere() async {
        let viewModel = viewModel(FakeSearchRepository(page: .of([SamplePapers.bert])))
        await submit("bert", viewModel)
        await viewModel.toggleSave(SamplePapers.bert)
        #expect(analytics.events.last == .paperSaved(from: .search))

        let lookupViewModel = self.viewModel(FakeSearchRepository(page: .of([])), lookup: FakePaperLookupRepository(otherwise: .found(SamplePapers.attention)))
        await submit("arXiv:1706.03762", lookupViewModel)
        await lookupViewModel.toggleSave(SamplePapers.attention)
        #expect(analytics.events.last == .paperSaved(from: .lookup))
    }

    @Test func removingFromSearchIsFinalAtOnce() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.bert])
        let viewModel = viewModel(FakeSearchRepository(page: .of([SamplePapers.bert])), library: library)
        await submit("bert", viewModel)
        #expect(await eventually { viewModel.isSaved(SamplePapers.bert) })
        await viewModel.toggleSave(SamplePapers.bert)
        #expect(analytics.events.last == .paperRemoved)
    }
}
