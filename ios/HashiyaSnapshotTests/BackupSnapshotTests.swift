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
}
