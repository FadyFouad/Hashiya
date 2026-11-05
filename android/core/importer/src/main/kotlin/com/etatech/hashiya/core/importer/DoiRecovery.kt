package com.etatech.hashiya.core.importer

import com.etatech.hashiya.core.model.normalizeDoi

internal const val ARXIV_DOI_PREFIX = "10.48550/arxiv."

private val NEW_ARXIV_ID = Regex("""\d{4}\.\d{4,5}""")
private val OLD_ARXIV_ID = Regex("""[a-z]+(?:-[a-z]+)?(?:\.[a-z]{2})?/\d{7}""", RegexOption.IGNORE_CASE)
private val ARXIV_VERSION = Regex("""v\d+$""", RegexOption.IGNORE_CASE)
private val ARXIV_URL = Regex("""arxiv\.org/(?:abs|pdf)/([^?#\s]+)""", RegexOption.IGNORE_CASE)
private val SCHOLAR_ARXIV_JOURNAL = Regex("""^arxiv preprint arxiv:(\S+)""", RegexOption.IGNORE_CASE)

/** The DOI arXiv assigns to [id] ("1706.03762v5" → "10.48550/arxiv.1706.03762"), or null when [id] isn't an arXiv id. */
internal fun arxivDoi(id: String?): String? {
    if (id == null) return null
    var value = id.trim().removeSuffix(".pdf")
    if (value.startsWith("arxiv:", ignoreCase = true)) value = value.substring("arxiv:".length)
    value = value.replace(ARXIV_VERSION, "")
    val valid = NEW_ARXIV_ID.matches(value) || OLD_ARXIV_ID.matches(value)
    return if (valid) ARXIV_DOI_PREFIX + value.lowercase() else null
}

/**
 * A DOI from a field that may hold one: a bare DOI, a `doi:` value, a doi.org link (percent-decoded), or an arxiv.org link (as
 * its arXiv DOI). Null for anything else.
 */
internal fun doiFrom(value: String?): String? {
    val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    ARXIV_URL.find(text)?.let { match -> return arxivDoi(match.groupValues[1]) }
    val decoded = if (text.contains("doi.org/", ignoreCase = true)) percentDecode(text) else text
    return normalizeDoi(decoded)?.takeIf { doi -> doi.none(::isSpace) }
}

/** Google Scholar's arXiv entries: `journal = {arXiv preprint arXiv:1810.04805}`. */
internal fun scholarArxivDoi(journal: String?): String? =
    journal?.let { SCHOLAR_ARXIV_JOURNAL.find(it.trim()) }?.let { arxivDoi(it.groupValues[1]) }

internal fun isArxivDoi(doi: String?): Boolean = doi?.startsWith(ARXIV_DOI_PREFIX) == true

internal fun isScholarArxivJournal(journal: String?): Boolean = journal?.let { SCHOLAR_ARXIV_JOURNAL.containsMatchIn(it.trim()) } == true

private fun percentDecode(value: String): String {
    if ('%' !in value) return value
    val bytes = java.io.ByteArrayOutputStream()
    var i = 0
    while (i < value.length) {
        val hex = if (value[i] == '%' && i + 2 < value.length) value.substring(i + 1, i + 3).toIntOrNull(16) else null
        if (hex != null) {
            bytes.write(hex)
            i += 3
        } else {
            val end = if (Character.isHighSurrogate(value[i]) && i + 1 < value.length) i + 2 else i + 1
            bytes.write(value.substring(i, end).toByteArray(Charsets.UTF_8))
            i = end
        }
    }
    return bytes.toByteArray().toString(Charsets.UTF_8)
}
