import Foundation
import HashiyaData
import HashiyaModel
import Observation

/// The Share Extension's sheet: one lookup, then Save to library / Remove from library.
@Observable
@MainActor
public final class ShareLookupViewModel {
    public enum State: Equatable, Sendable {
        /// The shared items are still loading.
        case reading
        case looking(PaperIdentifier)
        case found(Paper)
        case notFound(PaperIdentifier)
        case failed(SearchError)
        case noIdentifier(pageTitle: String)
        case nothing
    }

    public private(set) var state: State = .reading
    public private(set) var savedIDs: Set<String> = []
    public var message: SearchMessage?

    @ObservationIgnored private let lookup: any PaperLookupRepository
    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private var identifier: PaperIdentifier?
    @ObservationIgnored private let observations = TaskBag()

    public init(lookup: any PaperLookupRepository, library: any LibraryRepository) {
        self.lookup = lookup
        self.library = library
        observations.add(Task { [weak self] in
            for await ids in library.observeSavedIDs() {
                guard let self else { return }
                self.savedIDs = ids
            }
        })
    }

    /// Shows the input; an identifier is looked up. Cancelling the calling task leaves the state as it is.
    public func start(_ input: ShareLookupInput) async {
        switch input {
        case let .lookup(identifier):
            self.identifier = identifier
            await runLookup(identifier)
        case let .noIdentifier(pageTitle):
            state = .noIdentifier(pageTitle: pageTitle)
        case .nothing:
            state = .nothing
        }
    }

    /// Runs the last lookup again.
    public func retry() async {
        guard let identifier else { return }
        await runLookup(identifier)
    }

    public func isSaved(_ paper: Paper) -> Bool {
        savedIDs.contains(paper.openAlexID)
    }

    /// Removes the paper when it is in the library, else saves it.
    public func toggleSave(_ paper: Paper) async {
        if isSaved(paper) {
            do {
                _ = try await library.remove(openAlexID: paper.openAlexID)
            } catch {
                message = .removeFailed
            }
        } else {
            do {
                try await library.save(paper)
            } catch {
                message = .saveFailed
            }
        }
    }

    private func runLookup(_ identifier: PaperIdentifier) async {
        state = .looking(identifier)
        let result = await lookup.lookup(identifier)
        guard !Task.isCancelled else { return }
        state = switch result {
        case let .found(paper): .found(paper)
        case .notFound: .notFound(identifier)
        case let .failed(error): .failed(error)
        }
    }
}
