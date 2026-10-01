import Foundation
import HashiyaModel

/// Google Scholar–style cite keys: surname, year, first meaningful title word, e.g. "vaswani2017attention".
public enum CiteKeys {
    private static let stopWords: Set<String> = [
        "a", "an", "the", "on", "of", "in", "for", "and", "to", "with", "from", "by", "via", "is", "are", "towards", "toward",
        "using", "at",
    ]

    /// The key before collision suffixes. Always starts with a letter: "paper" stands in for a surname with no Latin letters,
    /// and leading digits are dropped from the surname ("Group 7" has no surname letters, so it becomes "paper").
    public static func base(_ paper: Paper) -> String {
        let lastName = paper.authors.first.flatMap { splitOnWhitespace(kotlinTrim($0.name)).last }.map(asciiFold) ?? ""
        let surname = String(lastName.drop { $0 >= "0" && $0 <= "9" })
        let year = paper.year.map { String($0) } ?? "nd"
        let word = splitOnWhitespace(kotlinTrim(paper.title)).map(asciiFold).first { !$0.isEmpty && !stopWords.contains($0) } ?? ""
        return (surname.isEmpty ? "paper" : surname) + year + word
    }

    /// Keys for `papers`, in order: each its `base` or the base plus the first free suffix, avoiding `taken` and each other.
    public static func assign(_ papers: [Paper], taken: Set<String>) -> [String] {
        var used = taken
        return papers.map { paper in
            let base = base(paper)
            var n = 0
            while used.contains(base + keySuffix(n)) { n += 1 }
            let key = base + keySuffix(n)
            used.insert(key)
            return key
        }
    }
}

private let specialLetters: [Unicode.Scalar: String] = ["ß": "ss", "æ": "ae", "ø": "o", "đ": "d", "ł": "l", "ı": "i", "œ": "oe"]
private let asciiLetters: ClosedRange<Unicode.Scalar> = "a"..."z"
private let asciiDigits: ClosedRange<Unicode.Scalar> = "0"..."9"

/// Lowercase ASCII letters and digits only: accents dropped, a few letters spelled out, everything else (Arabic too) removed.
/// NFKD also splits ligatures ("ﬃ" → "ffi") and full-width letters. Works on scalars: "é" is one Character but two scalars
/// after NFKD, and only the "e" is kept.
func asciiFold(_ text: String) -> String {
    var spelled = String.UnicodeScalarView()
    for scalar in text.lowercased().unicodeScalars {
        if let letters = specialLetters[scalar] {
            spelled.append(contentsOf: letters.unicodeScalars)
        } else {
            spelled.append(scalar)
        }
    }
    let decomposed = String(spelled).decomposedStringWithCompatibilityMapping.unicodeScalars
    return string(decomposed.filter { asciiLetters.contains($0) || asciiDigits.contains($0) })
}

/// 0 → "", 1 → "a" … 26 → "z", 27 → "aa", 28 → "ab" …
func keySuffix(_ n: Int) -> String {
    var letters: [Unicode.Scalar] = []
    var rest = n
    while rest > 0 {
        rest -= 1
        letters.append(Unicode.Scalar(UInt8(97 + rest % 26)))
        rest /= 26
    }
    return string(letters.reversed())
}
