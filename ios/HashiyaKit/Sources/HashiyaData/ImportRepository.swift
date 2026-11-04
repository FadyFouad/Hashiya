import Foundation
import HashiyaDatabase
import HashiyaDiagnostics
import HashiyaImport
import HashiyaModel

/// Files over this size are refused before they are parsed (spec §7.6).
public let maxImportFileBytes = 10 * 1024 * 1024

/// Importing a .bib or .ris file: one job at a time, with a preview before anything is saved. Android's `ImportRepository`.
public protocol ImportRepository: Sendable {
    /// The current job (the newest one the user hasn't dismissed), or nil. Each call returns a new stream starting with the
    /// current value.
    func observeCurrentJob() -> AsyncStream<ImportJob?>
    /// Starts a job for the file: analyzing, or failed when `data` is over `maxImportFileBytes`. A job that is still
    /// analyzing or importing is returned instead, and a finished one is dismissed first.
    func start(fileName: String, data: Data) async -> ImportJob
    /// From ready: saves the papers in the background, into a new collection named after the file when `addToCollection`.
    func confirm(jobID: Int64, addToCollection: Bool) async
    /// Stops the job. Papers already saved stay.
    func cancel(jobID: Int64) async
    /// Forgets a job that isn't running, and its copy of the file.
    func dismiss(jobID: Int64) async
    /// Continues a job the app was closed during, or one whose writes were refused in the background. Call at launch and
    /// whenever the app becomes active.
    func resumeIfNeeded() async
}

/// The job lives in the database (`import_jobs`, `import_job_papers`, schema `v6`) and the text of its file in `directory`
/// (Application Support/Imports in the app), so a job survives the app being closed and resumes later (spec §12). Each batch of
/// papers, its job links, collection links and progress are one transaction, and the import phase plans against the library as
/// it is: papers it saved before an interruption count as imported, and nothing is saved twice. Android's
/// `DefaultImportRepository` and `ImportRunner` together.
public actor LiveImportRepository: ImportRepository {
    /// Papers saved per transaction.
    static let batchSize = 50
    /// How many "name (n)" variants to try before giving up on the collection.
    static let maxCollectionNameTries = 100

    private let store: PaperStore
    private let matcher: DOIMatcher
    private let directory: URL
    private let background: any BackgroundTimeGranting
    private let analytics: any AnalyticsTracking
    private let newID: @Sendable () -> String
    private let now: @Sendable () -> Int64
    /// The analysis's lookups, reused by the import phase in the same process.
    private var lookups: [Int64: DOIMatches] = [:]
    private var running: Task<Void, Never>?
    /// A job moved on (confirmed) while the last run was finishing: run again once it has.
    private var runAgain = false

    public init(
        store: PaperStore,
        matcher: DOIMatcher,
        directory: URL,
        background: any BackgroundTimeGranting = NoBackgroundTime(),
        analytics: any AnalyticsTracking = NoAnalytics(),
        newID: @escaping @Sendable () -> String = { UUID().uuidString.lowercased() },
        now: @escaping @Sendable () -> Int64 = { Int64((Date().timeIntervalSince1970 * 1000).rounded()) }
    ) {
        self.store = store
        self.matcher = matcher
        self.directory = directory
        self.background = background
        self.analytics = analytics
        self.newID = newID
        self.now = now
    }

    /// The text of every job's file, by job id.
    public static func liveDirectory() -> URL {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appending(path: "Imports", directoryHint: .isDirectory)
    }

    // MARK: - ImportRepository

    public nonisolated func observeCurrentJob() -> AsyncStream<ImportJob?> {
        let records = store.observeLatestImportJob()
        return AsyncStream { continuation in
            let task = Task {
                for await record in records {
                    continuation.yield(record.map(ImportJob.init))
                }
                continuation.finish()
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    public func start(fileName: String, data: Data) async -> ImportJob {
        if let running = try? await store.runningImportJob() { return ImportJob(running) }
        if let latest = try? await store.latestImportJob(), let id = latest.id { await dismiss(jobID: id) }
        var record = ImportJobRecord(
            fileName: fileName, createdAt: now(), state: ImportState.analyzing.rawValue,
            collectionName: importCollectionName(fileName: fileName)
        )
        guard data.count <= maxImportFileBytes else {
            record.state = ImportState.failed.rawValue
            record.failure = ImportFailure.tooLarge.rawValue
            record.id = try? await store.createImportJob(record)
            let job = ImportJob(record)
            log(job, .tooLarge, format: nil)
            return job
        }
        let text = ImportParser.decode(data)
        guard let id = try? await store.createImportJob(record) else {
            // The database refused the write: nothing is stored, so there is no job to show.
            record.state = ImportState.failed.rawValue
            record.failure = ImportFailure.unreadable.rawValue
            return ImportJob(record)
        }
        record.id = id
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            try text.write(to: textURL(id), atomically: true, encoding: .utf8)
        } catch {
            var failed = record
            failed.state = ImportState.failed.rawValue
            failed.failure = ImportFailure.unreadable.rawValue
            if (try? await store.replaceImportJob(ifState: ImportState.analyzing.rawValue, with: failed)) == true {
                log(ImportJob(failed), .unreadable, format: nil)
            }
            return ImportJob(failed)
        }
        launch()
        return ImportJob(record)
    }

    public func confirm(jobID: Int64, addToCollection: Bool) async {
        guard var record = try? await store.importJob(id: jobID), record.state == ImportState.ready.rawValue else { return }
        record.state = ImportState.importing.rawValue
        record.addToCollection = addToCollection
        if (try? await store.replaceImportJob(ifState: ImportState.ready.rawValue, with: record)) == true { launch() }
    }

    public func cancel(jobID: Int64) async {
        guard let record = try? await store.importJob(id: jobID),
              [ImportState.analyzing, .ready, .importing].map(\.rawValue).contains(record.state) else { return }
        var cancelled = record
        cancelled.state = ImportState.cancelled.rawValue
        guard (try? await store.replaceImportJob(ifState: record.state, with: cancelled)) == true else { return }
        lookups[jobID] = nil
        // The format, for the event only: parsed off the actor from the job's copy of the file.
        let url = textURL(jobID)
        let format = await Task.detached { (try? String(contentsOf: url, encoding: .utf8)).flatMap(ImportParser.parse)?.format }.value
        log(ImportJob(record), .cancelled, format: format)
    }

    public func dismiss(jobID: Int64) async {
        guard let record = try? await store.importJob(id: jobID), !ImportJob(record).isRunning else { return }
        try? await store.deleteImportJob(id: jobID)
        try? FileManager.default.removeItem(at: textURL(jobID))
        lookups[jobID] = nil
    }

    public func resumeIfNeeded() async {
        if (try? await store.runningImportJob()) != nil { launch() }
    }

    // MARK: - Running

    /// One run at a time, inside a background-time grant. A run takes the running job phase by phase until none is left,
    /// or until a phase changes nothing (a write the suspended app's database refused): `resumeIfNeeded` continues it.
    private func launch() {
        guard running == nil else {
            runAgain = true
            return
        }
        runAgain = false
        let background = self.background
        running = Task {
            let token = await background.begin(name: "Import library", onExpiry: {})
            await self.runPhases()
            await token.end()
            self.finished()
        }
    }

    private func finished() {
        running = nil
        if runAgain { launch() }
    }

    private func runPhases() async {
        var last: (id: Int64, state: String, processed: Int)?
        while let record = try? await store.runningImportJob(), let id = record.id {
            if let last, last.id == id, last.state == record.state, last.processed == record.processed { return }
            last = (id, record.state, record.processed)
            do {
                switch ImportJob(record).state {
                case .analyzing: try await analyze(record)
                case .importing: try await importPapers(record)
                case .ready, .done, .failed, .cancelled: return
                }
            } catch {
                // A database write refused while the app is suspended in the background, or any other failed write: the job
                // stays as it is and continues when the app is active again, saving nothing twice.
                return
            }
        }
    }

    private func analyze(_ record: ImportJobRecord) async throws {
        guard let id = record.id, let parsed = try await parse(record) else { return }
        let keys = try await store.libraryKeys(jobID: id)
        let plan = ImportPlanner.plan(parsed.entries, existing: existingLibrary(keys, includeImported: false))
        let matches = await matcher.match(Self.lookups(plan))
        lookups[id] = matches
        let outcomes = Self.resolve(plan, matches: matches, keys: keys)
        var next = record
        next.found = parsed.entries.count
        next.matched = outcomes.count { if case .matched = $0 { true } else { false } }
        next.local = outcomes.count { if case .local = $0 { true } else { false } }
        next.inLibrary = outcomes.count { if case .inLibrary = $0 { true } else { false } }
        next.repeated = outcomes.count { if case .repeated = $0 { true } else { false } }
        next.unreadable = parsed.issues.count
        next.notChecked = outcomes.count { if case let .local(_, notChecked) = $0 { notChecked } else { false } }
        if next.matched + next.local == 0 {
            next.state = ImportState.failed.rawValue
            next.failure = ImportFailure.nothingToImport.rawValue
        } else {
            next.state = ImportState.ready.rawValue
        }
        // Cancelled meanwhile: leave it cancelled.
        if try await store.replaceImportJob(ifState: ImportState.analyzing.rawValue, with: next), next.state == ImportState.failed.rawValue {
            log(ImportJob(next), .nothingNew, format: parsed.format)
        }
    }

    private func importPapers(_ started: ImportJobRecord) async throws {
        guard let id = started.id, let parsed = try await parse(started), let record = try await withCollection(started) else { return }
        let keys = try await store.libraryKeys(jobID: id)
        let plan = ImportPlanner.plan(parsed.entries, existing: existingLibrary(keys, includeImported: true))
        let wanted = Self.lookups(plan)
        let matches: DOIMatches
        if let cached = lookups[id], cached.covers(wanted) {
            matches = cached
        } else {
            matches = await matcher.match(wanted)
            lookups[id] = matches
        }
        let outcomes = Self.resolve(plan, matches: matches, keys: keys)
        var takenKeys = Set(keys.compactMap(\.citeKey))
        var processed = outcomes.count { if case .alreadyImported = $0 { true } else { false } }
        let toSave = outcomes.filter {
            switch $0 {
            case .matched, .local: true
            default: false
            }
        }
        if toSave.isEmpty {
            try await store.insertImportBatch(jobID: id, papers: [], collectionID: record.collectionID, addedAt: now(), processed: processed)
        }
        var start = 0
        while start < toSave.count {
            // Cancel moves the job on; stop between batches, keeping what is saved.
            guard try await store.importJob(id: id)?.state == ImportState.importing.rawValue else { return }
            let batch = toSave[start..<min(start + Self.batchSize, toSave.count)]
            let savedAt = now()
            // Consecutive save times, so the library and the next export keep the file's order.
            let papers: [PaperWithAuthors] = batch.enumerated().map { index, outcome in
                let key = ImportCiteKeys.sanitize(outcome.entry.citeKey).map { ImportCiteKeys.resolve($0, taken: takenKeys) }
                if let key { takenKeys.insert(key) }
                let paper: Paper = if case let .matched(_, work) = outcome { work } else { outcome.entry.localPaper(uuid: newID()) }
                return paper.asRecords(localID: newID(), savedAt: savedAt + Int64(index), citeKey: key, detailsFetched: true)
            }
            processed += batch.count
            start += batch.count
            try await store.insertImportBatch(jobID: id, papers: papers, collectionID: record.collectionID, addedAt: savedAt, processed: processed)
        }
        guard var done = try await store.importJob(id: id) else { return }
        done.state = ImportState.done.rawValue
        if try await store.replaceImportJob(ifState: ImportState.importing.rawValue, with: done) {
            log(ImportJob(done), .ok, format: parsed.format)
        }
        lookups[id] = nil
        // The Share Extension's observations, and any other store, see the new papers.
        try? await store.notifyExternalChanges()
    }

    /// The parsed file, or nil after marking the job failed.
    private func parse(_ record: ImportJobRecord) async throws -> ImportParseResult? {
        guard let id = record.id else { return nil }
        let url = textURL(id)
        let text = await Task.detached { try? String(contentsOf: url, encoding: .utf8) }.value
        let parsed = await Task.detached { text.flatMap(ImportParser.parse) }.value
        guard parsed == nil else { return parsed }
        var failed = record
        failed.state = ImportState.failed.rawValue
        failed.failure = (text == nil ? ImportFailure.unreadable : .unsupported).rawValue
        if try await store.replaceImportJob(ifState: record.state, with: failed) {
            log(ImportJob(record), text == nil ? .unreadable : .unsupported, format: nil)
        }
        return nil
    }

    /// Creates the collection on the first run when asked to; nil when the job moved on meanwhile. The same name key as
    /// `GRDBCollectionsRepository`, and "name (n)" when the name is taken, as on Android.
    private func withCollection(_ record: ImportJobRecord) async throws -> ImportJobRecord? {
        guard record.addToCollection, record.collectionID == nil else { return record }
        let base = String(trimmedCollectionName(record.collectionName).prefix(collectionNameMaxLength))
        let name = base.isEmpty ? "Import" : base
        var updated = record
        updated.addToCollection = false
        for n in 1...Self.maxCollectionNameTries {
            let suffix = " (\(n))"
            let candidate = n == 1
                ? name
                : trimmedCollectionName(String(name.prefix(collectionNameMaxLength - suffix.count))) + suffix
            guard let collectionID = try await store.insertCollection(
                name: candidate, nameKey: collectionNameKey(candidate), createdAt: now()
            ) else { continue }
            updated.addToCollection = true
            updated.collectionID = collectionID
            updated.collectionName = candidate
            break
        }
        // Every name taken: import without a collection rather than not at all.
        return try await store.replaceImportJob(ifState: ImportState.importing.rawValue, with: updated) ? updated : nil
    }

    private func log(_ job: ImportJob, _ result: ImportResult, format: ImportFormat?) {
        analytics.log(importEvent(job, result, format: format))
    }

    // MARK: - Planning

    private enum Outcome {
        case matched(ImportedEntry, Paper)
        case local(ImportedEntry, notChecked: Bool)
        case inLibrary(ImportedEntry)
        case alreadyImported(ImportedEntry)
        case repeated(ImportedEntry)

        var entry: ImportedEntry {
            switch self {
            case let .matched(entry, _), let .local(entry, _), let .inLibrary(entry), let .alreadyImported(entry), let .repeated(entry):
                entry
            }
        }
    }

    /// What happens to each entry once its DOI is looked up. A match whose work is already saved is in the library; two
    /// entries matching the same work are one paper. Android's `ImportRunner.resolve`.
    private static func resolve(_ plan: [PlannedEntry], matches: DOIMatches, keys: [LibraryKeyRow]) -> [Outcome] {
        let savedWorks = Set(keys.filter { !$0.importedByJob }.compactMap(\.openAlexID))
        let importedWorks = Set(keys.filter(\.importedByJob).compactMap(\.openAlexID))
        var matchedWorks = Set<String>()
        return plan.map { planned in
            switch planned {
            case let .repeated(entry): return .repeated(entry)
            case let .inLibrary(entry): return .inLibrary(entry)
            case let .alreadyImported(entry): return .alreadyImported(entry)
            case let .toImport(entry):
                guard let doi = entry.doi, let paper = matches.found[doi] else {
                    return .local(entry, notChecked: entry.doi.map { matches.notChecked.contains($0) } ?? false)
                }
                if importedWorks.contains(paper.openAlexID) { return .alreadyImported(entry) }
                if savedWorks.contains(paper.openAlexID) { return .inLibrary(entry) }
                if !matchedWorks.insert(paper.openAlexID).inserted { return .repeated(entry) }
                return .matched(entry, paper)
            }
        }
    }

    private static func lookups(_ plan: [PlannedEntry]) -> [String] {
        plan.compactMap { planned -> String? in
            guard case let .toImport(entry) = planned else { return nil }
            return entry.doi
        }
    }

    private func existingLibrary(_ keys: [LibraryKeyRow], includeImported: Bool) -> ExistingLibrary {
        let imported = includeImported ? keys.filter(\.importedByJob) : []
        return ExistingLibrary(
            dois: Set(keys.compactMap(\.doi)),
            titleKeys: Set(keys.compactMap { titleKey($0.title, year: $0.year) }),
            importedDOIs: Set(imported.compactMap(\.doi)),
            importedTitleKeys: Set(imported.compactMap { titleKey($0.title, year: $0.year) })
        )
    }

    private func textURL(_ id: Int64) -> URL { directory.appending(path: "\(id).txt") }
}

/// The `import` event for a job that ended with `result` (spec §8.4): buckets and shares only, never names or titles.
func importEvent(_ job: ImportJob, _ result: ImportResult, format: ImportFormat?) -> AnalyticsEvent {
    .importFile(
        format: format.map { $0 == .bibTeX ? .bibtex : .ris },
        result: result,
        entries: librarySizeBucket(job.counts.found),
        matched: MatchedShare(matched: job.counts.matched, toImport: job.counts.toImport),
        toCollection: job.collectionID != nil
    )
}

extension ImportJob {
    init(_ record: ImportJobRecord) {
        self.init(
            id: record.id ?? 0,
            fileName: record.fileName,
            state: ImportState(rawValue: record.state) ?? .failed,
            counts: ImportCounts(
                found: record.found, matched: record.matched, local: record.local, inLibrary: record.inLibrary,
                repeated: record.repeated, unreadable: record.unreadable, notChecked: record.notChecked
            ),
            processed: record.processed,
            imported: record.imported,
            addToCollection: record.addToCollection,
            collectionName: record.collectionName,
            collectionID: record.collectionID,
            failure: record.failure.flatMap(ImportFailure.init(rawValue:))
        )
    }
}
