import HashiyaModel

/// The FTS MATCH expression for what the user typed, or nil when no word is left ("no search").
/// Each word becomes a quoted prefix term and every word must match: "Deep lear" → `"deep*" "lear*"`.
/// Only letters and numbers survive, so FTS syntax (quotes, `*`, `-`, parentheses, `column:`, `^`) never reaches
/// MATCH and `AND`/`OR`/`NOT`/`NEAR` are ordinary words. FTS4 reads a prefix only inside the quotes (`"transf*"`);
/// `"transf"*` would match the exact word.
func ftsMatch(_ query: String) -> String? {
    let words = searchableText(query).unicodeScalars
        .split { !isWordScalar($0) }
        .map { String(Substring($0)) }
    guard !words.isEmpty else { return nil }
    return words.map { "\"\($0)*\"" }.joined(separator: " ")
}

/// Letters (Lu, Ll, Lt, Lm, Lo) and numbers (Nd, Nl, No).
private func isWordScalar(_ scalar: Unicode.Scalar) -> Bool {
    switch scalar.properties.generalCategory {
    case .uppercaseLetter, .lowercaseLetter, .titlecaseLetter, .modifierLetter, .otherLetter,
         .decimalNumber, .letterNumber, .otherNumber:
        true
    default:
        false
    }
}
