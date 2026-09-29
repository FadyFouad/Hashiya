@testable import HashiyaData
import Testing

/// Ported from Android's `FtsQueryTest.kt`.
struct FtsQueryTests {
    @Test(arguments: [
        ("transf", "\"transf*\""),
        ("  Deep   LEARNING ", "\"deep*\" \"learning*\""),
        ("Schrödinger", "\"schrodinger*\""),
        ("التَّعلُّم", "\"التعلم*\""),
        ("أحمد", "\"احمد*\""),
        ("١٩", "\"19*\""),
        ("C++", "\"c*\""),
        ("\"attention", "\"attention*\""),
        ("title:deep", "\"title*\" \"deep*\""),
        ("-bert (gpt*)", "\"bert*\" \"gpt*\""),
        ("Ming-Wei", "\"ming*\" \"wei*\""),
        ("cats AND dogs", "\"cats*\" \"and*\" \"dogs*\""),
        ("OR NOT NEAR", "\"or*\" \"not*\" \"near*\""),
    ])
    func eachWordBecomesAQuotedPrefixTerm(input: String, expected: String) {
        #expect(ftsMatch(input) == expected)
    }

    @Test(arguments: ["", "   ", "\"*-():", "ـــ"])
    func blankOrPunctuationOnlyMeansNoSearch(input: String) {
        #expect(ftsMatch(input) == nil)
    }
}
