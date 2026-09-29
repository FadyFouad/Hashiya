/// The text without Arabic diacritics and tatweel; everything else as typed.
public func withoutArabicMarks(_ text: String) -> String {
    // Scalars, not Characters: a mark combines with its letter into one Character.
    String(String.UnicodeScalarView(text.unicodeScalars.filter { !isArabicMark($0.value) }))
}

/// Lowercased text with accents and marks removed, Arabic letter variants unified and digits in ASCII, for full-text search.
/// The library's search index and the queries typed into it both pass through here, so they always agree.
public func searchableText(_ text: String) -> String {
    var folded = String.UnicodeScalarView()
    // NFKD first: "ö" becomes "o" + U+0308 and "أ" becomes "ا" + U+0654, so the marks can be dropped on their own.
    for scalar in text.decomposedStringWithCompatibilityMapping.unicodeScalars {
        switch scalar.properties.generalCategory {
        case .nonspacingMark, .spacingMark, .enclosingMark:
            continue
        default:
            break
        }
        switch scalar.value {
        case 0x0640:
            // Tatweel.
            continue
        case 0x0623, 0x0625, 0x0622, 0x0671:
            // أ إ آ ٱ → ا
            folded.append("\u{0627}")
        case 0x0649:
            // ى → ي
            folded.append("\u{064A}")
        default:
            folded.append(asciiDigit(scalar) ?? scalar)
        }
    }
    // `lowercased()` uses Unicode's default mapping, never the device's locale (a Turkish phone still gives "title").
    return String(folded).lowercased()
}

/// The ASCII digit of a decimal digit (Arabic-Indic "١", Persian "۱", …); nil for anything else.
private func asciiDigit(_ scalar: Unicode.Scalar) -> Unicode.Scalar? {
    guard scalar.properties.numericType == .decimal, let value = scalar.properties.numericValue else { return nil }
    return Unicode.Scalar(UInt8(ascii: "0") + UInt8(value))
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
