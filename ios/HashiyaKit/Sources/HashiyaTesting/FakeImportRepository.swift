import Foundation
import HashiyaData
import HashiyaModel
import os

/// An import repository whose job a test sets; every call is recorded. Android's `FakeImportRepository`.
public final class FakeImportRepository: ImportRepository {
    public struct Confirm: Equatable, Sendable {
        public var jobID: Int64
        public var addToCollection: Bool

        public init(jobID: Int64, addToCollection: Bool) {
            self.jobID = jobID
            self.addToCollection = addToCollection
        }
    }

    private struct State {
        var job: ImportJob?
        var continuations: [UUID: AsyncStream<ImportJob?>.Continuation] = [:]
        var starts: [String] = []
        var confirms: [Confirm] = []
        var cancels: [Int64] = []
        var dismissals: [Int64] = []
        var resumes = 0
    }

    private let state: OSAllocatedUnfairLock<State>

    public init(job: ImportJob? = nil) {
        state = OSAllocatedUnfairLock(initialState: State(job: job))
    }

    public var starts: [String] { state.withLock { $0.starts } }
    public var confirms: [Confirm] { state.withLock { $0.confirms } }
    public var cancels: [Int64] { state.withLock { $0.cancels } }
    public var dismissals: [Int64] { state.withLock { $0.dismissals } }
    public var resumes: Int { state.withLock { $0.resumes } }

    /// Replaces the job and tells every observer.
    public func setJob(_ job: ImportJob?) {
        let continuations = state.withLock { state in
            state.job = job
            return Array(state.continuations.values)
        }
        for continuation in continuations { continuation.yield(job) }
    }

    public func observeCurrentJob() -> AsyncStream<ImportJob?> {
        AsyncStream { continuation in
            let id = UUID()
            let job = state.withLock { state in
                state.continuations[id] = continuation
                return state.job
            }
            continuation.yield(job)
            continuation.onTermination = { [state] _ in state.withLock { _ = $0.continuations.removeValue(forKey: id) } }
        }
    }

    public func start(fileName: String, data: Data) async -> ImportJob {
        let (running, id) = state.withLock { state -> (ImportJob?, Int64) in
            state.starts.append(fileName)
            return (state.job?.isRunning == true ? state.job : nil, Int64(state.starts.count))
        }
        if let running { return running }
        let job = data.count > maxImportFileBytes
            ? ImportJob(id: id, fileName: fileName, state: .failed, collectionName: importCollectionName(fileName: fileName), failure: .tooLarge)
            : ImportJob(id: id, fileName: fileName, state: .analyzing, collectionName: importCollectionName(fileName: fileName))
        setJob(job)
        return job
    }

    public func confirm(jobID: Int64, addToCollection: Bool) async {
        let job = state.withLock { state -> ImportJob? in
            state.confirms.append(Confirm(jobID: jobID, addToCollection: addToCollection))
            guard var job = state.job, job.id == jobID, job.state == .ready else { return nil }
            job.state = .importing
            job.addToCollection = addToCollection
            return job
        }
        if let job { setJob(job) }
    }

    public func cancel(jobID: Int64) async {
        let job = state.withLock { state -> ImportJob? in
            state.cancels.append(jobID)
            guard var job = state.job, job.id == jobID, [.analyzing, .ready, .importing].contains(job.state) else { return nil }
            job.state = .cancelled
            return job
        }
        if let job { setJob(job) }
    }

    public func dismiss(jobID: Int64) async {
        let forget = state.withLock { state -> Bool in
            state.dismissals.append(jobID)
            return state.job?.id == jobID && state.job?.isRunning == false
        }
        if forget { setJob(nil) }
    }

    public func resumeIfNeeded() async {
        state.withLock { $0.resumes += 1 }
    }
}
