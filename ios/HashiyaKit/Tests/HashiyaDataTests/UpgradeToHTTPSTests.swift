import HashiyaData
import Testing

/// OpenAlex often gives `http://` PDF links; App Transport Security blocks them, and some networks intercept plain HTTP.
struct UpgradeToHTTPSTests {
    @Test func anHttpLinkBecomesHttps() {
        #expect(upgradeToHTTPS("http://arxiv.org/pdf/1612.03928") == "https://arxiv.org/pdf/1612.03928")
    }

    @Test func theSchemeIsMatchedInAnyCase() {
        #expect(upgradeToHTTPS("HTTP://jsrse.edu.iq/download/285/301") == "https://jsrse.edu.iq/download/285/301")
    }

    @Test func aPortIsKept() {
        #expect(upgradeToHTTPS("http://example.org:8080/a.pdf") == "https://example.org:8080/a.pdf")
    }

    @Test func otherLinksAreUnchanged() {
        #expect(upgradeToHTTPS("https://aclanthology.org/2023.emnlp-main.16.pdf") == "https://aclanthology.org/2023.emnlp-main.16.pdf")
        #expect(upgradeToHTTPS("ftp://example.org/a.pdf") == "ftp://example.org/a.pdf")
    }
}
