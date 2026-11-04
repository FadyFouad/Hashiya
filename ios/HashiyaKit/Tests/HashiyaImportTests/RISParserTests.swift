@testable import HashiyaImport
import Testing

/// Ported from Android's `RisParserTest.kt`.
struct RISParserTests {
    private func parse(_ lines: String...) -> ImportParseResult {
        RISParser.parse(lines.joined(separator: "\n"))
    }

    @Test func tagsWithOneSpaceAndERWithoutATrailingSpace() {
        #expect(parse("TY - BOOK", "TI - One", "ER  -", "TY  - BOOK", "TI  - Two", "ER  -").entries.map(\.title) == ["One", "Two"])
    }

    @Test func wrappedLinesContinueTheValue() {
        let entry = parse("TY  - JOUR", "TI  - T", "AB  - First line", "  second line", "third", "", "ER  - ").entries.first
        #expect(entry?.abstract == "First line second line third")
    }

    @Test func aTYOrTheEndOfTheFileClosesAnOpenRecord() {
        let result = parse("TY  - JOUR", "TI  - One", "TY  - JOUR", "TI  - Two")
        #expect(result.entries.map(\.title) == ["One", "Two"])
        #expect(result.issues.isEmpty)
    }

    @Test func strayTagsAreOneMissingTypeIssue() {
        let result = parse("TY  - JOUR", "TI  - One", "ER  - ", "AU  - Stray, A", "TI  - Stray", "ER  - ", "TY  - JOUR", "TI  - Two", "ER  - ")
        #expect(result.entries.map(\.title) == ["One", "Two"])
        #expect(result.issues == [ImportIssue(line: 4, kind: .missingType)])
    }

    @Test func authorsAndEditors() {
        let entry = parse("TY  - JOUR", "TI  - T", "AU  - LeCun, Yann", "A1  - Hinton, Geoffrey", "A2  - Editor, E", "AU  - Smith, John, Jr.", "ER  - ")
        #expect(entry.entries.first?.authors == ["Yann LeCun", "Geoffrey Hinton", "John Smith Jr."])
        #expect(parse("TY  - EDBOOK", "TI  - T", "A2  - Editor, E", "ER  - ").entries.first?.authors == ["E Editor"])
    }

    @Test func venueRules() {
        #expect(parse("TY  - JOUR", "TI  - T", "J2  - J2", "JO  - JO", "ER  - ").entries.first?.venue == "JO")
        #expect(parse("TY  - BOOK", "TI  - T", "T2  - A series", "ER  - ").entries.first?.venue == nil)
        #expect(parse("TY  - THES", "TI  - T", "PB  - Uni", "ER  - ").entries.first?.venue == "Uni")
    }

    @Test func doiOrder() {
        #expect(parse("TY  - JOUR", "TI  - T", "N1  - 10.1/n1", "UR  - https://doi.org/10.1/ur", "DO  - 10.1/DO", "ER  - ").entries.first?.doi == "10.1/do")
        #expect(parse("TY  - JOUR", "TI  - T", "M3  - PhD Thesis", "N1  - doi: 10.1/n1", "ER  - ").entries.first?.doi == "10.1/n1")
        #expect(parse("TY  - GEN", "TI  - T", "UR  - https://arxiv.org/abs/1706.03762v7", "ER  - ").entries.first?.workType == "preprint")
    }

    @Test func pagesFromARangeInSP() {
        let entry = parse("TY  - JOUR", "TI  - T", "SP  - 770-778", "ER  - ").entries.first
        #expect(entry?.firstPage == "770")
        #expect(entry?.lastPage == "778")
    }
}
