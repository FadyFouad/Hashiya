package com.etatech.hashiya.core.citation

import java.text.Normalizer
import java.util.Locale

private val DOI_PREFIX = Regex("^(https?://(dx\\.)?doi\\.org/|doi:)", RegexOption.IGNORE_CASE)
private val COMBINING_MARKS = Regex("\\p{Mn}+")

/** "10.1/x" from "10.1/x", "https://doi.org/10.1/x" or "doi:10.1/x". */
internal fun doiOf(raw: String): String = raw.trim().replace(DOI_PREFIX, "")

/** "436–444", or "12" for a single page; null when there is no first page. */
internal fun pageRange(first: String?, last: String?): String? {
    val a = first?.trim()?.ifEmpty { null } ?: return null
    val b = last?.trim()?.ifEmpty { null }
    return if (b == null || b == a) a else "$a–$b"
}

internal fun isSinglePage(first: String?, last: String?): Boolean {
    val b = last?.trim()?.ifEmpty { null }
    return b == null || b == first?.trim()
}

/** Lower case without diacritics, for sorting. */
internal fun sortKey(text: String): String =
    Normalizer.normalize(text, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)

internal fun String?.orNullIfBlank(): String? = this?.trim()?.ifEmpty { null }
