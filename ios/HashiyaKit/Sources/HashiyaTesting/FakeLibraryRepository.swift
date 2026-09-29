import Foundation
import HashiyaData
import HashiyaModel
import os

/// An in-memory library with live streams. Saves get increasing times, so the newest is first.
/// Its search is a simple stand-in for the real index: every typed word must start a word of the paper's title,
/// authors, abstract or venue, compared through `searchableText`.
public final class FakeLibraryRepository: LibraryRepository {
    public struct Failure: Error {}

    private struct Entry {
        var paper: Paper
        var localID: String
        var savedAt: Int64
        var status: ReadingStatus
    }

    private struct Subscription {
        let query: String
        let status: ReadingStatus?
        let continuation: AsyncStream<LibrarySnapshot>.Continuation
    }

    private struct State {
        var entries: [Entry] = []
        var clock: Int64 = 0
        var failSaves = false
        var failRemoves = false
        var failStatusUpdates = false
        var subscriptions: [UUID: Subscription] = [:]
        var idContinuations: [UUID: AsyncStream<Set<String>>.Continuation] = [:]

        var library: [LibraryPaper] {
            entries.sorted { $0.savedAt > $1.savedAt }.map { LibraryPaper(paper: $0.paper, status: $0.status) }
        }
        var ids: Set<String> { Set(entries.map(\.paper.openAlexID)) }

        func snapshot(query: String, status: ReadingStatus?) -> LibrarySnapshot {
            let matching = library.filter { FakeLibraryRepository.matches($0.paper, query: query) }
            var counts = Dictionary(uniqueKeysWithValues: ReadingStatus.allCases.map { ($0, 0) })
            for paper in matching {
                counts[paper.status, default: 0] += 1
            }
            return LibrarySnapshot(
                papers: matching.filter { status == nil || $0.status == status },
                counts: counts,
                libraryTotal: entries.count
            )
        }

        func publish() {
            for subscription in subscriptions.values {
                subscription.continuation.yield(snapshot(query: subscription.query, status: subscription.status))
            }
            let ids = ids
            idContinuations.values.forEach { $0.yield(ids) }
        }
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    /// `saved` is the initial library, newest first; `statuses` gives some of them a status by OpenAlex ID (else To read).
    public init(saved: [Paper] = [], statuses: [String: ReadingStatus] = [:]) {
        state.withLock { state in
            for paper in saved.reversed() {
                state.clock += 1
                state.entries.append(Entry(
                    paper: paper,
                    localID: "local-\(paper.openAlexID)",
                    savedAt: state.clock,
                    status: statuses[paper.openAlexID] ?? .toRead
                ))
            }
        }
    }

    public var savedPapers: [Paper] { state.withLock { $0.library.map(\.paper) } }
    /// The library with statuses, newest first.
    public var library: [LibraryPaper] { state.withLock { $0.library } }

    /// When true, `save` and `restore` throw.
    public func setFailSaves(_ fail: Bool) { state.withLock { $0.failSaves = fail } }
    /// When true, `remove` throws.
    public func setFailRemoves(_ fail: Bool) { state.withLock { $0.failRemoves = fail } }
    /// When true, `setStatus` throws.
    public func setFailStatusUpdates(_ fail: Bool) { state.withLock { $0.failStatusUpdates = fail } }

    public func observeLibrary(query: String, status: ReadingStatus?) -> AsyncStream<LibrarySnapshot> {
        let id = UUID()
        return AsyncStream { continuation in
            state.withLock { state in
                state.subscriptions[id] = Subscription(query: query, status: status, continuation: continuation)
                continuation.yield(state.snapshot(query: query, status: status))
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.withLock { $0.subscriptions.removeValue(forKey: id) }
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
            state.entries.append(Entry(paper: paper, localID: "local-\(paper.openAlexID)", savedAt: state.clock, status: .toRead))
            state.publish()
        }
    }

    public func setStatus(openAlexID: String, status: ReadingStatus) async throws {
        try state.withLock { state in
            if state.failStatusUpdates { throw Failure() }
            guard let index = state.entries.firstIndex(where: { $0.paper.openAlexID == openAlexID }) else { return }
            state.entries[index].status = status
            state.publish()
        }
    }

    public func remove(openAlexID: String) async throws -> RemovedPaper? {
        try state.withLock { state in
            if state.failRemoves { throw Failure() }
            guard let index = state.entries.firstIndex(where: { $0.paper.openAlexID == openAlexID }) else { return nil }
            let entry = state.entries.remove(at: index)
            state.publish()
            return RemovedPaper(paper: entry.paper, localID: entry.localID, savedAt: entry.savedAt, status: entry.status)
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
            state.entries.append(Entry(paper: removed.paper, localID: removed.localID, savedAt: removed.savedAt, status: removed.status))
            state.publish()
        }
    }

    static func matches(_ paper: Paper, query: String) -> Bool {
        let wanted = words(query)
        guard !wanted.isEmpty else { return true }
        let text = [paper.title, paper.authors.map(\.name).joined(separator: " "), paper.abstract ?? "", paper.venue ?? ""]
        let available = words(text.joined(separator: " "))
        return wanted.allSatisfy { word in available.contains { $0.hasPrefix(word) } }
    }

    private static func words(_ text: String) -> [String] {
        searchableText(text).split { !$0.isLetter && !$0.isNumber }.map(String.init)
    }
}
