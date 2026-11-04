import Foundation
import HashiyaData
import HashiyaModel
import Observation

/// What the Import screen shows (spec §8.2). Android's `ImportUiState`.
public enum ImportScreenState: Equatable, Sendable {
    /// No job: explains the feature and offers the picker.
    case start
    /// Reading the chosen file, before a job exists.
    case reading
    case analyzing(ImportJob)
    case ready(ImportJob)
    case importing(ImportJob)
    case done(ImportJob)
    case failed(ImportFailure)
    case cancelled(ImportJob)

    var jobID: Int64? {
        switch self {
        case let .analyzing(job), let .ready(job), let .importing(job), let .done(job), let .cancelled(job): job.id
        case .start, .reading, .failed: nil
        }
    }

    /// The repository's job with what only the screen knows: a file being read, or one that couldn't be read.
    static func make(job: ImportJob?, reading: Bool, readFailed: Bool) -> ImportScreenState {
        if reading { return .reading }
        if readFailed { return .failed(.unreadable) }
        guard let job else { return .start }
        return switch job.state {
        case .analyzing: .analyzing(job)
        case .ready: .ready(job)
        case .importing: .importing(job)
        case .done: .done(job)
        case .failed: .failed(job.failure ?? .unreadable)
        case .cancelled: .cancelled(job)
        }
    }
}

@Observable
@MainActor
public final class ImportViewModel {
    public private(set) var state: ImportScreenState = .start
    /// The preview's "Add to a new collection" switch, on by default (spec §8.2).
    public var addToCollection = true
    private var job: ImportJob?
    private var reading = false
    private var readFailed = false
    private let repository: any ImportRepository

    public init(repository: any ImportRepository) {
        self.repository = repository
    }

    /// Follows the repository's job while the screen is shown; the view runs it in `.task`.
    public func observe() async {
        for await job in repository.observeCurrentJob() {
            self.job = job
            update()
        }
    }

    /// A file from the picker or from Open In. It is read at once, inside its security scope.
    public func open(url: URL) async {
        reading = true
        readFailed = false
        update()
        let file = await Task.detached { Self.read(url) }.value
        if let file {
            job = await repository.start(fileName: file.name, data: file.data)
        } else {
            readFailed = true
        }
        reading = false
        update()
    }

    public func confirm() async {
        guard case let .ready(job) = state else { return }
        await repository.confirm(jobID: job.id, addToCollection: addToCollection)
    }

    public func cancel() async {
        switch state {
        case let .analyzing(job), let .ready(job), let .importing(job): await repository.cancel(jobID: job.id)
        default: break
        }
    }

    /// Forgets a finished, failed or cancelled job (or a file that couldn't be read), back to the start.
    public func dismiss() async {
        readFailed = false
        update()
        if let job, !job.isRunning, job.state != .ready { await repository.dismiss(jobID: job.id) }
    }

    private func update() {
        let next = ImportScreenState.make(job: job, reading: reading, readFailed: readFailed)
        // A new preview starts with the switch on.
        if case let .ready(job) = next, state.jobID != job.id { addToCollection = true }
        state = next
    }

    /// At most one byte over the limit, so a large file is never read whole.
    nonisolated static func read(_ url: URL) -> (name: String, data: Data)? {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        guard let handle = try? FileHandle(forReadingFrom: url) else { return nil }
        defer { try? handle.close() }
        do {
            // nil at the end of the file: an empty file.
            let data = try handle.read(upToCount: maxImportFileBytes + 1) ?? Data()
            return (url.lastPathComponent, data)
        } catch {
            return nil
        }
    }
}
