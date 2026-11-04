@testable import FeatureImport
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing

@MainActor
@Suite(.serialized)
struct ImportSnapshotTests {
    private static let counts = ImportCounts(found: 126, matched: 98, local: 21, inLibrary: 4, repeated: 2, unreadable: 1, notChecked: 3)

    /// The screen once its view model shows `job`'s state.
    private func view(_ job: ImportJob?) async -> ImportView {
        let viewModel = ImportViewModel(repository: FakeImportRepository(job: job))
        Task { await viewModel.observe() }
        _ = await eventually { viewModel.state == .make(job: job, reading: false, readFailed: false) }
        return ImportView(viewModel: viewModel) {}
    }

    private func job(
        _ state: ImportState, processed: Int = 0, imported: Int = 0, collectionID: Int64? = nil, failure: ImportFailure? = nil
    ) -> ImportJob {
        ImportJob(
            id: 7, fileName: "My thesis.bib", state: state, counts: Self.counts, processed: processed, imported: imported,
            collectionName: "My thesis", collectionID: collectionID, failure: failure
        )
    }

    @Test func start() async {
        assertHashiyaSnapshots(of: await view(nil), named: "start", arabicText: "الاستيراد من BibTeX أو RIS")
    }

    @Test func ready() async {
        assertHashiyaSnapshots(of: await view(job(.ready)), named: "ready", arabicText: "مطابقة في OpenAlex")
    }

    @Test func importing() async {
        assertHashiyaSnapshots(of: await view(job(.importing, processed: 50)), named: "importing", arabicText: "إلغاء")
    }

    @Test func done() async {
        assertHashiyaSnapshots(of: await view(job(.done, processed: 119, imported: 119, collectionID: 3)), named: "done", arabicText: "عرض في المكتبة")
    }

    @Test func failed() async {
        assertHashiyaSnapshots(of: await view(job(.failed, failure: .unsupported)), named: "failed", arabicText: "هذا الملف ليس بصيغة BibTeX أو RIS.")
    }
}
