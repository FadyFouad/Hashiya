@testable import FeatureSettings
import HashiyaData
import HashiyaModel
import HashiyaTesting
import SwiftUI
import Testing

@MainActor
@Suite(.serialized)
struct SettingsSnapshotTests {
    @Test func builtInKey() async {
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(), pdfs: FakePdfRepository(), backup: FakeLibraryBackup())
        try? await Task.sleep(for: .milliseconds(20))
        assertHashiyaSnapshots(of: SettingsView(viewModel: viewModel), named: "builtIn", arabicText: "يتم استخدام المفتاح المدمج")
    }

    @Test func userKey() async {
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(key: "my-openalex-key"), pdfs: FakePdfRepository(), backup: FakeLibraryBackup())
        _ = await eventually { viewModel.usingUserKey }
        assertHashiyaSnapshots(of: SettingsView(viewModel: viewModel), named: "userKey", arabicText: "يتم استخدام مفتاحك")
    }

    /// The Storage section with downloaded and attached PDFs, and its Delete downloaded PDFs action.
    @Test func storage() async {
        let pdfs = FakePdfRepository()
        pdfs.setStorage(PdfStorage(downloadedBytes: 12_400_000, downloadedCount: 3, attachedBytes: 2_100_000, attachedCount: 1))
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(), pdfs: pdfs, backup: FakeLibraryBackup())
        await viewModel.loadStorage()
        assertHashiyaSnapshots(of: SettingsView(viewModel: viewModel), named: "storage", arabicText: "التخزين")
    }
}
