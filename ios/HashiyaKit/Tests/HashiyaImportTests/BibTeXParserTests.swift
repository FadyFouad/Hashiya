@testable import HashiyaImport
import Testing

/// Ported from Android's `BibTeXParserTest.kt`.
struct BibTeXParserTests {
    private func single(_ text: String) -> ImportedEntry? {
        let result = BibTeXParser.parse(text)
        return result.entries.count == 1 ? result.entries[0] : nil
    }

    private func authors(_ author: String) -> [String] {
        single("@misc{k, title = {T}, author = {\(author)}}")?.authors ?? []
    }

    @Test func anEntryGivesItsTypeKeyAndLine() throws {
        let entry = try #require(single("\n\n@ARTICLE{Key2020,\n  TITLE = {A title}\n}"))
        #expect(entry.line == 3)
        #expect(entry.entryType == "article")
        #expect(entry.citeKey == "Key2020")
        #expect(entry.title == "A title")
    }

    @Test func stringMacrosConcatenationAndMonths() {
        let text = """
            @string{ a = "Journal of " }
            @STRING(b = {Machine {L}earning})
            @string{ c = a # b }
            @article{k, title = "T", journal = c # " " # jan}
            """
        #expect(single(text)?.venue == "Journal of Machine Learning January")
    }

    @Test func aMissingClosingBraceIsReportedAndTheNextEntryStillParses() {
        let result = BibTeXParser.parse("@article{bad,\n  title = {Never closed,\n  year = {2019},\n\n@article{good,\n  title = {Fine}\n}\n")
        #expect(result.issues == [ImportIssue(line: 1, kind: .malformed)])
        #expect(result.entries.map(\.citeKey) == ["good"])
    }

    @Test func aSyntaxErrorIsReportedAtTheEntrysLine() {
        let result = BibTeXParser.parse("@misc{a, title = {A}}\n@article{bad, title {missing equals}}\n@misc{c, title = {C}}")
        #expect(result.issues == [ImportIssue(line: 2, kind: .malformed)])
        #expect(result.entries.map(\.citeKey) == ["a", "c"])
    }

    @Test func anEntryWithNeitherTitleNorDOIIsEmpty() {
        let result = BibTeXParser.parse("@article{k, note = {nothing}}")
        #expect(result.issues == [ImportIssue(line: 1, kind: .empty)])
        #expect(result.entries.isEmpty)
    }

    @Test func nameForms() {
        #expect(authors("LeCun, Yann and van Beethoven, Ludwig and Smith, Jr., John and Ada Lovelace")
            == ["Yann LeCun", "Ludwig van Beethoven", "John Smith Jr.", "Ada Lovelace"])
        #expect(authors("{Barnes and Noble, Inc.} and Doe, Jane") == ["Barnes and Noble, Inc.", "Jane Doe"])
        #expect(authors("Anderson, A AND Band, B") == ["A Anderson", "B Band"])
        #expect(authors("Vaswani, Ashish and others") == ["Ashish Vaswani"])
        #expect(authors("family=Linde, given=Anja, prefix=von der, useprefix=true") == ["Anja von der Linde"])
        #expect(authors("G{\\\"o}del, Kurt and Erd\\H{o}s, Paul") == ["Kurt Gödel", "Paul Erdős"])
    }

    @Test func editorsStandInWhenThereAreNoAuthors() {
        #expect(single("@book{k, title = {T}, editor = {Berg, Jan}}")?.authors == ["Jan Berg"])
    }

    @Test func doiOrder() {
        #expect(single("@article{k, doi = {10.1/A}, eprint = {1706.03762}, archiveprefix = {arXiv}}")?.doi == "10.1/a")
        #expect(single("@misc{k, title = {T}, eprint = {1706.03762v5}, eprinttype = {arxiv}}")?.doi == "10.48550/arxiv.1706.03762")
        #expect(single("@article{k, title = {T}, arxivId = {1810.04805}}")?.doi == "10.48550/arxiv.1810.04805")
        #expect(single("@article{k, title = {T}, url = {https://doi.org/10.1093/X}}")?.doi == "10.1093/x")
        #expect(single("@article{k, title = {T}, journal = {arXiv preprint arXiv:1810.04805}}")?.doi == "10.48550/arxiv.1810.04805")
    }

    @Test(arguments: [
        ("article", "article", "journal"), ("inproceedings", "article", "conference"), ("book", "book", nil),
        ("incollection", "book-chapter", nil), ("phdthesis", "dissertation", nil), ("techreport", "report", nil),
        ("misc", nil, nil),
    ] as [(String, String?, String?)])
    func entryTypes(type: String, workType: String?, sourceType: String?) {
        let entry = single("@\(type){k, title = {T}}")
        #expect(entry?.workType == workType)
        #expect(entry?.sourceType == sourceType)
    }
}
