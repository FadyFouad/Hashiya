@testable import FeatureSearch
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct ShareLookupViewModelTests {
    private let library = FakeLibraryRepository()
    private let attentionID = PaperIdentifier.arxiv("1706.03762")

    private func makeViewModel(_ lookup: FakePaperLookupRepository) -> ShareLookupViewModel {
        ShareLookupViewModel(lookup: lookup, library: library)
    }

    @Test func itIsReadingUntilItStarts() {
        #expect(makeViewModel(FakePaperLookupRepository()).state == .reading)
    }

    @Test func aFoundPaperIsShown() async {
        let lookup = FakePaperLookupRepository(results: [attentionID: .found(SamplePapers.attention)])
        let viewModel = makeViewModel(lookup)

        await viewModel.start(.lookup(attentionID))

        #expect(viewModel.state == .found(SamplePapers.attention))
        #expect(lookup.lookups == [attentionID])
    }

    @Test func itShowsLookingUntilTheLookupFinishes() async {
        let lookup = FakePaperLookupRepository(results: [attentionID: .found(SamplePapers.attention)])
        lookup.hold()
        let viewModel = makeViewModel(lookup)
        let start = Task { await viewModel.start(.lookup(attentionID)) }
        #expect(await eventually { lookup.lookups.count == 1 })
        #expect(viewModel.state == .looking(attentionID))

        lookup.release()
        await start.value

        #expect(viewModel.state == .found(SamplePapers.attention))
    }

    @Test(arguments: [LookupResult.notFound(arxivTitle: nil), .notFound(arxivTitle: "Attention Is All You Need")])
    func notFoundNeverOffersATitleSearch(result: LookupResult) async {
        let viewModel = makeViewModel(FakePaperLookupRepository(otherwise: result))
        await viewModel.start(.lookup(attentionID))
        #expect(viewModel.state == .notFound(attentionID))
    }

    @Test(arguments: [SearchError.offline, .invalidUserKey, .rateLimited, .serviceUnavailable, .unexpected])
    func failuresAreShown(error: SearchError) async {
        let viewModel = makeViewModel(FakePaperLookupRepository(otherwise: .failed(error)))
        await viewModel.start(.lookup(attentionID))
        #expect(viewModel.state == .failed(error))
    }

    @Test func aPageWithoutAnIDShowsItsTitleWithNoRequest() async {
        let lookup = FakePaperLookupRepository()
        let viewModel = makeViewModel(lookup)

        await viewModel.start(.noIdentifier(pageTitle: "Deep learning"))

        #expect(viewModel.state == .noIdentifier(pageTitle: "Deep learning"))
        #expect(lookup.lookups.isEmpty)
    }

    @Test func nothingUsableShowsNothingWithNoRequest() async {
        let lookup = FakePaperLookupRepository()
        let viewModel = makeViewModel(lookup)

        await viewModel.start(.nothing)

        #expect(viewModel.state == .nothing)
        #expect(lookup.lookups.isEmpty)
    }

    @Test func retryRerunsTheLookup() async {
        let lookup = FakePaperLookupRepository(otherwise: .failed(.offline))
        let viewModel = makeViewModel(lookup)
        await viewModel.start(.lookup(attentionID))

        lookup.setResult(.found(SamplePapers.attention), for: attentionID)
        await viewModel.retry()

        #expect(viewModel.state == .found(SamplePapers.attention))
        #expect(lookup.lookups == [attentionID, attentionID])
    }

    @Test func saveThenRemove() async {
        let viewModel = makeViewModel(FakePaperLookupRepository(results: [attentionID: .found(SamplePapers.attention)]))
        await viewModel.start(.lookup(attentionID))

        await viewModel.toggleSave(SamplePapers.attention)
        #expect(await eventually { viewModel.isSaved(SamplePapers.attention) })
        #expect(library.savedPapers == [SamplePapers.attention])

        await viewModel.toggleSave(SamplePapers.attention)
        #expect(await eventually { !viewModel.isSaved(SamplePapers.attention) })
        #expect(library.savedPapers.isEmpty)
    }

    @Test func aSaveFailureShowsItsMessage() async {
        library.setFailSaves(true)
        let viewModel = makeViewModel(FakePaperLookupRepository())

        await viewModel.toggleSave(SamplePapers.attention)

        #expect(viewModel.message == .saveFailed)
    }

    @Test func aRemoveFailureShowsItsMessage() async {
        let library = FakeLibraryRepository(saved: [SamplePapers.attention])
        library.setFailRemoves(true)
        let viewModel = ShareLookupViewModel(lookup: FakePaperLookupRepository(), library: library)
        #expect(await eventually { viewModel.isSaved(SamplePapers.attention) })

        await viewModel.toggleSave(SamplePapers.attention)

        #expect(viewModel.message == .removeFailed)
        #expect(library.savedPapers == [SamplePapers.attention])
    }

    /// Closing the sheet cancels the lookup: a late result must not change the state.
    @Test func aCancelledLookupLeavesTheStateAlone() async {
        let lookup = FakePaperLookupRepository(results: [attentionID: .found(SamplePapers.attention)])
        lookup.hold()
        let viewModel = makeViewModel(lookup)
        let start = Task { await viewModel.start(.lookup(attentionID)) }
        #expect(await eventually { lookup.lookups.count == 1 })

        start.cancel()
        lookup.release()
        await start.value

        #expect(viewModel.state == .looking(attentionID))
    }
}
