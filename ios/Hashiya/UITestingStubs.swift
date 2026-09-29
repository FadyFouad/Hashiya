#if DEBUG
import Foundation
import HashiyaData
import HashiyaModel
import os

/// Launched with `-ui-testing` (Debug only): an empty library in its own App Group file (which the Share
/// Extension also uses while stubbed), an in-memory key, a search that returns the same three papers for any
/// query and a lookup that knows arXiv 1706.03762. Nothing touches the network or the real library.
enum UITestingStubs {
    static func dependencies() -> LiveDependencies {
        LiveDependencies(
            libraryRepository: try! GRDBLibraryRepository.shared(fileName: UITestingFlags.databaseFileName, fresh: true),
            searchRepository: StubSearchRepository(),
            lookupRepository: StubPaperLookupRepository(),
            preferences: KeychainUserPreferencesRepository(keychain: InMemoryKeychain())
        )
    }

    static let papers = [
        Paper(
            openAlexID: "W2626778328",
            doi: "10.48550/arxiv.1706.03762",
            title: "Attention Is All You Need",
            authors: [Author(name: "Ashish Vaswani", openAlexID: "A5103024730"), Author(name: "Noam Shazeer", openAlexID: "A5021878400")],
            year: 2017,
            venue: "Neural Information Processing Systems",
            abstract: "The dominant sequence transduction models are based on complex recurrent or convolutional neural networks.",
            citationCount: 128_412,
            isOpenAccess: true,
            openAccessPDFURL: "https://arxiv.org/pdf/1706.03762"
        ),
        Paper(
            openAlexID: "W2896457183",
            doi: "10.18653/v1/n19-1423",
            title: "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding",
            authors: [Author(name: "Jacob Devlin"), Author(name: "Ming-Wei Chang")],
            year: 2019,
            venue: "NAACL",
            citationCount: 94_112,
            isOpenAccess: true
        ),
        Paper(
            openAlexID: "W3094502228",
            title: "An Image Is Worth 16x16 Words: Transformers for Image Recognition at Scale",
            authors: [Author(name: "Alexey Dosovitskiy"), Author(name: "Lucas Beyer")],
            year: 2021,
            venue: "ICLR",
            citationCount: 41_230
        ),
    ]
}

private struct StubSearchRepository: SearchRepository {
    func searchPage(_ query: SearchQuery, cursor: String?) async throws -> SearchPage {
        SearchPage(papers: UITestingStubs.papers, totalCount: Int64(UITestingStubs.papers.count), nextCursor: nil)
    }
}

private struct StubPaperLookupRepository: PaperLookupRepository {
    func lookup(_ identifier: PaperIdentifier) async -> LookupResult {
        identifier == .arxiv("1706.03762") ? .found(UITestingStubs.papers[0]) : .notFound(arxivTitle: nil)
    }
}

private final class InMemoryKeychain: KeychainStore {
    private let items = OSAllocatedUnfairLock<[String: String]>(initialState: [:])

    func read(service: String, account: String) throws -> String? {
        items.withLock { $0[service + "/" + account] }
    }

    func write(_ value: String, service: String, account: String) throws {
        items.withLock { $0[service + "/" + account] = value }
    }

    func delete(service: String, account: String) throws {
        _ = items.withLock { $0.removeValue(forKey: service + "/" + account) }
    }
}
#endif
