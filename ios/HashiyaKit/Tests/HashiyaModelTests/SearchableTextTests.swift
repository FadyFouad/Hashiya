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

    /// Ported from Android's `SearchableTextTest.kt`.
    @Test(arguments: [
        ("Schrödinger", "schrodinger"),
        ("schrodinger", "schrodinger"),
        ("SCHRÖDINGER", "schrodinger"),
        ("Schro\u{0308}dinger", "schrodinger"),
        ("Café Naïve", "cafe naive"),
        ("التَّعلُّم", "التعلم"),
        ("التعلم", "التعلم"),
        ("اَلتَّعَلُّمُ", "التعلم"),
        ("العـــربية", "العربية"),
        ("أحمد", "احمد"),
        ("إسلام", "اسلام"),
        ("آية", "اية"),
        ("ٱلكتاب", "الكتاب"),
        ("مستشفى", "مستشفي"),
        ("مستشفي", "مستشفي"),
        ("تعلُّم الآلة Machine LEARNING", "تعلم الالة machine learning"),
        ("١٩٨٤", "1984"),
        ("۱۹۸۴", "1984"),
        ("كوفيد-١٩", "كوفيد-19"),
        ("", ""),
        ("-- !? ()", "-- !? ()"),
        // Lowercasing never depends on the device's locale: on a Turkish phone "I" still becomes "i".
        ("TITLE", "title"),
    ])
    func searchableTextFoldsCaseAccentsMarksLetterFormsAndDigits(input: String, expected: String) {
        #expect(searchableText(input) == expected)
    }
}
