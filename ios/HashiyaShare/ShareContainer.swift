import FeatureSearch
import Foundation
import HashiyaData

/// Builds the extension's view model from `LiveDependencies`, like the app's `AppContainer`.
@MainActor
enum ShareContainer {
    /// The real graph, or — in a Debug build while the UI tests have set the App Group flag — a stub lookup and
    /// the UI tests' library file.
    static func makeViewModel() -> ShareLookupViewModel {
        #if DEBUG
        if UITestingFlags.stubsEnabled {
            do {
                let library = try GRDBLibraryRepository.shared(fileName: UITestingFlags.databaseFileName)
                return ShareLookupViewModel(lookup: UITestingLookup(), library: library)
            } catch {
                fatalError("Could not open the UI-testing library: \(error)")
            }
        }
        #endif
        do {
            let dependencies = try LiveDependencies.live()
            return ShareLookupViewModel(lookup: dependencies.lookupRepository, library: dependencies.libraryRepository)
        } catch {
            fatalError("Could not open the library database: \(error)")
        }
    }
}
