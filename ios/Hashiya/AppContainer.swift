import FeatureLibrary
import FeaturePaperDetails
import FeatureReader
import FeatureSearch
import FeatureSettings
import Foundation
import HashiyaData
import HashiyaDesignSystem
import HashiyaDiagnostics
import UIKit

/// Owns the long-lived objects and creates the view models. Built once per app launch.
@MainActor
final class AppContainer {
    let libraryRepository: any LibraryRepository
    let searchRepository: any SearchRepository
    let lookupRepository: any PaperLookupRepository
    let preferences: any UserPreferencesRepository
    let appUpdateRepository: any AppUpdateRepository
    let collectionsRepository: any CollectionsRepository
    let citationRepository: any CitationRepository
    /// Where Export .bib writes its file before sharing it.
    let exportFiles: ExportFiles
    let pdfRepository: any PdfRepository
    /// The app's one backup service: its lock is what allows one restore at a time across windows.
    let backup: any LibraryBackup
    /// Note writes the app waits for before it suspends the shared database in the background.
    let pendingWrites = PendingWrites()
    /// What the root views hand to the screens through the environment.
    let diagnostics: Diagnostics

    init(dependencies: LiveDependencies, appUpdateRepository: any AppUpdateRepository, diagnostics: Diagnostics = .none) {
        self.diagnostics = diagnostics
        libraryRepository = dependencies.libraryRepository
        searchRepository = dependencies.searchRepository
        lookupRepository = dependencies.lookupRepository
        preferences = dependencies.preferences
        collectionsRepository = dependencies.collections
        citationRepository = dependencies.citations
        exportFiles = dependencies.exportFiles
        pdfRepository = dependencies.pdfs
        backup = dependencies.backup
        // Files whose paper is gone (an Undo window the app didn't outlive) and unfinished downloads.
        Task { [pdfs = dependencies.pdfs] in await pdfs.sweepOrphans() }
        self.appUpdateRepository = appUpdateRepository
        // Collection, crash keys and user properties; the library's size is only known once the database has answered.
        Task { [diagnostics, backup = dependencies.backup, preferences = dependencies.preferences] in
            let papers = try? await backup.summary().papers
            await MainActor.run {
                DiagnosticsLaunch.apply(
                    diagnostics: diagnostics, privacy: PrivacySettings(), languageCode: HashiyaLanguage.code,
                    librarySize: papers, hasOwnKey: preferences.currentUserAPIKey != nil
                )
            }
            for await key in preferences.userAPIKeyUpdates() {
                diagnostics.analytics.setProperty(.hasOwnKey, key == nil ? YesNo.no : YesNo.yes)
            }
        }
    }

    /// The real graph, or — in Debug builds launched with `-ui-testing` — the UI tests' library file, stub search and lookup.
    static func make(arguments: [String] = ProcessInfo.processInfo.arguments, diagnostics: Diagnostics = .none) -> AppContainer {
        #if DEBUG
        // Tells a Debug Share Extension whether to use the UI tests' stubs; reset on every other launch.
        UITestingFlags.stubsEnabled = arguments.contains("-ui-testing")
        if arguments.contains("-ui-testing") {
            // Slow CI simulators can take longer than 4 s to tap Undo; the tests never wait for a banner to go.
            HashiyaBanner.duration = .seconds(30)
            return AppContainer(dependencies: UITestingStubs.dependencies(), appUpdateRepository: UITestingStubs.appUpdateRepository)
        }
        #endif
        do {
            return AppContainer(
                dependencies: try LiveDependencies.live(background: UIKitBackgroundTime(), crash: diagnostics.crash),
                appUpdateRepository: ConfigAppUpdateRepository.live(),
                diagnostics: diagnostics
            )
        } catch {
            diagnostics.crash.record(error, site: error.isMigrationFailure ? .migration : .databaseOpen)
            // The error's description can hold file paths: it went to the crash report sanitised, not here.
            fatalError("Could not open the library database")
        }
    }

    func makeSearchViewModel() -> SearchViewModel {
        SearchViewModel(repository: searchRepository, lookup: lookupRepository, library: libraryRepository, preferences: preferences)
    }

    func makeLibraryViewModel() -> LibraryViewModel {
        LibraryViewModel(
            library: libraryRepository,
            collections: collectionsRepository,
            citations: citationRepository,
            pdfs: pdfRepository,
            exportFiles: exportFiles,
            share: { await ShareSheet.present(fileURL: $0) }
        )
    }

    func makePaperDetailsViewModel(openAlexID: String) -> PaperDetailsViewModel {
        PaperDetailsViewModel(
            openAlexID: openAlexID,
            library: libraryRepository,
            pendingWrites: pendingWrites,
            collections: collectionsRepository,
            citations: citationRepository,
            pdfs: pdfRepository,
            copy: { UIPasteboard.general.string = $0 }
        )
    }

    /// The reader's notes and page writes go through the same `PendingWrites` as Details', so Details waits for the
    /// notes before it reads them and the app waits for both before it suspends the database.
    func makeReaderViewModel(openAlexID: String) -> ReaderViewModel {
        ReaderViewModel(
            openAlexID: openAlexID,
            pdfs: pdfRepository,
            library: libraryRepository,
            notes: NotesEditor(openAlexID: openAlexID, library: libraryRepository, pendingWrites: pendingWrites),
            pendingWrites: pendingWrites
        )
    }

    func makeSettingsViewModel() -> SettingsViewModel {
        SettingsViewModel(preferences: preferences, pdfs: pdfRepository, backup: backup, diagnostics: diagnostics)
    }

    func makeRestoreViewModel(source: URL, onSourceRead: @escaping () -> Void = {}) -> RestoreViewModel {
        RestoreViewModel(source: source, backup: backup, onSourceRead: onSourceRead, diagnostics: diagnostics)
    }

    func makeAppUpdateModel() -> AppUpdateModel {
        AppUpdateModel(repository: appUpdateRepository, currentBuild: Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String)
    }
}
