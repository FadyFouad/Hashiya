@testable import HashiyaImport
import Testing

/// Ported from Android's `LatexDecoderTest.kt`.
struct LatexDecoderTests {
    @Test(arguments: [
        ("\\'e", "é"), ("\\'{e}", "é"), ("{\\'e}", "é"), ("\\`e", "è"), ("\\^e", "ê"), ("{\\\"u}", "ü"), ("\\~n", "ñ"),
        ("\\=a", "ā"), ("\\.z", "ż"), ("\\u{a}", "ă"), ("\\v{s}", "š"), ("{\\v r}", "ř"), ("\\H{o}", "ő"), ("\\c{c}", "ç"),
        ("\\c c", "ç"), ("\\k{a}", "ą"), ("\\r{a}", "å"), ("\\d{a}", "ạ"), ("\\b{a}", "a\u{0331}"), ("\\t{oo}", "o\u{0361}o"),
        ("\\\"{\\i}", "ï"), ("{\\'\\i}", "í"), ("\\^{\\j}", "ĵ"),
    ])
    func accents(raw: String, expected: String) {
        #expect(LatexDecoder.decode(raw) == expected)
    }

    @Test func theOutputIsNFC() {
        #expect(LatexDecoder.decode("\\'e").unicodeScalars.map(\.value) == [0xE9])
    }

    @Test(arguments: [
        ("Stra{\\ss}e", "Straße"), ("Stra\\ss e", "Straße"), ("Stra\\ss{}e", "Straße"),
        ("{\\aa}{\\AA}{\\ae}{\\AE}{\\oe}{\\OE}{\\o}{\\O}{\\l}{\\L}{\\i}{\\j}", "åÅæÆœŒøØłŁıȷ"),
        ("\\& \\% \\$ \\# \\_ \\{ \\}", "& % $ # _ { }"),
        ("a\\ b\\\\c", "a b c"),
        ("hy\\-phen\\/ation\\@", "hyphenation"),
        ("a\\,b", "a b"),
        ("$\\alpha$-Stability", "α-Stability"),
        ("\\Gamma\\Delta\\Omega", "ΓΔΩ"),
        ("the \\emph{Approach}", "the Approach"),
        ("\\url{https://x.org}", "https://x.org"),
        ("\\RL{نص}", "نص"),
        ("\\href{https://x.org}{text}", "text"),
        ("Deep {{Residual Learning}}", "Deep Residual Learning"),
        ("$O(n^2)$", "O(n^2)"),
        ("A.~Turing", "A. Turing"),
        ("a --- b -- c - d", "a — b – c - d"),
        ("``quoted''", "“quoted”"),
        ("تحليل المشاعر باستخدام {BERT}", "تحليل المشاعر باستخدام BERT"),
        ("a\\", "a"),
        ("\\'{e", "é"),
        ("\\'", ""),
    ])
    func text(raw: String, expected: String) {
        #expect(LatexDecoder.decode(raw) == expected)
    }
}
