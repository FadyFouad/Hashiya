package com.etatech.hashiya.core.data.lookup

internal const val ARXIV_DOI_PREFIX = "10.48550/arXiv."

/**
 * OpenAlex filter matching any landing page OpenAlex stores for an arXiv paper: the abs page over http or https,
 * with no version or v1–v5, and the arXiv DOI page. `|` means "any of" and keeps this a single request.
 */
internal fun arxivLandingPageFilter(id: String): String {
    val versions = listOf("") + (1..5).map { "v$it" }
    val absPages = listOf("http", "https").flatMap { scheme -> versions.map { version -> "$scheme://arxiv.org/abs/$id$version" } }
    return "locations.landing_page_url:" + (absPages + "https://doi.org/10.48550/arxiv.$id").joinToString("|")
}

private val NON_ALPHANUMERIC = Regex("""[^\p{L}\p{N}]+""")

internal fun normalizedTitle(title: String): String = title.lowercase().replace(NON_ALPHANUMERIC, " ").trim()

/** True when both titles have the same letters and digits in the same order, ignoring case and punctuation. */
internal fun titlesMatch(a: String, b: String): Boolean {
    val left = normalizedTitle(a)
    return left.isNotEmpty() && left == normalizedTitle(b)
}
