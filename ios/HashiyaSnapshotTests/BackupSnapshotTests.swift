import FeatureSettings
import HashiyaData
import HashiyaTesting
import SwiftUI
import Testing

@MainActor @Suite(.serialized) struct BackupSnapshotTests {
    @Test func exportScreen() async {
        let backup = FakeLibraryBackup()
        backup.summary = BackupSummary(papers: 182, collections: 6, pdfCount: 41, pdfBytes: 238_000_000)
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(), pdfs: FakePdfRepository(), backup: backup)
        await viewModel.loadBackupSummary()
        viewModel.startExport()
        viewModel.setIncludePdfs(true)
        assertHashiyaSnapshots(of: NavigationStack { ExportBackupView(viewModel: viewModel) }, named: "export", arabicText: "تضمين ملفات PDF")
    }

    @Test func restorePreview() async {
        let backup = FakeLibraryBackup()
        backup.openResult = .ready(
            FakeLibraryBackup.preparedBackup(),
            RestorePreview(exportedAt: 1_791_122_700_000, papers: 182, collections: 6, pdfs: 41, newPapers: 150, existingPapers: 31, papersSkipped: 1)
        )
        let viewModel = RestoreViewModel(source: URL(fileURLWithPath: "/tmp/b.hashiya"), backup: backup)
        await viewModel.load()
        assertHashiyaSnapshots(of: NavigationStack { RestoreView(viewModel: viewModel, onDone: {}) }, named: "restorePreview", arabicText: "إضافة إلى المكتبة")
    }

    @Test func restoreDone() async {
        let backup = FakeLibraryBackup()
        backup.openResult = .ready(
            FakeLibraryBackup.preparedBackup(),
            RestorePreview(exportedAt: nil, papers: 1, collections: 0, pdfs: 0, newPapers: 1, existingPapers: 0, papersSkipped: 0)
        )
        backup.applyResult = RestoreResult(papersAdded: 150, notesAdded: 3, collectionsCreated: 6, pdfsAdded: 38, pdfsMissing: 3, papersSkipped: 1)
        let viewModel = RestoreViewModel(source: URL(fileURLWithPath: "/tmp/b.hashiya"), backup: backup)
        await viewModel.load()
        viewModel.confirm()
        _ = await eventually { if case .done = viewModel.state { return true }; return false }
        assertHashiyaSnapshots(of: NavigationStack { RestoreView(viewModel: viewModel, onDone: {}) }, named: "restoreDone", arabicText: "تمت استعادة النسخة الاحتياطية")
    }
}
