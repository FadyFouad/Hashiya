import Foundation
import HashiyaData
import HashiyaModel
import os

/// An in-memory library with live streams. Saves get increasing times, so the newest is first.
/// Its search is a simple stand-in for the real index: every typed word must start a word of the paper's title,
/// authors, abstract, venue or notes, compared through `searchableText`. Collections are only memberships here
/// (`collectionMembers`); `FakeCollectionsRepository` keeps them in step when it is given this library.
public final class FakeLibraryRepository: LibraryRepository {
    public struct Failure: Error {}

    private struct Entry {
        var paper: Paper
        var localID: String
        var savedAt: Int64
        var status: ReadingStatus
        var notes: PaperNotes
    }

    private struct Subscription {
        let query: String
        let status: ReadingStatus?
        let collectionID: Int64?
        let continuation: AsyncStream<LibrarySnapshot>.Continuation
    }

    private struct PaperSubscription {
        let openAlexID: String
        let continuation: AsyncStream<LibraryPaper?>.Continuation
    }

    private struct State: DeferredYields {
        var pending: [@Sendable () -> Void] = []
        var entries: [Entry] = []
        /// OpenAlex IDs per collection; a collection with no members has no key.
        var collectionMembers: [Int64: Set<String>] = [:]
        var clock: Int64 = 0
        var failSaves = false
        var failRemoves = false
        var failStatusUpdates = false
        var failSaveNotes = false
        var failNotesRead = false
        /// Non-nil while saves are held: the waiting saves.
        var heldSaves: [CheckedContinuation<Void, Never>]?
        /// Non-nil while notes reads are held: the waiting reads.
        var heldReads: [CheckedContinuation<Void, Never>]?
        var notesWriteAttempts: [PaperNotes] = []
        var subscriptions: [UUID: Subscription] = [:]
        var paperSubscriptions: [UUID: PaperSubscription] = [:]
        var idContinuations: [UUID: AsyncStream<Set<String>>.Continuation] = [:]

        /// Newest saved first.
        var sorted: [Entry] { entries.sorted { $0.savedAt > $1.savedAt } }
        var library: [LibraryPaper] { sorted.map { LibraryPaper(paper: $0.paper, status: $0.status) } }
        var ids: Set<String> { Set(entries.map(\.paper.openAlexID)) }

        func inView(_ entry: Entry, collectionID: Int64?) -> Bool {
            guard let collectionID else { return true }
            return collectionMembers[collectionID]?.contains(entry.paper.openAlexID) ?? false
        }

        func snapshot(query: String, status: ReadingStatus?, collectionID: Int64?) -> LibrarySnapshot {
            let view = sorted.filter { inView($0, collectionID: collectionID) }
            let matching = view
                .filter { FakeLibraryRepository.matches($0.paper, notes: $0.notes, query: query) }
                .map { LibraryPaper(paper: $0.paper, status: $0.status) }
            var counts = Dictionary(uniqueKeysWithValues: ReadingStatus.allCases.map { ($0, 0) })
            for paper in matching {
                counts[paper.status, default: 0] += 1
            }
            return LibrarySnapshot(
                papers: matching.filter { status == nil || $0.status == status },
                counts: counts,
                libraryTotal: view.count,
                allPapersTotal: entries.count
            )
        }

        func paper(_ openAlexID: String) -> LibraryPaper? {
            entries.first { $0.paper.openAlexID == openAlexID }.map { LibraryPaper(paper: $0.paper, status: $0.status) }
        }

        /// Queues the emissions; `update` yields them once the lock is released (see `DeferredYields`).
        mutating func publish() {
            var yields: [@Sendable () -> Void] = []
            for subscription in subscriptions.values {
                let value = snapshot(query: subscription.query, status: subscription.status, collectionID: subscription.collectionID)
                yields.append { subscription.continuation.yield(value) }
            }
            for subscription in paperSubscriptions.values {
                let value = paper(subscription.openAlexID)
                yields.append { subscription.continuation.yield(value) }
            }
            let ids = ids
            for continuation in idContinuations.values {
                yields.append { continuation.yield(ids) }
            }
            pending += yields
        }

        mutating func setMembership(collectionID: Int64, openAlexID: String, member: Bool) {
            var members = collectionMembers[collectionID] ?? []
            if member { members.insert(openAlexID) } else { members.remove(openAlexID) }
            collectionMembers[collectionID] = members.isEmpty ? nil : members
        }
    }

    private let state = OSAllocatedUnfairLock(initialState: State())

    /// `saved` is the initial library, newest first; `statuses` gives some of them a status by OpenAlex ID (else To
    /// read), `notes` some notes, and `collectionMembers` the OpenAlex IDs in each collection.
    public init(
        saved: [Paper] = [],
        statuses: [String: ReadingStatus] = [:],
        notes: [String: PaperNotes] = [:],
        collectionMembers: [Int64: Set<String>] = [:]
    ) {
        state.update { state in
            for paper in saved.reversed() {
                state.clock += 1
                state.entries.append(Entry(
                    paper: paper,
                    localID: "local-\(paper.openAlexID)",
                    savedAt: state.clock,
                    status: statuses[paper.openAlexID] ?? .toRead,
                    notes: notes[paper.openAlexID] ?? PaperNotes()
                ))
            }
            state.collectionMembers = collectionMembers.filter { !$0.value.isEmpty }
        }
    }

    public var savedPapers: [Paper] { state.update { $0.library.map(\.paper) } }
    /// The library with statuses, newest first.
    public var library: [LibraryPaper] { state.update { $0.library } }
    /// The OpenAlex IDs in each collection that has any.
    public var collectionMembers: [Int64: Set<String>] { state.update { $0.collectionMembers } }
    /// Whether the paper is saved, read synchronously.
    public func isSaved(openAlexID: String) -> Bool { state.update { $0.ids.contains(openAlexID) } }
    /// A saved paper's stored notes; empty when it has none or isn't saved.
    public func notes(of openAlexID: String) -> PaperNotes {
        state.update { state in state.entries.first { $0.paper.openAlexID == openAlexID }?.notes ?? PaperNotes() }
    }
    /// Every `saveNotes` call in order once it runs (after any hold), failed ones included.
    public var notesWriteAttempts: [PaperNotes] { state.update { $0.notesWriteAttempts } }
    /// The saves waiting while saves are held.
    public var heldNotesSaves: Int { state.update { $0.heldSaves?.count ?? 0 } }
    /// The notes reads waiting while reads are held.
    public var heldNotesReads: Int { state.update { $0.heldReads?.count ?? 0 } }

    /// When true, `save` and `restore` throw.
    public func setFailSaves(_ fail: Bool) { state.update { $0.failSaves = fail } }
    /// When true, `remove` throws.
    public func setFailRemoves(_ fail: Bool) { state.update { $0.failRemoves = fail } }
    /// When true, `setStatus` throws.
    public func setFailStatusUpdates(_ fail: Bool) { state.update { $0.failStatusUpdates = fail } }
    /// When true, `saveNotes` throws.
    public func setFailSaveNotes(_ fail: Bool) { state.update { $0.failSaveNotes = fail } }
    /// When true, `notes(openAlexID:)` throws.
    public func setFailNotesRead(_ fail: Bool) { state.update { $0.failNotesRead = fail } }

    /// Adds or removes a saved paper's membership and re-emits. An unsaved paper is never added, as in the real store.
    public func setCollectionMembership(collectionID: Int64, openAlexID: String, member: Bool) {
        state.update { state in
            guard !member || state.ids.contains(openAlexID) else { return }
            state.setMembership(collectionID: collectionID, openAlexID: openAlexID, member: member)
            state.publish()
        }
    }

    /// Forgets a deleted collection's memberships and re-emits.
    public func removeCollection(_ collectionID: Int64) {
        state.update { state in
            state.collectionMembers[collectionID] = nil
            state.publish()
        }
    }

    /// From now on `saveNotes` waits until `releaseNotesSaves()`, so a test can see a write in progress.
    public func holdNotesSaves() {
        state.update { if $0.heldSaves == nil { $0.heldSaves = [] } }
    }

    /// Lets every held save run, in the order they started, and stops holding.
    public func releaseNotesSaves() {
        let waiting = state.update { state -> [CheckedContinuation<Void, Never>] in
            defer { state.heldSaves = nil }
            return state.heldSaves ?? []
        }
        waiting.forEach { $0.resume() }
    }

    /// From now on `notes(openAlexID:)` reads the stored notes at once but returns them only after
    /// `releaseNotesReads()`, so a test can change the store while a read's answer is on its way.
    public func holdNotesReads() {
        state.update { if $0.heldReads == nil { $0.heldReads = [] } }
    }

    /// Lets every held read return what it read, and stops holding.
    public func releaseNotesReads() {
        let waiting = state.update { state -> [CheckedContinuation<Void, Never>] in
            defer { state.heldReads = nil }
            return state.heldReads ?? []
        }
        waiting.forEach { $0.resume() }
    }

    public func observeLibrary(query: String, status: ReadingStatus?, collectionID: Int64?) -> AsyncStream<LibrarySnapshot> {
        let id = UUID()
        return AsyncStream { continuation in
            state.update { state in
                state.subscriptions[id] = Subscription(
                    query: query, status: status, collectionID: collectionID, continuation: continuation
                )
                continuation.yield(state.snapshot(query: query, status: status, collectionID: collectionID))
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.update { $0.subscriptions.removeValue(forKey: id) }
            }
        }
    }

    public func observeSavedIDs() -> AsyncStream<Set<String>> {
        let id = UUID()
        return AsyncStream { continuation in
            state.update { state in
                state.idContinuations[id] = continuation
                continuation.yield(state.ids)
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.update { $0.idContinuations.removeValue(forKey: id) }
            }
        }
    }

    public func observePaper(openAlexID: String) -> AsyncStream<LibraryPaper?> {
        let id = UUID()
        return AsyncStream { continuation in
            state.update { state in
                state.paperSubscriptions[id] = PaperSubscription(openAlexID: openAlexID, continuation: continuation)
                continuation.yield(state.paper(openAlexID))
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.update { $0.paperSubscriptions.removeValue(forKey: id) }
            }
        }
    }

    public func save(_ paper: Paper) async throws {
        try state.update { state in
            if state.failSaves { throw Failure() }
            guard !state.ids.contains(paper.openAlexID) else { return }
            state.clock += 1
            state.entries.append(Entry(
                paper: paper, localID: "local-\(paper.openAlexID)", savedAt: state.clock, status: .toRead, notes: PaperNotes()
            ))
            state.publish()
        }
    }

    public func setStatus(openAlexID: String, status: ReadingStatus) async throws {
        try state.update { state in
            if state.failStatusUpdates { throw Failure() }
            guard let index = state.entries.firstIndex(where: { $0.paper.openAlexID == openAlexID }) else { return }
            state.entries[index].status = status
            state.publish()
        }
    }

    public func notes(openAlexID: String) async throws -> PaperNotes {
        let (notes, held) = try state.update { state in
            if state.failNotesRead { throw Failure() }
            return (state.entries.first { $0.paper.openAlexID == openAlexID }?.notes ?? PaperNotes(), state.heldReads != nil)
        }
        guard held else { return notes }
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let held = state.update { state -> Bool in
                guard state.heldReads != nil else { return false }
                state.heldReads?.append(continuation)
                return true
            }
            if !held { continuation.resume() }
        }
        return notes
    }

    public func saveNotes(openAlexID: String, notes: PaperNotes) async throws {
        await waitWhileHeld()
        try state.update { state in
            state.notesWriteAttempts.append(notes)
            if state.failSaveNotes { throw Failure() }
            guard let index = state.entries.firstIndex(where: { $0.paper.openAlexID == openAlexID }) else { return }
            state.entries[index].notes = notes
            state.publish()
        }
    }

    public func remove(openAlexID: String) async throws -> RemovedPaper? {
        try state.update { state in
            if state.failRemoves { throw Failure() }
            guard let index = state.entries.firstIndex(where: { $0.paper.openAlexID == openAlexID }) else { return nil }
            let entry = state.entries.remove(at: index)
            let collectionIDs = Set(state.collectionMembers.filter { $0.value.contains(openAlexID) }.keys)
            for collectionID in collectionIDs {
                state.setMembership(collectionID: collectionID, openAlexID: openAlexID, member: false)
            }
            state.publish()
            return RemovedPaper(
                paper: entry.paper,
                localID: entry.localID,
                savedAt: entry.savedAt,
                status: entry.status,
                notes: entry.notes,
                collectionIDs: collectionIDs
            )
        }
    }

    /// Emits the current values again.
    public func refreshAfterExternalChanges() async {
        state.update { $0.publish() }
    }

    /// Puts the paper back with its memberships. Unlike the real store, the fake doesn't know which collections were
    /// deleted, so tests that delete one also call `removeCollection` after restoring.
    public func restore(_ removed: RemovedPaper) async throws {
        try state.update { state in
            if state.failSaves { throw Failure() }
            guard !state.ids.contains(removed.paper.openAlexID) else { return }
            state.entries.append(Entry(
                paper: removed.paper, localID: removed.localID, savedAt: removed.savedAt, status: removed.status, notes: removed.notes
            ))
            for collectionID in removed.collectionIDs {
                state.setMembership(collectionID: collectionID, openAlexID: removed.paper.openAlexID, member: true)
            }
            state.publish()
        }
    }

    private func waitWhileHeld() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let held = state.update { state -> Bool in
                guard state.heldSaves != nil else { return false }
                state.heldSaves?.append(continuation)
                return true
            }
            if !held { continuation.resume() }
        }
    }

    static func matches(_ paper: Paper, notes: PaperNotes = PaperNotes(), query: String) -> Bool {
        let wanted = words(query)
        guard !wanted.isEmpty else { return true }
        let text = [paper.title, paper.authors.map(\.name).joined(separator: " "), paper.abstract ?? "", paper.venue ?? ""]
            + NoteSection.allCases.map { notes[$0] }
        let available = words(text.joined(separator: " "))
        return wanted.allSatisfy { word in available.contains { $0.hasPrefix(word) } }
    }

    private static func words(_ text: String) -> [String] {
        searchableText(text).split { !$0.isLetter && !$0.isNumber }.map(String.init)
    }
}
