package com.etatech.hashiya.core.importer

/** Keeping a BibTeX entry's own key, so `\cite` commands written against the old library keep working (spec §7.3). */
object ImportCiteKeys {
    private const val UNSAFE = ",{}\"#%'()=\\"

    /** [key] when BibTeX can use it as a key, else null (the next export assigns one). */
    fun sanitize(key: String?): String? = key?.takeIf { it.isNotEmpty() && it.none { c -> isSpace(c) || c in UNSAFE } }

    /** [key] if no saved paper has it, else [key] with the first free suffix: a, b … z, aa, ab … */
    fun resolve(key: String, taken: Set<String>): String = generateSequence(0) { it + 1 }.map { key + suffix(it) }.first { it !in taken }

    /** 0 → "", 1 → "a" … 26 → "z", 27 → "aa": the same suffixes core/bibtex's CiteKeys uses. */
    private fun suffix(n: Int): String {
        val letters = StringBuilder()
        var rest = n
        while (rest > 0) {
            rest--
            letters.append('a' + rest % 26)
            rest /= 26
        }
        return letters.reverse().toString()
    }
}
