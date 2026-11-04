import Foundation

/// Turns the LaTeX in a BibTeX value into plain text (spec §4.2). Whitespace is left for `collapse`. Android's
/// `LatexDecoder`, scalar for scalar.
enum LatexDecoder {
    /// Combining marks for the accent commands, placed after the accented character.
    private static let accents: [Unicode.Scalar: Unicode.Scalar] = [
        "'": "\u{0301}", "`": "\u{0300}", "^": "\u{0302}", "\"": "\u{0308}", "~": "\u{0303}", "=": "\u{0304}",
        ".": "\u{0307}", "u": "\u{0306}", "v": "\u{030C}", "H": "\u{030B}", "c": "\u{0327}", "k": "\u{0328}",
        "r": "\u{030A}", "d": "\u{0323}", "b": "\u{0331}", "t": "\u{0361}",
    ]

    /// Control words that stand for text.
    private static let words: [String: String] = [
        "ss": "ß", "aa": "å", "AA": "Å", "ae": "æ", "AE": "Æ", "oe": "œ", "OE": "Œ",
        "o": "ø", "O": "Ø", "l": "ł", "L": "Ł", "i": "ı", "j": "ȷ",
        "textendash": "–", "textemdash": "—", "textquoteleft": "‘", "textquoteright": "’",
        "textquotedblleft": "“", "textquotedblright": "”", "ldots": "…", "dots": "…", "textellipsis": "…",
        "textasciitilde": "~", "textasciicircum": "^", "textbackslash": "\\", "textunderscore": "_",
        "textbraceleft": "{", "textbraceright": "}", "S": "§", "P": "¶", "copyright": "©",
        "textregistered": "®", "texttrademark": "™", "dag": "†", "LaTeX": "LaTeX", "TeX": "TeX",
        "alpha": "α", "beta": "β", "gamma": "γ", "delta": "δ", "epsilon": "ε", "varepsilon": "ε", "zeta": "ζ",
        "eta": "η", "theta": "θ", "iota": "ι", "kappa": "κ", "lambda": "λ", "mu": "μ", "nu": "ν", "xi": "ξ",
        "pi": "π", "rho": "ρ", "sigma": "σ", "tau": "τ", "upsilon": "υ", "phi": "φ", "varphi": "φ", "chi": "χ",
        "psi": "ψ", "omega": "ω", "Gamma": "Γ", "Delta": "Δ", "Theta": "Θ", "Lambda": "Λ", "Xi": "Ξ", "Pi": "Π",
        "Sigma": "Σ", "Upsilon": "Υ", "Phi": "Φ", "Psi": "Ψ", "Omega": "Ω",
    ]

    static func decode(_ raw: String) -> String {
        let scalars = Array(raw.unicodeScalars)
        var out = String.UnicodeScalarView()
        decodeRange(scalars, 0, scalars.count, &out)
        return String(out).precomposedStringWithCanonicalMapping
    }

    private static func starts(_ s: [Unicode.Scalar], _ i: Int, _ end: Int, with text: String) -> Bool {
        let needle = Array(text.unicodeScalars)
        guard i + needle.count <= end else { return false }
        return Array(s[i..<(i + needle.count)]) == needle
    }

    private static func decodeRange(_ s: [Unicode.Scalar], _ start: Int, _ end: Int, _ out: inout String.UnicodeScalarView) {
        var i = start
        while i < end {
            let c = s[i]
            if c == "\\" {
                i = command(s, i, end, &out)
            } else if c == "{" || c == "}" || c == "$" {
                i += 1
            } else if c == "~" {
                out.append(" ")
                i += 1
            } else if starts(s, i, end, with: "---") {
                out.append("—")
                i += 3
            } else if starts(s, i, end, with: "--") {
                out.append("–")
                i += 2
            } else if starts(s, i, end, with: "``") {
                out.append("“")
                i += 2
            } else if starts(s, i, end, with: "''") {
                out.append("”")
                i += 2
            } else {
                out.append(c)
                i += 1
            }
        }
    }

    /// Decodes the command at `at` (a backslash) and returns the index after it.
    private static func command(_ s: [Unicode.Scalar], _ at: Int, _ end: Int, _ out: inout String.UnicodeScalarView) -> Int {
        let next = at + 1
        guard next < end else { return end }
        let n = s[next]
        if !isASCIILetter(n) {
            if let mark = accents[n] { return accent(s, next + 1, end, mark, &out) }
            switch n {
            case "-", "/", "@": break
            case " ", "\\", ",": out.append(" ")
            default: out.append(n)
            }
            return next + 1
        }
        var j = next
        while j < end, isASCIILetter(s[j]) { j += 1 }
        let name = string(s[next..<j])
        if name.unicodeScalars.count == 1, let mark = accents[n] { return accent(s, j, end, mark, &out) }
        if let text = words[name] {
            out.append(contentsOf: text.unicodeScalars)
            return swallowAfterWord(s, j, end)
        }
        if name == "href" { return skipGroup(s, skipSpaces(s, j, end), end) }
        // Any other command: drop its name, keep what follows (its braced argument is decoded as ordinary text).
        return swallowAfterWord(s, j, end)
    }

    /// A control word swallows one following space, or an empty group right after it.
    private static func swallowAfterWord(_ s: [Unicode.Scalar], _ at: Int, _ end: Int) -> Int {
        if at + 1 < end, s[at] == "{", s[at + 1] == "}" { return at + 2 }
        if at < end, s[at] == " " { return at + 1 }
        return at
    }

    private static func accent(
        _ s: [Unicode.Scalar],
        _ at: Int,
        _ end: Int,
        _ mark: Unicode.Scalar,
        _ out: inout String.UnicodeScalarView
    ) -> Int {
        let (arg, after) = argument(s, at, end)
        if let first = arg.first {
            out.append(first)
            out.append(mark)
            out.append(contentsOf: arg.dropFirst())
        }
        return after
    }

    /// An accent's argument: a braced group or one character, with `\i` and `\j` as plain i and j.
    private static func argument(_ s: [Unicode.Scalar], _ at: Int, _ end: Int) -> ([Unicode.Scalar], Int) {
        let i = skipSpaces(s, at, end)
        guard i < end else { return ([], end) }
        if s[i] == "{" {
            let close = matchingBrace(s, i, end)
            let inner = Array(string(s[(i + 1)..<close]).trimmingCharacters(in: .whitespacesAndNewlines).unicodeScalars)
            if let dotless = dotless(inner) { return (dotless, min(close + 1, end)) }
            var text = String.UnicodeScalarView()
            decodeRange(inner, 0, inner.count, &text)
            return (Array(text), min(close + 1, end))
        }
        if s[i] == "\\" {
            var j = i + 1
            while j < end, isASCIILetter(s[j]) { j += 1 }
            if let dotless = dotless(Array(s[i..<j])) { return (dotless, j) }
            var text = String.UnicodeScalarView()
            let after = command(s, i, end, &text)
            return (Array(text), after)
        }
        return ([s[i]], i + 1)
    }

    private static func dotless(_ text: [Unicode.Scalar]) -> [Unicode.Scalar]? {
        switch string(text[...]) {
        case "\\i": ["i"]
        case "\\j": ["j"]
        default: nil
        }
    }

    private static func skipSpaces(_ s: [Unicode.Scalar], _ at: Int, _ end: Int) -> Int {
        var i = at
        while i < end, s[i] == " " || s[i] == "\t" { i += 1 }
        return i
    }

    private static func skipGroup(_ s: [Unicode.Scalar], _ at: Int, _ end: Int) -> Int {
        guard at < end, s[at] == "{" else { return at }
        return min(matchingBrace(s, at, end) + 1, end)
    }

    /// The index of the brace closing the one at `open`, or `end` when it never closes.
    private static func matchingBrace(_ s: [Unicode.Scalar], _ open: Int, _ end: Int) -> Int {
        var depth = 0
        for i in open..<end {
            if s[i] == "{" {
                depth += 1
            } else if s[i] == "}" {
                depth -= 1
                if depth == 0 { return i }
            }
        }
        return end
    }
}
