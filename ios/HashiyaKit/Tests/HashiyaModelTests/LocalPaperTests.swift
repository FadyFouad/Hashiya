import HashiyaModel
import Testing

struct LocalPaperTests {
    @Test func onlyImportedPapersHaveLocalIDs() {
        #expect(isLocalPaperID("local-1b4e28ba-2fa1-11d2-883f-0016d3cca427"))
        #expect(!isLocalPaperID("W2741809807"))
        #expect(!isLocalPaperID("Wlocal-1"))
    }
}
