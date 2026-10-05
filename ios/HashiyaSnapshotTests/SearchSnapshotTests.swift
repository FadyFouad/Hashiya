@testable import FeatureSearch
import HashiyaData
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing

@MainActor
@Suite(.serialized)
struct SearchSnapshotTests {
    private let library = FakeLibraryRepository(saved: [SamplePapers.attention])

    private func makeViewModel(
        _ repository: FakeSearchRepository = FakeSearchRepository(page: .of([])),
        lookup: FakePaperLookupRepository = FakePaperLookupRepository()
    ) -> SearchViewModel {
        SearchViewModel(repository: repository, lookup: lookup, library: library, preferences: FakeUserPreferencesRepository())
    }

    private func screen(_ viewModel: SearchViewModel) -> some View {
        NavigationStack {
            SearchView(viewModel: viewModel, onOpenSettings: {})
        }
    }

    /// A view model showing results for "transformers", with Attention in the library. The page has no
    /// next cursor, so showing the last card never starts a load that could race the snapshot.
    private func resultsViewModel() async -> SearchViewModel {
        let page = SearchPage.of([SamplePapers.attention, SamplePapers.bert, SamplePapers.arabicTitled], total: 48_210, next: nil)
        let viewModel = makeViewModel(FakeSearchRepository(page: page))
        viewModel.updateText("transformers")
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
        _ = await eventually { viewModel.savedIDs == [SamplePapers.attention.openAlexID] }
        return viewModel
    }

    /// A view model that has looked up `text` with `lookup`.
    private func lookupViewModel(_ text: String, _ lookup: FakePaperLookupRepository) async -> SearchViewModel {
        let viewModel = makeViewModel(lookup: lookup)
        viewModel.updateText(text)
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
        _ = await eventually { viewModel.savedIDs == [SamplePapers.attention.openAlexID] }
        return viewModel
    }

    @Test func idle() {
        assertHashiyaSnapshots(of: screen(makeViewModel()), named: "idle", arabicText: "ابحث في OpenAlex")
    }

    @Test func loading() {
        let viewModel = makeViewModel()
        viewModel.phase = .loading
        assertHashiyaSnapshots(of: screen(viewModel), named: "loading", arabicText: "ابحث، أو الصق DOI أو معرّف arXiv أو رابطًا")
    }

    @Test func results() async {
        let viewModel = await resultsViewModel()
        assertHashiyaSnapshots(of: screen(viewModel), named: "results", arabicText: "في المكتبة")
    }

    @Test func emptyWithClearFilters() async {
        let viewModel = makeViewModel()
        viewModel.setOpenAccessOnly(true)
        viewModel.updateText("zzzz")
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
        #expect(viewModel.phase == .empty)
        assertHashiyaSnapshots(of: screen(viewModel), named: "empty", arabicText: "مسح عوامل التصفية")
    }

    @Test func offlineError() async {
        let viewModel = makeViewModel(FakeSearchRepository { _, _ in throw SearchError.offline })
        viewModel.updateText("transformers")
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
        #expect(viewModel.phase == .failed(.offline))
        assertHashiyaSnapshots(of: screen(viewModel), named: "offline", arabicText: "تعذّر الوصول إلى OpenAlex")
    }

    @Test func dailyLimitError() async {
        let reset = ISO8601DateFormatter().date(from: "2026-10-06T00:00:00Z")!
        let viewModel = makeViewModel(FakeSearchRepository { _, _ in throw SearchError.dailyLimit(resetAt: reset) })
        viewModel.updateText("transformers")
        viewModel.submitNow()
        await viewModel.waitForPendingWork()
        #expect(viewModel.phase == .failed(.dailyLimit(resetAt: reset)))
        assertHashiyaSnapshots(
            of: screen(viewModel).environment(\.timeZone, TimeZone(identifier: "Asia/Riyadh")!),
            named: "dailyLimit",
            arabicText: "تم بلوغ الحد اليومي للبحث"
        )
    }

    @Test func appendErrorFooter() async {
        let viewModel = await resultsViewModel()
        viewModel.append = .failed(.offline)
        assertHashiyaSnapshots(of: screen(viewModel), named: "appendError", arabicText: "تعذّر تحميل المزيد من النتائج")
    }

    @Test func pageCapFooter() async {
        let viewModel = await resultsViewModel()
        viewModel.append = .capReached(results: 200)
        assertHashiyaSnapshots(of: screen(viewModel), named: "pageCap", arabicText: "حسِّن بحثك لرؤية المزيد")
    }

    @Test func filtersAndBanner() async {
        let viewModel = await resultsViewModel()
        viewModel.setSort(.mostCited)
        viewModel.setYears(.between(from: 2015, to: 2020))
        viewModel.setOpenAccessOnly(true)
        await viewModel.waitForPendingWork()
        viewModel.message = .saveFailed
        assertHashiyaSnapshots(of: screen(viewModel), named: "filtersAndBanner", arabicText: "تعذّر حفظ الورقة")
    }

    @Test func lookupLooking() async {
        let lookup = FakePaperLookupRepository()
        lookup.hold()
        let viewModel = makeViewModel(lookup: lookup)
        viewModel.updateText("10.18653/v1/n19-1423")
        viewModel.submitNow()
        _ = await eventually { lookup.lookups.count == 1 }
        #expect(viewModel.lookup == .looking(.doi("10.18653/v1/n19-1423")))
        assertHashiyaSnapshots(of: screen(viewModel), named: "lookupLooking", arabicText: "جارٍ البحث عن DOI \u{2068}10.18653/v1/n19-1423\u{2069}…")
        lookup.release()
        await viewModel.waitForPendingWork()
    }

    @Test func lookupFound() async {
        let lookup = FakePaperLookupRepository(results: [.doi("10.18653/v1/n19-1423"): .found(SamplePapers.bert)])
        let viewModel = await lookupViewModel("https://doi.org/10.18653/v1/N19-1423", lookup)
        #expect(viewModel.lookup == .found(SamplePapers.bert))
        assertHashiyaSnapshots(of: screen(viewModel), named: "lookupFound", arabicText: "حفظ في المكتبة")
    }

    @Test func lookupNotFoundWithTheSearchForButton() async {
        let bertTitle = "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding"
        let lookup = FakePaperLookupRepository(results: [.arxiv("1810.04805"): .notFound(arxivTitle: bertTitle)])
        let viewModel = await lookupViewModel("1810.04805", lookup)
        #expect(viewModel.lookup == .notFound(.arxiv("1810.04805"), searchTitle: bertTitle))
        assertHashiyaSnapshots(of: screen(viewModel), named: "lookupNotFound", arabicText: "لم يتم العثور على ورقة بمعرّف arXiv هذا")
    }

    @Test func lookupError() async {
        let lookup = FakePaperLookupRepository(otherwise: .failed(.offline))
        let viewModel = await lookupViewModel("arXiv:1706.03762", lookup)
        #expect(viewModel.lookup == .failed(.offline))
        assertHashiyaSnapshots(of: screen(viewModel), named: "lookupError", arabicText: "تعذّر الوصول إلى OpenAlex")
    }

    @Test func linkWithoutAnID() async {
        let viewModel = await lookupViewModel("https://ieeexplore.ieee.org/document/1234567", FakePaperLookupRepository())
        #expect(viewModel.lookup == .noIDInLink)
        assertHashiyaSnapshots(of: screen(viewModel), named: "linkWithoutID", arabicText: "لا يوجد DOI أو معرّف arXiv في هذا الرابط")
    }
}
