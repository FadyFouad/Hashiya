@testable import FeatureSettings
import HashiyaDesignSystem
import HashiyaModel
import HashiyaTesting
import Observation
import os
import Testing

@MainActor
struct SettingsViewModelTests {
    @Test func startsOnTheBuiltInKey() async {
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(), pdfs: FakePdfRepository(), backup: FakeLibraryBackup())
        try? await Task.sleep(for: .milliseconds(20))
        #expect(!viewModel.usingUserKey)
        #expect(viewModel.keyInput == "")
    }

    @Test func showsTheStoredKey() async {
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(key: "stored-key"), pdfs: FakePdfRepository(), backup: FakeLibraryBackup())
        #expect(await eventually { viewModel.usingUserKey })
        #expect(viewModel.keyInput == "stored-key")
    }

    @Test func showsTheStoredKeyAsSoonAsItOpens() {
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(key: "stored-key"), pdfs: FakePdfRepository(), backup: FakeLibraryBackup())
        #expect(viewModel.usingUserKey)
        #expect(viewModel.keyInput == "stored-key")
    }

    /// SwiftUI builds the Settings sheet's view model inside the observation scope that builds the sheet.
    /// Reading its state in init makes that scope observe it, so each stored-key update rebuilds the sheet's
    /// view model and drops what the user typed; the Save then stores the empty field.
    @Test func creatingItDoesNotMakeTheCreatorObserveIt() async {
        let preferences = FakeUserPreferencesRepository()
        let creatorInvalidated = OSAllocatedUnfairLock(initialState: false)
        let viewModel = withObservationTracking {
            SettingsViewModel(preferences: preferences, pdfs: FakePdfRepository(), backup: FakeLibraryBackup())
        } onChange: {
            creatorInvalidated.withLock { $0 = true }
        }
        viewModel.keyInput = "my-key"
        await viewModel.save()

        #expect(await eventually { viewModel.usingUserKey })
        #expect(!creatorInvalidated.withLock { $0 })
    }

    @Test func savesTheKeyTrimmed() async {
        let preferences = FakeUserPreferencesRepository()
        let viewModel = SettingsViewModel(preferences: preferences, pdfs: FakePdfRepository(), backup: FakeLibraryBackup())
        viewModel.keyInput = "  my-key  "
        await viewModel.save()

        #expect(preferences.key == "my-key")
        #expect(await eventually { viewModel.usingUserKey })
        #expect(viewModel.keyInput == "my-key")
    }

    @Test func savingBlankRevertsToTheBuiltInKey() async {
        let preferences = FakeUserPreferencesRepository(key: "stored-key")
        let viewModel = SettingsViewModel(preferences: preferences, pdfs: FakePdfRepository(), backup: FakeLibraryBackup())
        #expect(await eventually { viewModel.usingUserKey })
        viewModel.keyInput = "   "
        await viewModel.save()

        #expect(preferences.key == nil)
        #expect(await eventually { !viewModel.usingUserKey })
        #expect(viewModel.keyInput == "")
    }

    @Test func resetRevertsToTheBuiltInKey() async {
        let preferences = FakeUserPreferencesRepository(key: "stored-key")
        let viewModel = SettingsViewModel(preferences: preferences, pdfs: FakePdfRepository(), backup: FakeLibraryBackup())
        #expect(await eventually { viewModel.usingUserKey })
        viewModel.keyInput = "half-typed"
        await viewModel.reset()

        #expect(preferences.key == nil)
        #expect(await eventually { !viewModel.usingUserKey })
        #expect(viewModel.keyInput == "")
    }

    @Test func loadsTheStorage() async {
        let pdfs = FakePdfRepository()
        pdfs.setStorage(PdfStorage(downloadedBytes: 5_000_000, downloadedCount: 3, attachedBytes: 1_000_000, attachedCount: 1))
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(), pdfs: pdfs, backup: FakeLibraryBackup())
        #expect(viewModel.storage == nil)

        await viewModel.loadStorage()

        #expect(viewModel.storage == PdfStorage(downloadedBytes: 5_000_000, downloadedCount: 3, attachedBytes: 1_000_000, attachedCount: 1))
    }

    @Test func deletingDownloadedPdfsReloadsTheStorage() async {
        let pdfs = FakePdfRepository()
        pdfs.setStorage(PdfStorage(downloadedBytes: 5_000_000, downloadedCount: 3, attachedBytes: 1_000_000, attachedCount: 1))
        let viewModel = SettingsViewModel(preferences: FakeUserPreferencesRepository(), pdfs: pdfs, backup: FakeLibraryBackup())
        await viewModel.loadStorage()

        await viewModel.deleteDownloaded()

        #expect(pdfs.deleteDownloadedCalls == 1)
        #expect(viewModel.storage == PdfStorage(downloadedBytes: 0, downloadedCount: 0, attachedBytes: 1_000_000, attachedCount: 1))
    }

    @Test func theStoragePluralsResolveInBothLanguages() {
        for language in ["en", "ar"] {
            HashiyaLanguage.override = language
            defer { HashiyaLanguage.override = nil }
            for count in [0, 1, 2, 3, 11, 100] {
                #expect(!L10n.downloadedPdfs(bytes: 2_048, count: count).contains("%"))
                #expect(!L10n.attachedPdfs(bytes: 2_048, count: count).contains("%"))
                #expect(!L10n.deleteDownloadedMessage(count: count).contains("%"))
            }
        }
        HashiyaLanguage.override = "en"
        defer { HashiyaLanguage.override = nil }
        #expect(L10n.downloadedPdfs(bytes: 0, count: 1).hasSuffix("· 1 file"))
        #expect(L10n.downloadedPdfs(bytes: 0, count: 3).hasSuffix("· 3 files"))
        #expect(L10n.deleteDownloadedMessage(count: 1) == "Delete 1 downloaded PDF? You can download it again. Attached PDFs are kept.")
    }

    @Test func theAPIKeyFooterLinksToFreeKeysInBothLanguages() {
        for language in ["en", "ar"] {
            let previous = HashiyaLanguage.override
            HashiyaLanguage.override = language
            defer { HashiyaLanguage.override = previous }
            let footer = L10n.string("settings.apiKeyFooter")
            #expect(footer.contains("(https://openalex.org/settings/api)"))
            #expect(!footer.hasPrefix("settings."))
        }
    }
}
