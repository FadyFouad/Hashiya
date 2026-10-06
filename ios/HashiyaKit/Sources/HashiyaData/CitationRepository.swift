import Foundation
import HashiyaBibTeX
import HashiyaCitation
import HashiyaDatabase
import HashiyaModel
import HashiyaNetwork

/// `text` is the plain citation or reference list (BibTeX for `.bibtex`); `html` and `rtf` are the rich forms of an APA or IEEE
/// entry and list respectively. `complete` is false when at least one paper's details still couldn't be fetched, so its
/// citation may lack volume or pages.
public struct CitationResult: Equatable, Sendable {
    public var text: String
    public var html: String?
    public var rtf: String?
    public var complete: Bool

    public init(text: String, html: String? = nil, rtf: String? = nil, complete: Bool) {
        self.text = text
        self.html = html
        self.rtf = rtf
        self.complete = complete
    }
}

public protocol CitationRepository: Sendable {
    /// One saved paper's citation in `style`, refetching its details first if needed. Nil if it isn't saved.
    func entry(openAlexID: String, style: CitationStyle) async throws -> CitationResult?
    /// Every paper in `collectionID` (nil = the whole library), regardless of any search or status filter, in `style`.
    func export(collectionID: Int64?, style: CitationStyle) async throws -> CitationResult
}

extension CitationRepository {
    public func entry(openAlexID: String) async throws -> CitationResult? {
        try await entry(openAlexID: openAlexID, style: .bibtex)
    }

    public func export(collectionID: Int64?) async throws -> CitationResult {
        try await export(collectionID: collectionID, style: .bibtex)
    }
}

/// Refetches the details papers saved before v4 lack, once each; assigns cite keys once; then builds the citation text.
public struct GRDBCitationRepository: CitationRepository {
    private let store: PaperStore
    private let lookup: any OpenAlexLookupService
    private let maxConcurrentRefetches: Int

    public init(store: PaperStore, lookup: any OpenAlexLookupService, maxConcurrentRefetches: Int = 4) {
        self.store = store
        self.lookup = lookup
        self.maxConcurrentRefetches = maxConcurrentRefetches
    }

    public func entry(openAlexID: String, style: CitationStyle) async throws -> CitationResult? {
        guard let stored = try await store.citablePaper(openAlexID: openAlexID) else { return nil }
        try await refetch([stored])
        try await assignMissingKeys()
        // Nil when the paper was removed while its details were being fetched.
        guard let row = try await store.citablePaper(openAlexID: openAlexID) else { return nil }
        switch style {
        case .bibtex:
            guard let citable = row.citable else { return nil }
            return CitationResult(text: BibTeX.entry(citable), complete: row.hasDetails)
        case .apa, .ieee:
            let citation = style == .apa ? APA.format(row.asPaper()) : IEEE.format(row.asPaper())
            return CitationResult(text: citation.plain, html: Rendering.html(citation), complete: row.hasDetails)
        }
    }

    public func export(collectionID: Int64?, style: CitationStyle) async throws -> CitationResult {
        try await refetch(store.citablePapers(collectionID: collectionID))
        try await assignMissingKeys()
        // Read again: papers removed meanwhile drop out. One saved after the keys were assigned has none yet and is left out too.
        let rows = try await store.citablePapers(collectionID: collectionID).filter { $0.paper.citeKey != nil }
        let complete = rows.allSatisfy(\.hasDetails)
        switch style {
        case .bibtex:
            return CitationResult(text: BibTeX.file(rows.compactMap(\.citable)), complete: complete)
        case .apa, .ieee:
            let papers = rows.map { $0.asPaper() }
            let list = style == .apa ? APA.list(papers) : IEEE.list(papers)
            return CitationResult(
                text: list.map(\.plain).joined(separator: "\n\n"),
                rtf: Rendering.rtf(list, hangingIndent: style == .apa),
                complete: complete
            )
        }
    }

    /// Fetches the details papers saved before v4 lack, at most `maxConcurrentRefetches` at a time.
    private func refetch(_ rows: [PaperWithAuthors]) async throws {
        let missing = rows.filter { !$0.hasDetails }
        guard !missing.isEmpty else { return }
        try await withThrowingTaskGroup(of: Void.self) { group in
            var pending = missing.makeIterator()
            for _ in 0..<min(maxConcurrentRefetches, missing.count) {
                guard let row = pending.next() else { break }
                group.addTask { try await refetchOne(row) }
            }
            while try await group.next() != nil {
                if let row = pending.next() {
                    group.addTask { try await refetchOne(row) }
                }
            }
        }
    }

    /// A failed request leaves details_fetched at 0, so the next export or copy asks again. Cancellation is rethrown.
    private func refetchOne(_ row: PaperWithAuthors) async throws {
        guard let openAlexID = row.paper.openAlexID else { return }
        let work: NetworkWork?
        do {
            work = try await lookup.work(id: openAlexID)
        } catch is NetworkFailure {
            return
        }
        // Both updates match no row if the paper was removed meanwhile.
        if let work {
            try await store.updatePublicationDetails(paperID: row.paper.id, details: work.asPublicationDetails())
        } else {
            try await store.markDetailsFetched(paperID: row.paper.id)
        }
    }

    /// Gives every keyless saved paper a key, oldest saved first, in one transaction. Keys are assigned across the whole library,
    /// not just the exported papers, so a key never depends on which collection was exported first. A clash with a key another
    /// export stored at the same moment retries once against the fresh set.
    private func assignMissingKeys() async throws {
        for attempt in 0..<2 {
            let keyless = try await store.papersWithoutCiteKeys()
            guard !keyless.isEmpty else { return }
            let keys = CiteKeys.assign(keyless.map { $0.asPaper() }, taken: try await store.allCiteKeys())
            do {
                try await store.assignCiteKeys(Dictionary(uniqueKeysWithValues: zip(keyless.map(\.paper.id), keys)))
                return
            } catch let clash as CiteKeyTakenError {
                if attempt == 1 { throw clash }
            }
        }
    }
}

extension PaperWithAuthors {
    fileprivate var citable: CitablePaper? {
        paper.citeKey.map { CitablePaper(paper: asPaper(), citeKey: $0) }
    }

    /// Whether the stored details are as complete as they will get. A paper with no OpenAlex ID has nothing to refetch.
    fileprivate var hasDetails: Bool {
        paper.detailsFetched || paper.openAlexID == nil
    }
}

/// For launches with no network (UI tests): every work is unknown, so papers are marked fetched and never asked for again.
struct OfflineLookupService: OpenAlexLookupService {
    func work(id: String) async throws -> NetworkWork? { nil }
    func works(filter: String, perPage: Int) async throws -> NetworkWorksResponse { throw NetworkFailure.connectivity }
}
