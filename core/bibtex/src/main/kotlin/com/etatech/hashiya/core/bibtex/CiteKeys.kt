package com.etatech.hashiya.core.bibtex

import com.etatech.hashiya.core.model.Paper
import java.text.Normalizer
import java.util.Locale

/** Google Scholar–style cite keys: surname, year, first meaningful title word, e.g. "vaswani2017attention". */
object CiteKeys {
    private val STOP_WORDS = setOf(
        "a", "an", "the", "on", "of", "in", "for", "and", "to", "with", "from", "by", "via", "is", "are", "towards", "toward",
        "using", "at"
    )
    private val WHITESPACE = Regex("""\s+""")

    /** The key before collision suffixes. Always starts with a letter: "paper" stands in for a surname with no Latin letters. */
    fun base(paper: Paper): String {
        val surname = paper.authors.firstOrNull()?.name?.trim()?.split(WHITESPACE)?.lastOrNull()?.let(::asciiFold).orEmpty()
        val year = paper.year?.toString() ?: "nd"
        val word = paper.title.trim().split(WHITESPACE).map(::asciiFold).firstOrNull { it.isNotEmpty() && it !in STOP_WORDS }.orEmpty()
        return surname.ifEmpty { "paper" } + year + word
    }

    /** Keys for [papers], in order: each its [base] or the base plus the first free suffix, avoiding [taken] and each other. */
    fun assign(papers: List<Paper>, taken: Set<String>): List<String> {
        val used = taken.toMutableSet()
        return papers.map { paper ->
            val base = base(paper)
            val key = generateSequence(0) { it + 1 }.map { base + keySuffix(it) }.first { it !in used }
            used += key
            key
        }
    }
}

private val SPECIAL_LETTERS = mapOf(
    'ß' to "ss", 'æ' to "ae", 'Æ' to "ae", 'ø' to "o", 'Ø' to "o", 'đ' to "d", 'Đ' to "d", 'ł' to "l", 'Ł' to "l", 'ı' to "i",
    'œ' to "oe", 'Œ' to "oe"
)

/** Lowercase ASCII letters and digits only: accents dropped, a few letters spelled out, everything else (Arabic too) removed. */
internal fun asciiFold(text: String): String {
    val spelled = buildString { text.forEach { c -> append(SPECIAL_LETTERS[c] ?: c) } }
    return Normalizer.normalize(spelled, Normalizer.Form.NFD).lowercase(Locale.ROOT).filter { it in 'a'..'z' || it in '0'..'9' }
}

/** 0 → "", 1 → "a" … 26 → "z", 27 → "aa", 28 → "ab" … */
internal fun keySuffix(n: Int): String {
    val letters = StringBuilder()
    var rest = n
    while (rest > 0) {
        rest--
        letters.append('a' + rest % 26)
        rest /= 26
    }
    return letters.reverse().toString()
}
