package com.etatech.hashiya.core.bibtex

private val WHITESPACE = Regex("""\s+""")

/** Escapes the characters LaTeX treats specially. Everything else, Arabic and accented letters included, stays UTF-8. */
internal fun escapeLatex(text: String): String = buildString {
    text.forEach { c ->
        append(
            when (c) {
                '\\' -> "\\textbackslash{}"
                '&', '%', '$', '#', '_', '{', '}' -> "\\$c"
                '~' -> "\\textasciitilde{}"
                '^' -> "\\textasciicircum{}"
                else -> c
            }
        )
    }
}

internal fun cleanWhitespace(text: String): String = text.trim().replace(WHITESPACE, " ")

/** Wraps words with a capital after their first character in braces, so bibliography styles keep BERT, ImageNet, iPhone. */
internal fun protectCapitals(text: String): String =
    text.split(' ').joinToString(" ") { word -> if (word.drop(1).any { it.isUpperCase() }) "{$word}" else word }
