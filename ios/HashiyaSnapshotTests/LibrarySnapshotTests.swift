@testable import FeatureLibrary
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing

@MainActor
@Suite(.serialized)
struct LibrarySnapshotTests {
    private let sleeper = ManualSleeper()

    private func screen(_ viewModel: LibraryViewModel) -> some View {
        NavigationStack {
            LibraryView(viewModel: viewModel, onGoToSearch: {}, onAddPaper: {}, onOpenSettings: {})
        }
    }

    /// Attention (Reading), an Arabic title (Read), ViT and an untitled paper (To read).
    private func library() -> FakeLibraryRepository {
        FakeLibraryRepository(
            saved: [SamplePapers.attention, SamplePapers.arabicTitled, SamplePapers.vit, SamplePapers.untitled],
            statuses: [SamplePapers.attention.openAlexID: .reading, SamplePapers.arabicTitled.openAlexID: .read]
        )
    }

    @Test func empty() async {
        let viewModel = LibraryViewModel(library: FakeLibraryRepository())
        _ = await eventually { viewModel.isLoaded }
        assertHashiyaSnapshots(of: screen(viewModel), named: "empty", arabicText: "الذهاب إلى البحث")
    }

    @Test func papersWithChipsAndBadges() async {
        let viewModel = LibraryViewModel(library: library())
        _ = await eventually { viewModel.papers.count == 4 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "papers", arabicText: "قيد القراءة")
    }

    @Test func aFilteredSearch() async {
        let viewModel = LibraryViewModel(library: library(), sleep: sleeper.sleep)
        viewModel.setStatusFilter(.toRead)
        viewModel.updateText("transformers")
        viewModel.submitNow()
        _ = await eventually { viewModel.papers.count == 1 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "filtered", arabicText: "ابحث في مكتبتك")
    }

    @Test func noMatches() async {
        let viewModel = LibraryViewModel(library: library(), sleep: sleeper.sleep)
        viewModel.updateText("quantum")
        viewModel.submitNow()
        _ = await eventually { viewModel.state != .loading && viewModel.papers.isEmpty }
        assertHashiyaSnapshots(of: screen(viewModel), named: "noMatches", arabicText: "مسح البحث والفلاتر")
    }

    /// The badge's three styles. Its open menu is a system popup outside the view, which a view snapshot can't
    /// capture; `LibraryFlowTests.testChangingAStatusFiltersAndSearchesTheLibrary` checks its items and selection.
    @Test func statusBadges() {
        let badges = VStack(alignment: .leading, spacing: 12) {
            ForEach(ReadingStatus.allCases, id: \.self) { status in
                ReadingStatusBadge(status: status) { _ in }
            }
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(HashiyaColors.surface)
        assertHashiyaSnapshots(of: badges, named: "badges", arabicText: "مقروءة")
    }

    @Test func undoBanner() async {
        let viewModel = LibraryViewModel(library: FakeLibraryRepository(saved: [SamplePapers.attention, SamplePapers.bert]))
        _ = await eventually { viewModel.papers.count == 2 }
        await viewModel.remove(SamplePapers.bert)
        _ = await eventually { viewModel.papers.count == 1 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "undo", arabicText: "تمت الإزالة من المكتبة")
    }

    @Test func statusUpdateFailedBanner() async {
        let library = library()
        library.setFailStatusUpdates(true)
        let viewModel = LibraryViewModel(library: library)
        _ = await eventually { viewModel.papers.count == 4 }
        await viewModel.setStatus(of: SamplePapers.vit, to: .read)
        assertHashiyaSnapshots(of: screen(viewModel), named: "statusFailed", arabicText: "تعذّر تحديث الحالة")
    }
}
