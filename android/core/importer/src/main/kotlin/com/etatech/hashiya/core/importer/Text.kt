package com.etatech.hashiya.core.importer

/**
 * The whitespace the import rules know: a fixed set, so Kotlin's and Swift's different Unicode tables can't make the platforms
 * disagree.
 */
private const val SPACES = " \t\n\r\u000B\u000C "

internal fun isSpace(c: Char): Boolean = c in SPACES

/** Runs of whitespace become one space and the ends are trimmed; null when nothing is left. */
internal fun collapse(text: String?): String? {
    if (text == null) return null
    val out = StringBuilder(text.length)
    var pendingSpace = false
    for (c in text) {
        if (isSpace(c)) {
            pendingSpace = out.isNotEmpty()
        } else {
            if (pendingSpace) out.append(' ')
            pendingSpace = false
            out.append(c)
        }
    }
    return out.toString().ifEmpty { null }
}

/** The first run of exactly four ASCII digits, as a year: "2015/05//" → 2015. */
internal fun firstYear(text: String?): Int? {
    if (text == null) return null
    var i = 0
    while (i < text.length) {
        if (text[i] in '0'..'9') {
            val start = i
            while (i < text.length && text[i] in '0'..'9') i++
            if (i - start == 4) return text.substring(start, i).toInt()
        } else {
            i++
        }
    }
    return null
}

/** "770--778" → ("770", "778"): split on the first run of hyphens or en dashes; one value fills the first page only. */
internal fun splitPages(text: String?): Pair<String?, String?> {
    val value = collapse(text) ?: return null to null
    val start = value.indexOfFirst { it == '-' || it == '–' }
    if (start < 0) return value to null
    var end = start
    while (end < value.length && (value[end] == '-' || value[end] == '–')) end++
    return collapse(value.substring(0, start)) to collapse(value.substring(end))
}

/** For a DOI or URL: braces go and the escapes `\_ \% \& \#` give their character. Nothing else is decoded. */
internal fun cleanRaw(text: String): String {
    val out = StringBuilder(text.length)
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            c == '{' || c == '}' -> i++

            c == '\\' && i + 1 < text.length && text[i + 1] in "_%&#" -> {
                out.append(text[i + 1])
                i += 2
            }

            else -> {
                out.append(c)
                i++
            }
        }
    }
    return out.toString().trim()
}
