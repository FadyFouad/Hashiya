import FeatureSearch
import Foundation
import HashiyaModel
import Testing

/// Ported from Android's `ShareToSearchRouteTest.kt`.
struct ShareLookupInputTests {
    private static let longTitle = String(repeating: "A", count: 400)

    @Test(arguments: [
        ("https://arxiv.org/abs/1706.03762", nil, "Attention Is All You Need", ShareLookupInput.lookup(.arxiv("1706.03762"))),
        ("https://doi.org/10.1038/nature14539", nil, "Deep learning | Nature", .lookup(.doi("10.1038/nature14539"))),
        ("https://dl.acm.org/doi/10.1145/3292500.3330701", nil, nil, .lookup(.doi("10.1145/3292500.3330701"))),
        (nil, "Check this out: https://arxiv.org/abs/2401.00001v2", "  ", .lookup(.arxiv("2401.00001"))),
        ("https://ieeexplore.ieee.org/document/1234567", nil, " Deep learning ", .noIdentifier(pageTitle: "Deep learning")),
        ("https://www.nature.com/articles/nature14539", nil, "Deep learning | Nature", .lookup(.doi("10.1038/nature14539"))),
        (nil, nil, "Deep learning", .noIdentifier(pageTitle: "Deep learning")),
        (nil, "just some words", nil, .nothing),
        (nil, nil, "   ", .nothing),
        ("https://ieeexplore.ieee.org/document/9999999", nil, "https://ieeexplore.ieee.org/document/9999999", .nothing),
        (nil, nil, longTitle, .noIdentifier(pageTitle: String(repeating: "A", count: 300))),
        ("https://arxiv.org/abs/1706.03762", nil, longTitle, .lookup(.arxiv("1706.03762"))),
    ] as [(String?, String?, String?, ShareLookupInput)])
    func mapsWhatWasShared(url: String?, text: String?, title: String?, expected: ShareLookupInput) {
        #expect(shareLookupInput(url: url.flatMap(URL.init(string:)), text: text, title: title) == expected)
    }
}
