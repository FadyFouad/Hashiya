package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.CitationStyle

interface CitationRepository {
    /** One saved paper's citation in [style], refetching its details first if needed. Null if it isn't saved. */
    suspend fun entry(openAlexId: String, style: CitationStyle = CitationStyle.Bibtex): CitationResult?

    /** Every paper in [collectionId] (null = the whole library) in [style], regardless of any search or status filter. */
    suspend fun export(collectionId: Long?, style: CitationStyle = CitationStyle.Bibtex): CitationResult
}

/**
 * [text] is BibTeX or the plain citation(s); [html] is set for APA and IEEE entries, [rtf] for APA and IEEE exports.
 * [complete] is false when at least one paper's details still couldn't be fetched, so the citation may lack volume or pages.
 */
data class CitationResult(val text: String, val html: String? = null, val rtf: String? = null, val complete: Boolean)
