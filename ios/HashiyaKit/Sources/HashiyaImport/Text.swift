import Foundation

/// The whitespace the import rules know: a fixed set, so Swift's and Kotlin's Unicode tables can't make the platforms
/// disagree.
private let spaces: Set<Unicode.Scalar> = [" ", "\t", "\n", "\r", "\u{0B}", "\u{0C}", "\u{A0}"]

func isSpace(_ scalar: Unicode.Scalar) -> Bool {
    spaces.contains(scalar)
}

/// Runs of whitespace become one space and the ends are trimmed; nil when nothing is left.
func collapse(_ text: String?) -> String? {
    guard let text else { return nil }
    var out = String.UnicodeScalarView()
    var pendingSpace = false
    for scalar in text.unicodeScalars {
        if isSpace(scalar) {
            pendingSpace = !out.isEmpty
        } else {
            if pendingSpace { out.append(" ") }
            pendingSpace = false
            out.append(scalar)
        }
    }
    return out.isEmpty ? nil : String(out)
}

private func isASCIIDigit(_ scalar: Unicode.Scalar) -> Bool {
    scalar.value >= 0x30 && scalar.value <= 0x39
}

/// The first run of exactly four ASCII digits, as a year: "2015/05//" → 2015.
func firstYear(_ text: String?) -> Int? {
    guard let text else { return nil }
    let scalars = Array(text.unicodeScalars)
    var i = 0
    while i < scalars.count {
        if isASCIIDigit(scalars[i]) {
            let start = i
            while i < scalars.count, isASCIIDigit(scalars[i]) { i += 1 }
            if i - start == 4 {
                var view = String.UnicodeScalarView()
                view.append(contentsOf: scalars[start..<i])
                return Int(String(view))
            }
        } else {
            i += 1
        }
    }
    return nil
}

/// "770--778" → ("770", "778"): split on the first run of hyphens or en dashes; one value fills the first page only.
func splitPages(_ text: String?) -> (String?, String?) {
    guard let value = collapse(text) else { return (nil, nil) }
    let scalars = Array(value.unicodeScalars)
    let isDash: (Unicode.Scalar) -> Bool = { $0 == "-" || $0 == "\u{2013}" }
    guard let start = scalars.firstIndex(where: isDash) else { return (value, nil) }
    var end = start
    while end < scalars.count, isDash(scalars[end]) { end += 1 }
    return (collapse(string(scalars[0..<start])), collapse(string(scalars[end...])))
}

/// For a DOI or URL: braces go and the escapes `\_ \% \& \#` give their character. Nothing else is decoded.
func cleanRaw(_ text: String) -> String {
    let scalars = Array(text.unicodeScalars)
    var out = String.UnicodeScalarView()
    var i = 0
    while i < scalars.count {
        let scalar = scalars[i]
        if scalar == "{" || scalar == "}" {
            i += 1
        } else if scalar == "\\", i + 1 < scalars.count, "_%&#".unicodeScalars.contains(scalars[i + 1]) {
            out.append(scalars[i + 1])
            i += 2
        } else {
            out.append(scalar)
            i += 1
        }
    }
    return String(out).trimmingCharacters(in: .whitespacesAndNewlines)
}

func string(_ scalars: ArraySlice<Unicode.Scalar>) -> String {
    var view = String.UnicodeScalarView()
    view.append(contentsOf: scalars)
    return String(view)
}

func isASCIILetter(_ scalar: Unicode.Scalar) -> Bool {
    (scalar.value >= 0x41 && scalar.value <= 0x5A) || (scalar.value >= 0x61 && scalar.value <= 0x7A)
}

func isASCIIDigitScalar(_ scalar: Unicode.Scalar) -> Bool {
    isASCIIDigit(scalar)
}
