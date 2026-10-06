@testable import FeatureLibrary
import Foundation
import HashiyaData
import HashiyaDiagnostics
import HashiyaModel
import HashiyaTesting
import Testing

@MainActor
struct LibraryReviewPromptTests {
    private let review = FakeReviewPrompting()
    private let library = FakeLibraryRepository(saved: [SamplePapers.attention])

    private func makeViewModel(shareResult: Bool) -> LibraryViewModel {
        LibraryViewModel(
            library: library,
            collections: FakeCollectionsRepository(library: library),
            citations: FakeCitationRepository(),
            pdfs: FakePdfRepository(),
            exportFiles: ExportFiles(directory: FileManager.default.temporaryDirectory.appendingPathComponent("review-tests-\(UUID().uuidString)")),
            share: { _ in shareResult },
            sleep: ManualSleeper().sleep,
            diagnostics: .fake(review: review)
        )
    }

    @Test func anExportCountsAndAsksAfterTheShareSheetCloses() async {
        let viewModel = makeViewModel(shareResult: true)
        await viewModel.export()
        #expect(review.exports == 1)
        #expect(review.asks == 1)
    }

    @Test func aCancelledOrFailedShareNeitherCountsNorAsks() async {
        let viewModel = makeViewModel(shareResult: false)
        await viewModel.export()
        #expect(review.exports == 0)
        #expect(review.asks == 0)
    }
}
