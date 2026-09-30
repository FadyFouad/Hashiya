import FeatureLibrary
import FeaturePaperDetails
import FeatureSearch
import FeatureSettings
import Foundation
import HashiyaData
import HashiyaDesignSystem

/// Owns the long-lived objects and creates the view models. Built once per app launch.
@MainActor
final class AppContainer {
    let libraryRepository: any LibraryRepository
    let searchRepository: any SearchRepository
    let lookupRepository: any PaperLookupRepository
    let preferences: any UserPreferencesRepository
    let appUpdateRepository: any AppUpdateRepository
    /// Note writes the app waits for before it suspends the shared database in the background.
    let pendingWrites = PendingWrites()

    init(dependencies: LiveDependencies, appUpdateRepository: any AppUpdateRepository) {
        libraryRepository = dependencies.libraryRepository
        searchRepository = dependencies.searchRepository
        lookupRepository = dependencies.lookupRepository
        preferences = dependencies.preferences
        self.appUpdateRepository = appUpdateRepository
    }

    /// The real graph, or — in Debug builds launched with `-ui-testing` — the UI tests' library file, stub search and lookup.
    static func make(arguments: [String] = ProcessInfo.processInfo.arguments) -> AppContainer {
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
            return AppContainer(dependencies: try LiveDependencies.live(), appUpdateRepository: ConfigAppUpdateRepository.live())
        } catch {
            fatalError("Could not open the library database: \(error)")
        }
    }

    func makeSearchViewModel() -> SearchViewModel {
        SearchViewModel(repository: searchRepository, lookup: lookupRepository, library: libraryRepository, preferences: preferences)
    }

    func makeLibraryViewModel() -> LibraryViewModel {
        LibraryViewModel(library: libraryRepository)
    }

    func makePaperDetailsViewModel(openAlexID: String) -> PaperDetailsViewModel {
        PaperDetailsViewModel(openAlexID: openAlexID, library: libraryRepository, pendingWrites: pendingWrites)
    }

    func makeSettingsViewModel() -> SettingsViewModel {
        SettingsViewModel(preferences: preferences)
    }

    func makeAppUpdateModel() -> AppUpdateModel {
        AppUpdateModel(repository: appUpdateRepository, currentBuild: Bundle.main.object(forInfoDictionaryKey: "CFBundleVersion") as? String)
    }
}
