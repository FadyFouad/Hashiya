import HashiyaModel
import Testing

struct NormalizeDOITests {
    @Test(arguments: [
        ("https://doi.org/10.48550/arXiv.1706.03762", "10.48550/arxiv.1706.03762"),
        ("http://doi.org/10.1000/xyz", "10.1000/xyz"),
        ("https://dx.doi.org/10.1000/xyz", "10.1000/xyz"),
        ("http://dx.doi.org/10.1000/XYZ", "10.1000/xyz"),
        ("doi:10.1000/XYZ", "10.1000/xyz"),
        ("DOI: 10.1000/xyz", "10.1000/xyz"),
        ("  10.1000/xyz \n", "10.1000/xyz"),
        ("10.1000/xyz", "10.1000/xyz"),
    ])
    func normalizes(raw: String, expected: String) {
        #expect(normalizeDOI(raw) == expected)
    }

    @Test(arguments: ["", "   ", "https://example.com/paper", "10.1000", "not-a-doi", "11.1000/xyz"])
    func rejects(raw: String) {
        #expect(normalizeDOI(raw) == nil)
    }
}
