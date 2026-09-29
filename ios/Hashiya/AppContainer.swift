import FeatureLibrary
import FeatureSearch
import FeatureSettings
import Foundation
import HashiyaData

/// Owns the long-lived objects and creates the view models. Built once per app launch.
@MainActor
final class AppContainer {
    let libraryRepository: any LibraryRepository
    let searchRepository: any SearchRepository
    let lookupRepository: any PaperLookupRepository
    let preferences: any UserPreferencesRepository

    init(dependencies: LiveDependencies) {
        libraryRepository = dependencies.libraryRepository
        searchRepository = dependencies.searchRepository
        lookupRepository = dependencies.lookupRepository
        preferences = dependencies.preferences
    }

    /// The real graph, or — in Debug builds launched with `-ui-testing` — an in-memory library, stub search and lookup.
    static func make(arguments: [String] = ProcessInfo.processInfo.arguments) -> AppContainer {
        #if DEBUG
        if arguments.contains("-ui-testing") {
            return AppContainer(dependencies: UITestingStubs.dependencies())
        }
        #endif
        do {
            return AppContainer(dependencies: try LiveDependencies.live())
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

    func makeSettingsViewModel() -> SettingsViewModel {
        SettingsViewModel(preferences: preferences)
    }
}
