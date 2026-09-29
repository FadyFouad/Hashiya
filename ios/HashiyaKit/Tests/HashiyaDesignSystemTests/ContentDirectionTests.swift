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
}
