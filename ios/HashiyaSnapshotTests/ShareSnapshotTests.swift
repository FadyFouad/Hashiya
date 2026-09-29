@testable import FeatureSearch
import HashiyaData
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing

/// The Share Extension's sheet in every state.
@MainActor
@Suite(.serialized)
struct ShareSnapshotTests {
    private let attentionID = PaperIdentifier.arxiv("1706.03762")

    private func sheet(_ viewModel: ShareLookupViewModel) -> some View {
        ShareLookupView(viewModel: viewModel, readInput: { .nothing }, onDone: {})
    }

    /// A view model that has handled `input` with `lookup`, over a library holding `saved`.
    private func viewModel(
        _ input: ShareLookupInput,
        _ lookup: FakePaperLookupRepository = FakePaperLookupRepository(),
        saved: [Paper] = []
    ) async -> ShareLookupViewModel {
        let viewModel = ShareLookupViewModel(lookup: lookup, library: FakeLibraryRepository(saved: saved))
        await viewModel.start(input)
        _ = await eventually { viewModel.savedIDs == Set(saved.map(\.openAlexID)) }
        return viewModel
    }

    @Test func looking() async {
        let lookup = FakePaperLookupRepository()
        lookup.hold()
        let viewModel = ShareLookupViewModel(lookup: lookup, library: FakeLibraryRepository())
        let start = Task { await viewModel.start(.lookup(attentionID)) }
        _ = await eventually { lookup.lookups.count == 1 }
        assertHashiyaSnapshots(of: sheet(viewModel), named: "looking", arabicText: "جارٍ البحث عن arXiv \u{2068}1706.03762\u{2069}…")
        lookup.release()
        await start.value
    }

    @Test func found() async {
        let lookup = FakePaperLookupRepository(results: [attentionID: .found(SamplePapers.attention)])
        let viewModel = await viewModel(.lookup(attentionID), lookup)
        assertHashiyaSnapshots(of: sheet(viewModel), named: "found", arabicText: "حفظ في المكتبة")
    }

    @Test func foundAndSaved() async {
        let lookup = FakePaperLookupRepository(results: [attentionID: .found(SamplePapers.attention)])
        let viewModel = await viewModel(.lookup(attentionID), lookup, saved: [SamplePapers.attention])
        assertHashiyaSnapshots(of: sheet(viewModel), named: "foundSaved", arabicText: "إزالة من المكتبة")
    }

    @Test func notFound() async {
        let viewModel = await viewModel(.lookup(.doi("10.9999/nothing")))
        assertHashiyaSnapshots(of: sheet(viewModel), named: "notFound", arabicText: "لم يتم العثور على ورقة بهذا الـ DOI")
    }

    @Test func error() async {
        let viewModel = await viewModel(.lookup(attentionID), FakePaperLookupRepository(otherwise: .failed(.invalidUserKey)))
        assertHashiyaSnapshots(of: sheet(viewModel), named: "error", arabicText: "إعادة المحاولة")
    }

    @Test func noIDWithAnEnglishPageTitle() async {
        let viewModel = await viewModel(.noIdentifier(pageTitle: "Deep Residual Learning for Image Recognition | IEEE Conference Publication"))
        assertHashiyaSnapshots(of: sheet(viewModel), named: "noIDEnglishTitle", arabicText: "لا يوجد DOI أو معرّف arXiv في هذه الصفحة")
    }

    @Test func noIDWithAnArabicPageTitle() async {
        let viewModel = await viewModel(.noIdentifier(pageTitle: "تطبيقات التعلم العميق في معالجة اللغة العربية (2022)"))
        assertHashiyaSnapshots(of: sheet(viewModel), named: "noIDArabicTitle", arabicText: "لا يوجد DOI أو معرّف arXiv في هذه الصفحة")
    }

    @Test func nothing() async {
        let viewModel = await viewModel(.nothing)
        assertHashiyaSnapshots(of: sheet(viewModel), named: "nothing", arabicText: "تعذّر العثور على ورقة فيما شاركته.")
    }
}
