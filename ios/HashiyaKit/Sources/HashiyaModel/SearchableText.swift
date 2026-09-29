/// The text without Arabic diacritics and tatweel; everything else as typed.
public func withoutArabicMarks(_ text: String) -> String {
    // Scalars, not Characters: a mark combines with its letter into one Character.
    String(String.UnicodeScalarView(text.unicodeScalars.filter { !isArabicMark($0.value) }))
}

/// Tashkeel and Quranic marks (U+0610–U+061A, U+064B–U+065F, U+0670, U+06D6–U+06DC, U+06DF–U+06E4,
/// U+06E7–U+06E8, U+06EA–U+06ED) and tatweel (U+0640).
private func isArabicMark(_ value: UInt32) -> Bool {
    switch value {
    case 0x0610...0x061A, 0x064B...0x065F, 0x0670, 0x06D6...0x06DC, 0x06DF...0x06E4, 0x06E7...0x06E8, 0x06EA...0x06ED, 0x0640:
        true
    default:
        false
    }
}
