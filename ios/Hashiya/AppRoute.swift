import FeaturePaperDetails
import FeatureReader
import Foundation

/// A screen pushed in a tab: a saved paper's Details, or its reader above it. One list of these per tab drives both
/// layouts (one stack on narrow windows, list and detail panes on wide ones), so resizing a window keeps the open
/// paper, and each window saves its own list.
enum AppRoute: Hashable, Codable {
    case details(PaperDetailsRoute)
    case reader(ReaderRoute)

    /// The paper the route shows.
    var openAlexID: String {
        switch self {
        case .details(let route): route.openAlexID
        case .reader(let route): route.openAlexID
        }
    }
}

extension [AppRoute] {
    /// The routes as saved in a window's scene storage; empty when there is nothing to restore.
    init(sceneData: Data?) {
        self = sceneData.flatMap { try? JSONDecoder().decode([AppRoute].self, from: $0) } ?? []
    }

    var sceneData: Data? { try? JSONEncoder().encode(self) }
}
