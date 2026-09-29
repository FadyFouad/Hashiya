import Foundation
import HashiyaData
import HashiyaModel
import os

/// An in-memory library with live streams. Saves get increasing times, so the newest is first.
public final class FakeLibraryRepository: LibraryRepository {
    public struct Failure: Error {}

    private struct Entry {
        var paper: Paper
        var localID: String
        var savedAt: Int64
    }

    private struct State {
        var entries: [Entry] = []
        var clock: Int64 = 0
        var failSaves = false
        var failRemoves = false
        var paperContinuations: [UUID: AsyncStream<[Paper]>.Continuation] = [:]
        var idContinuations: [UUID: AsyncStream<Set<String>>.Continuation] = [:]

        var papers: [Paper] { entries.sorted { $0.savedAt > $1.savedAt }.map(\.paper) }
        var ids: Set<String> { Set(entries.map(\.paper.openAlexID)) }

        func publish() {
            let papers = papers
            let ids = ids
            paperContinuations.values.forEach { $0.yield(papers) }
            idContinuations.values.forEach { $0.yield(ids) }
        }
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    /// `saved` is the initial library, newest first.
    public init(saved: [Paper] = []) {
        state.withLock { state in
            for paper in saved.reversed() {
                state.clock += 1
                state.entries.append(Entry(paper: paper, localID: "local-\(paper.openAlexID)", savedAt: state.clock))
            }
        }
    }

    public var savedPapers: [Paper] { state.withLock { $0.papers } }

    /// When true, `save` and `restore` throw.
    public func setFailSaves(_ fail: Bool) { state.withLock { $0.failSaves = fail } }
    /// When true, `remove` throws.
    public func setFailRemoves(_ fail: Bool) { state.withLock { $0.failRemoves = fail } }

    public func observeSavedPapers() -> AsyncStream<[Paper]> {
        let id = UUID()
        return AsyncStream { continuation in
            state.withLock { state in
                state.paperContinuations[id] = continuation
                continuation.yield(state.papers)
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.withLock { $0.paperContinuations.removeValue(forKey: id) }
            }
        }
    }

    public func observeSavedIDs() -> AsyncStream<Set<String>> {
        let id = UUID()
        return AsyncStream { continuation in
            state.withLock { state in
                state.idContinuations[id] = continuation
                continuation.yield(state.ids)
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.withLock { $0.idContinuations.removeValue(forKey: id) }
            }
        }
    }

    public func save(_ paper: Paper) async throws {
        try state.withLock { state in
            if state.failSaves { throw Failure() }
            guard !state.ids.contains(paper.openAlexID) else { return }
            state.clock += 1
            state.entries.append(Entry(paper: paper, localID: "local-\(paper.openAlexID)", savedAt: state.clock))
            state.publish()
        }
    }

    public func remove(openAlexID: String) async throws -> RemovedPaper? {
        try state.withLock { state in
            if state.failRemoves { throw Failure() }
            guard let index = state.entries.firstIndex(where: { $0.paper.openAlexID == openAlexID }) else { return nil }
            let entry = state.entries.remove(at: index)
            state.publish()
            return RemovedPaper(paper: entry.paper, localID: entry.localID, savedAt: entry.savedAt)
        }
    }

    /// Emits the current values again.
    public func refreshAfterExternalChanges() async {
        state.withLock { $0.publish() }
    }

    public func restore(_ removed: RemovedPaper) async throws {
        try state.withLock { state in
            if state.failSaves { throw Failure() }
            guard !state.ids.contains(removed.paper.openAlexID) else { return }
            state.entries.append(Entry(paper: removed.paper, localID: removed.localID, savedAt: removed.savedAt))
            state.publish()
        }
    }
}
