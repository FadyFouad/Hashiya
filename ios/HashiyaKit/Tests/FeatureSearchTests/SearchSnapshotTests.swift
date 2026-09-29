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

    private func makeViewModel(_ repository: FakeSearchRepository = FakeSearchRepository(page: .of([]))) -> SearchViewModel {
        SearchViewModel(repository: repository, library: library, preferences: FakeUserPreferencesRepository())
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

    @Test func idle() {
        assertHashiyaSnapshots(of: screen(makeViewModel()), named: "idle", arabicText: "ابحث في OpenAlex")
    }

    @Test func loading() {
        let viewModel = makeViewModel()
        viewModel.phase = .loading
        assertHashiyaSnapshots(of: screen(viewModel), named: "loading", arabicText: "ابحث عن أوراق")
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

    @Test func appendErrorFooter() async {
        let viewModel = await resultsViewModel()
        viewModel.append = .failed(.offline)
        assertHashiyaSnapshots(of: screen(viewModel), named: "appendError", arabicText: "تعذّر تحميل المزيد من النتائج")
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
}
