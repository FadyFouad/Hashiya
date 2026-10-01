@testable import FeatureLibrary
import Foundation
import HashiyaData
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

    private func makeViewModel(_ library: FakeLibraryRepository, collections: FakeCollectionsRepository? = nil) -> LibraryViewModel {
        LibraryViewModel(
            library: library,
            collections: collections ?? FakeCollectionsRepository(library: library),
            citations: FakeCitationRepository(),
            exportFiles: ExportFiles(directory: FileManager.default.temporaryDirectory.appendingPathComponent("library-snapshots")),
            share: { _ in true },
            sleep: sleeper.sleep
        )
    }

    @Test func empty() async {
        let viewModel = makeViewModel(FakeLibraryRepository())
        _ = await eventually { viewModel.isLoaded }
        assertHashiyaSnapshots(of: screen(viewModel), named: "empty", arabicText: "الذهاب إلى البحث")
    }

    @Test func papersWithChipsAndBadges() async {
        let viewModel = makeViewModel(library())
        _ = await eventually { viewModel.papers.count == 4 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "papers", arabicText: "قيد القراءة")
    }

    @Test func aFilteredSearch() async {
        let viewModel = makeViewModel(library())
        viewModel.setStatusFilter(.toRead)
        viewModel.updateText("transformers")
        viewModel.submitNow()
        _ = await eventually { viewModel.papers.count == 1 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "filtered", arabicText: "ابحث في مكتبتك")
    }

    @Test func noMatches() async {
        let viewModel = makeViewModel(library())
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
        let viewModel = makeViewModel(FakeLibraryRepository(saved: [SamplePapers.attention, SamplePapers.bert]))
        _ = await eventually { viewModel.papers.count == 2 }
        await viewModel.remove(SamplePapers.bert)
        _ = await eventually { viewModel.papers.count == 1 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "undo", arabicText: "تمت الإزالة من المكتبة")
    }

    @Test func statusUpdateFailedBanner() async {
        let library = library()
        library.setFailStatusUpdates(true)
        let viewModel = makeViewModel(library)
        _ = await eventually { viewModel.papers.count == 4 }
        await viewModel.setStatus(of: SamplePapers.vit, to: .read)
        assertHashiyaSnapshots(of: screen(viewModel), named: "statusFailed", arabicText: "تعذّر تحديث الحالة")
    }

    @Test func aCollection() async throws {
        let library = library()
        let collections = FakeCollectionsRepository(library: library)
        guard case let .done(id) = try await collections.create(name: "Thesis") else { return }
        try await collections.setMembership(collectionID: id, openAlexID: SamplePapers.attention.openAlexID, member: true)
        try await collections.setMembership(collectionID: id, openAlexID: SamplePapers.arabicTitled.openAlexID, member: true)
        let viewModel = makeViewModel(library, collections: collections)
        viewModel.selectCollection(id)
        _ = await eventually { viewModel.papers.count == 2 && viewModel.selectedCollection != nil }
        assertHashiyaSnapshots(of: screen(viewModel), named: "collection", arabicText: "قيد القراءة")
    }

    @Test func anEmptyCollection() async throws {
        let library = library()
        let collections = FakeCollectionsRepository(library: library)
        guard case let .done(id) = try await collections.create(name: "Thesis") else { return }
        let viewModel = makeViewModel(library, collections: collections)
        viewModel.selectCollection(id)
        _ = await eventually { viewModel.state == .emptyCollection && viewModel.selectedCollection != nil }
        assertHashiyaSnapshots(
            of: screen(viewModel),
            named: "collectionEmpty",
            arabicText: "لا توجد أوراق في هذه المجموعة بعد. أضف الأوراق من شاشة تفاصيلها."
        )
    }

    @Test func removedFromCollectionBanner() async throws {
        let library = library()
        let collections = FakeCollectionsRepository(library: library)
        guard case let .done(id) = try await collections.create(name: "Thesis") else { return }
        try await collections.setMembership(collectionID: id, openAlexID: SamplePapers.attention.openAlexID, member: true)
        try await collections.setMembership(collectionID: id, openAlexID: SamplePapers.vit.openAlexID, member: true)
        let viewModel = makeViewModel(library, collections: collections)
        viewModel.selectCollection(id)
        _ = await eventually { viewModel.papers.count == 2 && viewModel.selectedCollection != nil }
        await viewModel.removeFromCollection(openAlexID: SamplePapers.vit.openAlexID)
        _ = await eventually { viewModel.papers.count == 1 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "removedFromCollection", arabicText: "تراجع")
    }
}
