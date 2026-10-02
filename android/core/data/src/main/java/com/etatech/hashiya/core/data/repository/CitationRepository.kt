package com.etatech.hashiya.core.data.repository

interface CitationRepository {
    /** One saved paper's BibTeX entry, refetching its details first if needed. Null if it isn't saved. */
    suspend fun entry(openAlexId: String): CitationResult?

    /** Every paper in [collectionId] (null = the whole library), regardless of any search or status filter. */
    suspend fun export(collectionId: Long?): CitationResult
}

/** [complete] is false when at least one paper's details still couldn't be fetched, so the entry may lack volume or pages. */
data class CitationResult(val bibtex: String, val complete: Boolean)
