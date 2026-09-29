@testable import HashiyaDesignSystem
import Testing

struct LanguageTests {
    @Test(arguments: [
        (["ar"], "ar"),
        (["ar-EG"], "ar"),
        (["ar_EG"], "ar"),
        (["en"], "en"),
        (["fr"], "en"),
        ([String](), "en"),
    ])
    func resolvesTheAppLanguageToArabicOrEnglish(preferred: [String], expected: String) {
        #expect(HashiyaLanguage.resolvedCode(preferredLocalizations: preferred) == expected)
    }
}
