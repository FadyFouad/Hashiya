@testable import FeatureLibrary
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing

@MainActor
@Suite(.serialized)
struct LibrarySnapshotTests {
    private func screen(_ viewModel: LibraryViewModel) -> some View {
        NavigationStack {
            LibraryView(viewModel: viewModel, onGoToSearch: {}, onOpenSettings: {})
        }
    }

    @Test func empty() async {
        let viewModel = LibraryViewModel(library: FakeLibraryRepository())
        _ = await eventually { viewModel.isLoaded }
        assertHashiyaSnapshots(of: screen(viewModel), named: "empty", arabicText: "الذهاب إلى البحث")
    }

    @Test func papers() async {
        let saved = [SamplePapers.attention, SamplePapers.arabicTitled, SamplePapers.vit, SamplePapers.untitled]
        let viewModel = LibraryViewModel(library: FakeLibraryRepository(saved: saved))
        _ = await eventually { viewModel.papers.count == 4 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "papers", arabicText: "\u{2068}4\u{2069} أوراق")
    }

    @Test func undoBanner() async {
        let viewModel = LibraryViewModel(library: FakeLibraryRepository(saved: [SamplePapers.attention, SamplePapers.bert]))
        _ = await eventually { viewModel.papers.count == 2 }
        await viewModel.remove(SamplePapers.bert)
        _ = await eventually { viewModel.papers.count == 1 }
        assertHashiyaSnapshots(of: screen(viewModel), named: "undo", arabicText: "تمت الإزالة من المكتبة")
    }
}
