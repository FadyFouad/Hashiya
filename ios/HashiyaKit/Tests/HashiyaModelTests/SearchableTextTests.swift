import HashiyaModel
import Testing

struct SearchableTextTests {
    @Test(arguments: [
        ("التَّعلُّم", "التعلم"),
        ("التّعلمُ", "التعلم"),
        ("ـالتعلمـ", "التعلم"),
        ("Schrödinger", "Schrödinger"),
        ("أإآ", "أإآ"),
        ("Deep Learning", "Deep Learning"),
        ("١٩", "١٩"),
        ("", ""),
    ])
    func withoutArabicMarksDropsOnlyTashkeelAndTatweel(input: String, expected: String) {
        #expect(withoutArabicMarks(input) == expected)
    }
}
