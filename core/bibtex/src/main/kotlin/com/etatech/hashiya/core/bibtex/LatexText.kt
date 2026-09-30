package com.etatech.hashiya.core.bibtex

/** Unicode-aware, so line separators, NEL and no-break spaces collapse too. */
private val WHITESPACE = Regex("""(?U)\s+""")

/**
 * Escapes the characters LaTeX treats specially. Everything else, Arabic and accented letters included, stays UTF-8.
 * Braces become commands rather than `\{`, because BibTeX counts braces without looking at backslashes, so a lone `\{` in a
 * title would unbalance the entry.
 */
internal fun escapeLatex(text: String): String = buildString {
    text.forEach { c ->
        append(
            when (c) {
                '\\' -> "\\textbackslash{}"
                '&', '%', '$', '#', '_' -> "\\$c"
                '{' -> "\\textbraceleft{}"
                '}' -> "\\textbraceright{}"
                '~' -> "\\textasciitilde{}"
                '^' -> "\\textasciicircum{}"
                else -> c
            }
        )
    }
}

internal fun cleanWhitespace(text: String): String = text.replace(WHITESPACE, " ").trim()

/**
 * Wraps words with a capital after their first character in braces, so bibliography styles keep BERT, ImageNet, iPhone.
 * A word starting with a command (`\#MeToo`) gets double braces: BibTeX treats `{\` as a special character and would
 * lowercase the rest of the group.
 */
internal fun protectCapitals(text: String): String = text.split(' ').joinToString(" ") { word ->
    when {
        word.drop(1).none { it.isUpperCase() } -> word
        word.startsWith('\\') -> "{{$word}}"
        else -> "{$word}"
    }
}
