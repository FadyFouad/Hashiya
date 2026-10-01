@testable import HashiyaBibTeX
import Testing

/// Mirrors Android's core/bibtex LatexTextTest, case for case, plus the Kotlin edges the port reproduces.
struct LatexTextTests {
    @Test func escapesEverySpecialCharacter() {
        #expect(escapeLatex("R&D 50% $5 #1 a_b {x") == ##"R\&D 50\% \$5 \#1 a\_b \textbraceleft{}x"##)
        #expect(escapeLatex("}") == #"\textbraceright{}"#)
        #expect(escapeLatex("a~b^c") == #"a\textasciitilde{}b\textasciicircum{}c"#)
        #expect(escapeLatex(#"C:\dir"#) == #"C:\textbackslash{}dir"#)
        #expect(escapeLatex(#"\&"#) == #"\textbackslash{}\&"#)
    }

    @Test func keepsUnicode() {
        #expect(escapeLatex("Jörg Müller · تعلم") == "Jörg Müller · تعلم")
    }

    @Test func collapsesWhitespace() {
        #expect(cleanWhitespace("  Deep\nlearning \t for   graphs ") == "Deep learning for graphs")
        #expect(cleanWhitespace("\u{0085}Deep\u{2028}learning\u{00A0}for\u{3000}graphs ") == "Deep learning for graphs")
    }

    @Test func protectsWordsWithInnerCapitals() {
        #expect(protectCapitals("BERT: Pre-training of Deep Models") == "{BERT:} Pre-training of Deep Models")
        #expect(protectCapitals("ImageNet and COVID-19 on an iPhone") == "{ImageNet} and {COVID-19} on an {iPhone}")
        #expect(protectCapitals("The deep A") == "The deep A")
        #expect(protectCapitals("تعلم GPU") == "تعلم {GPU}")
        #expect(protectCapitals(#"(BERT) and R\&D"#) == #"{(BERT)} and {R\&D}"#)
        #expect(protectCapitals(escapeLatex("#MeToo era")) == ##"{{\#MeToo}} era"##)
    }

    // Swift-only: the edges of Kotlin's trim() and split(Regex) that the port reproduces (spec §6.2).

    @Test func whitespaceIsUnicodeWhiteSpace() {
        for scalar: Unicode.Scalar in ["\t", "\n", "\u{0B}", "\u{0C}", "\r", " ", "\u{0085}", "\u{00A0}", "\u{1680}", "\u{2000}", "\u{200A}", "\u{2028}", "\u{2029}", "\u{202F}", "\u{205F}", "\u{3000}"] {
            #expect(isBibWhitespace(scalar), "U+\(String(scalar.value, radix: 16))")
        }
        // Zero-width space and the BOM are format characters, not whitespace, on Android too.
        #expect(!isBibWhitespace("\u{200B}"))
        #expect(!isBibWhitespace("\u{FEFF}"))
        #expect(cleanWhitespace("a\u{200B}b") == "a\u{200B}b")
    }

    @Test func trimMatchesKotlin() {
        // Kotlin's trim() removes U+001C–U+001F but not NEL (U+0085).
        #expect(kotlinTrim("\u{001C} x \u{001F}") == "x")
        #expect(kotlinTrim("\u{0085}x\u{0085}") == "\u{0085}x\u{0085}")
        #expect(kotlinTrim("\u{00A0}\u{3000}x\u{2028}") == "x")
        #expect(kotlinTrim("   ") == "")
        #expect(kotlinTrim("") == "")
    }

    @Test func splitKeepsLeadingAndTrailingEmptyPieces() {
        #expect(splitOnWhitespace("a  b") == ["a", "b"])
        #expect(splitOnWhitespace(" a b") == ["", "a", "b"])
        #expect(splitOnWhitespace("a b\u{0085}") == ["a", "b", ""])
        #expect(splitOnWhitespace("") == [""])
    }

    @Test func escapingWorksOnScalarsSoACombiningMarkCantHideABackslash() {
        // "\" + U+0301 is one Swift Character; Android escapes the backslash and keeps the mark.
        #expect(escapeLatex("\\\u{0301}") == "\\textbackslash{}\u{0301}")
    }

    // Swift-only.
    @Test func capitalsOutsideTheBMPAreNotProtected() {
        // Android checks UTF-16 chars, so a surrogate half is never uppercase.
        #expect(protectCapitals("a\u{1D400}") == "a\u{1D400}")
    }
}
