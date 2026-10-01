import Foundation
import HashiyaData
import HashiyaModel
import os

/// One `setMembership` call that didn't throw (including one that changed nothing).
public struct MembershipCall: Equatable, Sendable {
    public var collectionID: Int64
    public var openAlexID: String
    public var member: Bool

    public init(collectionID: Int64, openAlexID: String, member: Bool) {
        self.collectionID = collectionID
        self.openAlexID = openAlexID
        self.member = member
    }
}

/// In-memory collections with live streams, validating names like the real repository. Given a `FakeLibraryRepository`,
/// it mirrors memberships and deletions into it, so a Library filtered by a collection follows. The two fakes keep separate
/// membership records: removing a paper from the library does not change this fake's counts or IDs, unlike the real store's
/// cascade, so a test that needs that calls `setMemberships` or `setCollections` itself.
public final class FakeCollectionsRepository: CollectionsRepository {
    public struct Failure: Error {}

    private struct State {
        var collections: [PaperCollection]
        /// Collection IDs per OpenAlex ID.
        var memberships: [String: Set<Int64>]
        var nextID: Int64
        var failWrites = false
        var nextResult: CollectionResult?
        /// Non-nil while creates are held: the waiting creates.
        var heldCreates: [CheckedContinuation<Void, Never>]?
        var createdNames: [String] = []
        var renamed: [Int64: String] = [:]
        var membershipCalls: [MembershipCall] = []
        var deletedIDs: [Int64] = []
        var collectionSubscriptions: [UUID: AsyncStream<[PaperCollection]>.Continuation] = [:]
        var idSubscriptions: [UUID: (openAlexID: String, continuation: AsyncStream<Set<Int64>>.Continuation)] = [:]

        var sorted: [PaperCollection] { collections.sorted { collectionNameKey($0.name) < collectionNameKey($1.name) } }

        func isTaken(_ name: String, except id: Int64? = nil) -> Bool {
            let key = collectionNameKey(name)
            return collections.contains { $0.id != id && collectionNameKey($0.name) == key }
        }

        func publish() {
            let sorted = sorted
            collectionSubscriptions.values.forEach { $0.yield(sorted) }
            for subscription in idSubscriptions.values {
                subscription.continuation.yield(memberships[subscription.openAlexID] ?? [])
            }
        }

        mutating func setCount(_ id: Int64, by delta: Int) {
            guard let index = collections.firstIndex(where: { $0.id == id }) else { return }
            let collection = collections[index]
            collections[index] = PaperCollection(id: id, name: collection.name, paperCount: collection.paperCount + delta)
        }
    }

    private let state: OSAllocatedUnfairLock<State>
    private let library: FakeLibraryRepository?

    /// - Parameters:
    ///   - collections: the initial collections, with the counts given.
    ///   - memberships: collection IDs per OpenAlex ID.
    ///   - library: when given, memberships and deletions are mirrored into it.
    public init(
        collections: [PaperCollection] = [],
        memberships: [String: Set<Int64>] = [:],
        library: FakeLibraryRepository? = nil
    ) {
        self.library = library
        state = OSAllocatedUnfairLock(initialState: State(
            collections: collections,
            memberships: memberships,
            nextID: (collections.map(\.id).max() ?? 0) + 1
        ))
        for (openAlexID, ids) in memberships {
            for id in ids { library?.setCollectionMembership(collectionID: id, openAlexID: openAlexID, member: true) }
        }
    }

    /// The names of the creates that returned `.done`, as passed, in order.
    public var createdNames: [String] { state.withLock { $0.createdNames } }
    /// The last name each collection was renamed to.
    public var renamed: [Int64: String] { state.withLock { $0.renamed } }
    public var membershipCalls: [MembershipCall] { state.withLock { $0.membershipCalls } }
    public var deletedIDs: [Int64] { state.withLock { $0.deletedIDs } }
    /// One paper's collection IDs, read synchronously for assertions.
    public func collectionIDs(of openAlexID: String) -> Set<Int64> { state.withLock { $0.memberships[openAlexID] ?? [] } }
    /// The creates waiting while creates are held.
    public var heldCreates: Int { state.withLock { $0.heldCreates?.count ?? 0 } }

    /// When true, every write (`create`, `rename`, `delete`, `setMembership`) throws and changes nothing.
    public func setFailWrites(_ fail: Bool) { state.withLock { $0.failWrites = fail } }
    /// The next `create` or `rename` returns `result` without changing anything.
    public func setNextResult(_ result: CollectionResult?) { state.withLock { $0.nextResult = result } }

    /// Replaces the collections and re-emits.
    public func setCollections(_ collections: [PaperCollection]) {
        state.withLock { state in
            state.collections = collections
            state.nextID = max(state.nextID, (collections.map(\.id).max() ?? 0) + 1)
            state.publish()
        }
    }

    /// Replaces one paper's collection IDs and re-emits (not mirrored into the library).
    public func setMemberships(openAlexID: String, _ ids: Set<Int64>) {
        state.withLock { state in
            state.memberships[openAlexID] = ids
            state.publish()
        }
    }

    /// From now on `create` waits until `releaseCreates()`, so a test can tap Create twice while one runs.
    public func holdCreates() {
        state.withLock { if $0.heldCreates == nil { $0.heldCreates = [] } }
    }

    /// Lets every held create run, in order, and stops holding.
    public func releaseCreates() {
        let waiting = state.withLock { state -> [CheckedContinuation<Void, Never>] in
            defer { state.heldCreates = nil }
            return state.heldCreates ?? []
        }
        waiting.forEach { $0.resume() }
    }

    public func observeCollections() -> AsyncStream<[PaperCollection]> {
        let id = UUID()
        return AsyncStream { continuation in
            state.withLock { state in
                state.collectionSubscriptions[id] = continuation
                continuation.yield(state.sorted)
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.withLock { $0.collectionSubscriptions.removeValue(forKey: id) }
            }
        }
    }

    public func observeCollectionIDs(openAlexID: String) -> AsyncStream<Set<Int64>> {
        let id = UUID()
        return AsyncStream { continuation in
            state.withLock { state in
                state.idSubscriptions[id] = (openAlexID, continuation)
                continuation.yield(state.memberships[openAlexID] ?? [])
            }
            continuation.onTermination = { [weak self] _ in
                _ = self?.state.withLock { $0.idSubscriptions.removeValue(forKey: id) }
            }
        }
    }

    public func create(name: String) async throws -> CollectionResult {
        await waitWhileHeld()
        return try state.withLock { state in
            if state.failWrites { throw Failure() }
            if let result = state.nextResult {
                state.nextResult = nil
                return result
            }
            guard isValidCollectionName(name) else { return .invalidName }
            guard !state.isTaken(name) else { return .nameTaken }
            let id = state.nextID
            state.nextID += 1
            state.collections.append(PaperCollection(id: id, name: trimmedCollectionName(name), paperCount: 0))
            state.createdNames.append(name)
            state.publish()
            return .done(id: id)
        }
    }

    public func rename(id: Int64, name: String) async throws -> CollectionResult {
        try state.withLock { state in
            if state.failWrites { throw Failure() }
            if let result = state.nextResult {
                state.nextResult = nil
                return result
            }
            guard isValidCollectionName(name) else { return .invalidName }
            guard let index = state.collections.firstIndex(where: { $0.id == id }) else { return .notFound }
            guard !state.isTaken(name, except: id) else { return .nameTaken }
            let collection = state.collections[index]
            state.collections[index] = PaperCollection(id: id, name: trimmedCollectionName(name), paperCount: collection.paperCount)
            state.renamed[id] = trimmedCollectionName(name)
            state.publish()
            return .done(id: id)
        }
    }

    public func delete(id: Int64) async throws {
        try state.withLock { state in
            if state.failWrites { throw Failure() }
            state.deletedIDs.append(id)
            state.collections.removeAll { $0.id == id }
            for key in state.memberships.keys {
                state.memberships[key]?.remove(id)
            }
            state.publish()
        }
        library?.removeCollection(id)
    }

    public func setMembership(collectionID: Int64, openAlexID: String, member: Bool) async throws {
        // As the real repository: adding an unsaved paper does nothing at all.
        if member, let library, !library.isSaved(openAlexID: openAlexID) {
            if state.withLock({ $0.failWrites }) { throw Failure() }
            return
        }
        let changed = try state.withLock { state -> Bool in
            if state.failWrites { throw Failure() }
            state.membershipCalls.append(MembershipCall(collectionID: collectionID, openAlexID: openAlexID, member: member))
            guard state.collections.contains(where: { $0.id == collectionID }) else { return false }
            var ids = state.memberships[openAlexID] ?? []
            let wasMember = ids.contains(collectionID)
            guard wasMember != member else { return false }
            if member { ids.insert(collectionID) } else { ids.remove(collectionID) }
            state.memberships[openAlexID] = ids
            state.setCount(collectionID, by: member ? 1 : -1)
            state.publish()
            return true
        }
        if changed {
            library?.setCollectionMembership(collectionID: collectionID, openAlexID: openAlexID, member: member)
        }
    }

    private func waitWhileHeld() async {
        await withCheckedContinuation { (continuation: CheckedContinuation<Void, Never>) in
            let held = state.withLock { state -> Bool in
                guard state.heldCreates != nil else { return false }
                state.heldCreates?.append(continuation)
                return true
            }
            if !held { continuation.resume() }
        }
    }
}
