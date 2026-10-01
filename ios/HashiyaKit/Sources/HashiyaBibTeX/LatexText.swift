import Foundation

// Android's core/bibtex works on UTF-16 chars and regular expressions. This port works on Unicode scalars and uses no
// regular expressions (Android's `(?U)\s+` crashed on device; engines differ), so the output matches byte for byte.

/// Unicode `White_Space`: exactly what Android's `[\s\p{Z}\u0085]` matches, including NEL, the line separator,
/// no-break spaces and the ideographic space.
func isBibWhitespace(_ scalar: Unicode.Scalar) -> Bool {
    scalar.properties.isWhitespace
}

/// What Kotlin's `Char.isWhitespace` accepts, which is what `String.trim()` removes: the Z categories plus U+0009–U+000D
/// and U+001C–U+001F. Not NEL (U+0085), unlike `isBibWhitespace`.
private func isKotlinWhitespace(_ scalar: Unicode.Scalar) -> Bool {
    switch scalar.value {
    case 0x09...0x0D, 0x1C...0x1F:
        return true
    default:
        switch scalar.properties.generalCategory {
        case .spaceSeparator, .lineSeparator, .paragraphSeparator: return true
        default: return false
        }
    }
}

func string<S: Sequence<Unicode.Scalar>>(_ scalars: S) -> String {
    var view = String.UnicodeScalarView()
    view.append(contentsOf: scalars)
    return String(view)
}

/// Kotlin's `String.trim()`.
func kotlinTrim(_ text: String) -> String {
    let scalars = Array(text.unicodeScalars)
    guard let start = scalars.firstIndex(where: { !isKotlinWhitespace($0) }),
          let end = scalars.lastIndex(where: { !isKotlinWhitespace($0) }) else { return "" }
    return string(scalars[start...end])
}

/// Kotlin's `split(WHITESPACE)` with `WHITESPACE` a run of `isBibWhitespace`: empty pieces at either end are kept.
func splitOnWhitespace(_ text: String) -> [String] {
    var pieces: [String] = []
    var current = String.UnicodeScalarView()
    var inRun = false
    for scalar in text.unicodeScalars {
        if isBibWhitespace(scalar) {
            if !inRun {
                pieces.append(String(current))
                current = String.UnicodeScalarView()
                inRun = true
            }
        } else {
            current.append(scalar)
            inRun = false
        }
    }
    pieces.append(String(current))
    return pieces
}

/// Kotlin's `split(' ')`: splits on U+0020 only and keeps empty pieces.
private func splitOnSpace(_ text: String) -> [String] {
    var pieces: [String] = []
    var current = String.UnicodeScalarView()
    for scalar in text.unicodeScalars {
        if scalar == " " {
            pieces.append(String(current))
            current = String.UnicodeScalarView()
        } else {
            current.append(scalar)
        }
    }
    pieces.append(String(current))
    return pieces
}

/// Escapes the characters LaTeX treats specially. Everything else, Arabic and accented letters included, stays UTF-8.
/// Braces become commands rather than `\{`, because BibTeX counts braces without looking at backslashes, so a lone `\{` in a
/// title would unbalance the entry. Walks scalars, so a combining mark after a backslash can't hide it.
func escapeLatex(_ text: String) -> String {
    var out = ""
    for scalar in text.unicodeScalars {
        switch scalar {
        case "\\": out += "\\textbackslash{}"
        case "&", "%", "$", "#", "_": out += "\\" + String(Character(scalar))
        case "{": out += "\\textbraceleft{}"
        case "}": out += "\\textbraceright{}"
        case "~": out += "\\textasciitilde{}"
        case "^": out += "\\textasciicircum{}"
        default: out.unicodeScalars.append(scalar)
        }
    }
    return out
}

/// Runs of whitespace (Unicode `White_Space`) become one space, then the ends are trimmed as Kotlin's `trim()` does.
func cleanWhitespace(_ text: String) -> String {
    kotlinTrim(splitOnWhitespace(text).joined(separator: " "))
}

/// Wraps words with a capital after their first character in braces, so bibliography styles keep BERT, ImageNet, iPhone.
/// A word starting with a command (`\#MeToo`) gets double braces: BibTeX treats `{\` as a special character and would
/// lowercase the rest of the group. Uppercase is the Unicode `Uppercase` property, as Kotlin's `Char.isUpperCase`.
func protectCapitals(_ text: String) -> String {
    splitOnSpace(text).map { word in
        let scalars = word.unicodeScalars
        guard scalars.dropFirst().contains(where: { $0.properties.isUppercase }) else { return word }
        return scalars.first == "\\" ? "{{\(word)}}" : "{\(word)}"
    }.joined(separator: " ")
}
