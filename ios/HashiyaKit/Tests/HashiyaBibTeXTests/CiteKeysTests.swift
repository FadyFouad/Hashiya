@testable import HashiyaBibTeX
import HashiyaModel
import Testing

/// Mirrors Android's core/bibtex CiteKeysTest, case for case.
struct CiteKeysTests {
    private func paper(_ title: String, _ authors: String..., year: Int? = 2017) -> Paper {
        Paper(openAlexID: "W1", title: title, authors: authors.map { Author(name: $0) }, year: year)
    }

    @Test func surnameYearAndFirstMeaningfulTitleWord() {
        #expect(CiteKeys.base(paper("Attention Is All You Need", "Ashish Vaswani", "Noam Shazeer")) == "vaswani2017attention")
    }

    @Test func stopWordsAreSkipped() {
        #expect(CiteKeys.base(paper("On the Deep Nature of Things", "Jane Smith", year: 2020)) == "smith2020deep")
        #expect(CiteKeys.base(paper("Towards Using: Learning", "Jane Smith", year: 2020)) == "smith2020learning")
    }

    @Test func accentsAndSpecialLettersFoldToAscii() {
        #expect(CiteKeys.base(paper("Über Netze", "Jörg Müller", year: 2019)) == "muller2019uber")
        #expect(CiteKeys.base(paper("Große Modelle", "Anna Straße", year: 2019)) == "strasse2019grosse")
        #expect(CiteKeys.base(paper("Æon", "Jan Łukasz", year: 2019)) == "lukasz2019aeon")
    }

    @Test func punctuationInsideWordsIsDropped() {
        #expect(
            CiteKeys.base(paper("BERT: Pre-training of Deep Bidirectional Transformers", "Jacob Devlin", year: 2019))
                == "devlin2019bert"
        )
        #expect(CiteKeys.base(paper("Self-Attention", "Mary O'Neill", year: 2021)) == "oneill2021selfattention")
    }

    @Test func missingYearIsNd() {
        #expect(CiteKeys.base(paper("Graphs", "Jane Smith", year: nil)) == "smithndgraphs")
    }

    @Test func nonLatinOrMissingAuthorStartsWithPaper() {
        #expect(CiteKeys.base(paper("تطبيقات التعلم العميق", "محمد علي", year: 2019)) == "paper2019")
        #expect(CiteKeys.base(paper("Deep nets", "محمد علي", year: 2019)) == "paper2019deep")
        #expect(CiteKeys.base(paper("Deep nets", year: 2019)) == "paper2019deep")
        #expect(CiteKeys.base(paper("", year: nil)) == "papernd")
    }

    @Test func keyNeverStartsWithADigit() {
        #expect(CiteKeys.base(paper("Nets", "Group 7", year: 2020)) == "paper2020nets")
        #expect(CiteKeys.base(paper("Nets", "Team 3b", year: 2020)) == "b2020nets")
    }

    @Test func ligaturesAndCapitalSharpSFold() {
        #expect(CiteKeys.base(paper("Eﬃcient Nets", "Anna STRAẞE", year: 2020)) == "strasse2020efficient")
    }

    @Test func suffixesRunAToZThenAa() {
        #expect(keySuffix(0) == "")
        #expect(keySuffix(1) == "a")
        #expect(keySuffix(26) == "z")
        #expect(keySuffix(27) == "aa")
        #expect(keySuffix(28) == "ab")
    }

    @Test func assignAvoidsTakenKeysAndEachOther() {
        let same = paper("Deep nets", "Jane Smith", year: 2020)
        #expect(
            CiteKeys.assign([same, same, paper("Attention", "Ashish Vaswani")], taken: ["smith2020deep"])
                == ["smith2020deepa", "smith2020deepb", "vaswani2017attention"]
        )
    }

    @Test func assignRunsPastZ() {
        let same = paper("Deep", "Jane Smith", year: 2020)
        let taken = Set((0...26).map { "smith2020deep" + keySuffix($0) })
        #expect(CiteKeys.assign([same], taken: taken) == ["smith2020deepaa"])
    }

    // Swift-only: Unicode cases that differ between Swift Characters and Kotlin chars (spec §6.2).

    @Test func decomposedAccentsFoldLikePrecomposedOnes() {
        // "Mu\u{0308}ller" is one Character per letter in Swift but two scalars for the ü; both fold to "muller".
        #expect(CiteKeys.base(paper("Graphs", "Jörg Mu\u{0308}ller", year: 2019)) == "muller2019graphs")
    }

    @Test func unicodeSpacesSplitNamesAndTitles() {
        #expect(CiteKeys.base(paper("Deep\u{3000}learning", "José\u{00A0}Müller", year: 2015)) == "muller2015deep")
    }
}
