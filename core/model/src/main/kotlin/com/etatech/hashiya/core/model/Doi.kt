package com.etatech.hashiya.core.model

private val DOI_PREFIXES = listOf(
    "https://doi.org/",
    "http://doi.org/",
    "https://dx.doi.org/",
    "http://dx.doi.org/",
    "doi:"
)

/** Returns the DOI in canonical form (`10.xxxx/yyy`, lowercase, no URL prefix), or null if [raw] is not a DOI. */
fun normalizeDoi(raw: String): String? {
    var value = raw.trim().lowercase()
    DOI_PREFIXES.firstOrNull { value.startsWith(it) }?.let { prefix ->
        value = value.removePrefix(prefix).trim()
    }
    return value.takeIf { it.startsWith("10.") && it.contains('/') }
}
