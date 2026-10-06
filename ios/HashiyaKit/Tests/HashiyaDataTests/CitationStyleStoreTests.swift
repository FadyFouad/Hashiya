import Foundation
import HashiyaData
import HashiyaModel
import HashiyaTesting
import Testing

struct CitationStyleStoreTests {
    @Test func defaultsToAPA() {
        #expect(CitationStyleStore(defaults: TestDefaults.make()).style == .apa)
    }

    @Test func aChosenStyleIsRemembered() {
        let defaults = TestDefaults.make()
        CitationStyleStore(defaults: defaults).set(.ieee)
        #expect(CitationStyleStore(defaults: defaults).style == .ieee)
    }

    @Test func anUnknownStoredStyleReadsAsAPA() {
        let defaults = TestDefaults.make()
        defaults.set("harvard", forKey: "citationStyle")
        #expect(CitationStyleStore(defaults: defaults).style == .apa)
    }
}
