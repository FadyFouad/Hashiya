import Foundation
import HashiyaData
import HashiyaModel
import Observation
import os

@Observable
@MainActor
public final class LibraryViewModel {
    /// Saved papers, newest saved first.
    public internal(set) var papers: [Paper] = []
    /// False until the library's first value arrives.
    public internal(set) var isLoaded = false
    /// The paper in the preview sheet.
    public var selectedPaperID: String?
    /// The latest removal, which Undo can put back.
    public internal(set) var pendingUndo: RemovedPaper?

    @ObservationIgnored private let library: any LibraryRepository
    @ObservationIgnored private let observations = TaskBag()

    public init(library: any LibraryRepository) {
        self.library = library
        observations.add(Task { [weak self] in
            for await papers in library.observeSavedPapers() {
                guard let self else { return }
                self.papers = papers
                self.isLoaded = true
                if let id = self.selectedPaperID, !papers.contains(where: { $0.openAlexID == id }) {
                    self.selectedPaperID = nil
                }
            }
        })
    }

    /// The library's current copy of the selected paper; nil once it is gone.
    public var selectedPaper: Paper? {
        guard let id = selectedPaperID else { return nil }
        return papers.first { $0.openAlexID == id }
    }

    public func select(_ paper: Paper) {
        selectedPaperID = paper.openAlexID
    }

    /// Closes the preview and removes the paper; only the latest removal can be undone.
    public func remove(_ paper: Paper) async {
        selectedPaperID = nil
        do {
            if let removed = try await library.remove(openAlexID: paper.openAlexID) {
                pendingUndo = removed
            }
        } catch {
            Self.log("remove failed")
        }
    }

    /// Puts the latest removed paper back in its place.
    public func undo() async {
        guard let removed = pendingUndo else { return }
        pendingUndo = nil
        do {
            try await library.restore(removed)
        } catch {
            Self.log("restore failed")
        }
    }

    /// The Undo banner timed out.
    public func undoExpired() {
        pendingUndo = nil
    }

    private static func log(_ message: StaticString) {
        #if DEBUG
        Logger(subsystem: "com.etatech.hashiya", category: "library").error("\(message)")
        #endif
    }
}
