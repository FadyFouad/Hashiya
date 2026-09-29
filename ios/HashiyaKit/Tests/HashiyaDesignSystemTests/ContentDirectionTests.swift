import HashiyaDesignSystem
import SwiftUI
import Testing

struct ContentDirectionTests {
    @Test(arguments: [
        ("Attention Is All You Need", LayoutDirection.leftToRight),
        ("تطبيقات التعلم العميق", .rightToLeft),
        ("עברית", .rightToLeft),
        ("2024: تعلم الآلة", .rightToLeft),
        ("«BERT» والنماذج", .leftToRight),
        ("ﻻ", .rightToLeft),
        ("Ωmega", .leftToRight),
    ])
    func followsTheFirstStrongCharacter(text: String, expected: LayoutDirection) {
        #expect(ContentDirection.of(text) == expected)
    }

    @Test(arguments: ["", "2024", "  -- 12 ()"])
    func hasNoDirectionWithoutALetter(text: String) {
        #expect(ContentDirection.of(text) == nil)
    }

    /// Arabic wraps format arguments in FSI…PDI; the Unicode bidi algorithm skips those when it picks
    /// the paragraph direction, so the shown text starts with a mark that agrees with `of`.
    @Test(arguments: [
        ("\u{2068}Ashish Vaswani\u{2069} وآخرون · 2017 · NeurIPS", LayoutDirection.rightToLeft, LayoutDirection.leftToRight, "\u{200E}"),
        ("تطبيقات التعلم العميق", .leftToRight, .rightToLeft, "\u{200F}"),
        ("Attention Is All You Need", .rightToLeft, .leftToRight, "\u{200E}"),
        ("2024", .rightToLeft, .rightToLeft, "\u{200F}"),
        ("2024", .leftToRight, .leftToRight, "\u{200E}"),
    ] as [(String, LayoutDirection, LayoutDirection, Unicode.Scalar)])
    func paragraphsStartWithTheirDirectionMark(text: String, ui: LayoutDirection, expected: LayoutDirection, mark: Unicode.Scalar) {
        let paragraph = ContentDirection.paragraph(text, uiDirection: ui)
        #expect(paragraph.direction == expected)
        #expect(paragraph.text.unicodeScalars.first == mark)
        #expect(String(paragraph.text.unicodeScalars.dropFirst()) == text)
    }
}
