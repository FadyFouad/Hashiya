import Foundation
@testable import HashiyaImport
import Testing

/// Ported from Android's `ImportParserTest.kt`.
struct ImportParserTests {
    @Test func decodeDropsABOMAndFallsBackToWindows1252() {
        #expect(ImportParser.decode(Data([0xEF, 0xBB, 0xBF, 0x54, 0x59])) == "TY")
        #expect(ImportParser.decode(Data([0x47, 0xF6, 0x64, 0x65, 0x6C, 0x20, 0x93, 0x71, 0x94, 0x20, 0x96])) == "Gödel “q” –")
    }

    @Test func crlfAndLoneCRAreLineEnds() {
        let crlf = ImportParser.parse("TY  - JOUR\r\nTI  - One\r\nER  - \r\n\r\nTY  - JOUR\r\nTI  - Two\r\nER  - \r\n")
        #expect(crlf?.entries.map(\.line) == [1, 5])
        let cr = ImportParser.parse("@misc{a, title = {A}}\r@misc{b,\r title = {B}}")
        #expect(cr?.entries.map(\.line) == [1, 2])
    }

    @Test func theFormatComesFromTheContent() {
        #expect(ImportParser.parse("\u{FEFF}TY  - JOUR\nTI  - T\nER  - ")?.format == .ris)
        #expect(ImportParser.parse("Exported by Mendeley\n\n@article{k, title = {T}}")?.format == .bibTeX)
        #expect(ImportParser.parse("AU  - Stray, A\nER  - \nTY  - JOUR\nTI  - T\nER  - ")?.issues == [ImportIssue(line: 1, kind: .missingType)])
    }

    @Test(arguments: ["", "Dear reviewer, please email me@example.org {soon}.", "<?xml version=\"1.0\"?><records/>", "PMID- 12345\nTI  - Medline"])
    func anythingElseIsNotSupported(text: String) {
        #expect(ImportParser.parse(text) == nil)
    }
}
