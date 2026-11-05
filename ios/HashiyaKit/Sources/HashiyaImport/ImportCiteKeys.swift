/// Keeping a BibTeX entry's own key, so `\cite` commands written against the old library keep working (spec §7.3).
public enum ImportCiteKeys {
    private static let unsafe = Set(",{}\"#%'()=\\".unicodeScalars)

    /// `key` when BibTeX can use it as a key, else nil (the next export assigns one).
    public static func sanitize(_ key: String?) -> String? {
        guard let key, !key.isEmpty, !key.unicodeScalars.contains(where: { isSpace($0) || unsafe.contains($0) }) else { return nil }
        return key
    }

    /// `key` if no saved paper has it, else `key` with the first free suffix: a, b … z, aa, ab …
    public static func resolve(_ key: String, taken: Set<String>) -> String {
        var n = 0
        while taken.contains(key + suffix(n)) { n += 1 }
        return key + suffix(n)
    }

    /// 0 → "", 1 → "a" … 26 → "z", 27 → "aa".
    private static func suffix(_ n: Int) -> String {
        var letters: [Character] = []
        var rest = n
        while rest > 0 {
            rest -= 1
            letters.append(Character(Unicode.Scalar(UInt8(97 + rest % 26))))
            rest /= 26
        }
        return String(letters.reversed())
    }
}
