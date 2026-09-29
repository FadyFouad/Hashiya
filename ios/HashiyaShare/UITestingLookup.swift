#if DEBUG
import HashiyaData
import HashiyaModel

/// The UI tests' lookup (Debug only): arXiv 1706.03762 is "Attention Is All You Need", anything else is not found.
/// Its own copy of the sample paper: the extension never links HashiyaTesting.
struct UITestingLookup: PaperLookupRepository {
    static let attention = Paper(
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
    )

    func lookup(_ identifier: PaperIdentifier) async -> LookupResult {
        identifier == .arxiv("1706.03762") ? .found(Self.attention) : .notFound(arxivTitle: nil)
    }
}
#endif
