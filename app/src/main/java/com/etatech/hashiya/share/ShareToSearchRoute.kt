package com.etatech.hashiya.share

import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.extractPaperIdentifier
import com.etatech.hashiya.core.model.looksLikeLink
import com.etatech.hashiya.feature.search.SearchNote
import com.etatech.hashiya.feature.search.navigation.SearchRoute

/** A shared title beyond this length is truncated, so an oversized EXTRA_SUBJECT/EXTRA_TITLE can't bloat saved state. */
private const val MAX_SHARED_TITLE = 300

/**
 * Where a share from another app should land: a lookup when the shared text holds a DOI or arXiv ID,
 * otherwise a keyword search for the page title, otherwise an empty Search with an explanation.
 * A "title" that is only a link (browsers send the URL when a page has no title) doesn't count as one.
 */
internal fun shareToSearchRoute(text: String?, subject: String?): SearchRoute {
    val title = subject?.trim()?.take(MAX_SHARED_TITLE)?.takeIf { it.isNotEmpty() && !looksLikeLink(it) }
    val identifier = text?.let(::extractPaperIdentifier)
    return when {
        identifier != null -> SearchRoute(query = identifier.asQuery(), pageTitle = title)
        title != null -> SearchRoute(query = title, note = SearchNote.NoIdInShare.name)
        else -> SearchRoute(note = SearchNote.NothingInShare.name)
    }
}

/** Text the Search box's strict parser recognizes as the same identifier. */
private fun PaperIdentifier.asQuery(): String = when (this) {
    is PaperIdentifier.Doi -> value
    is PaperIdentifier.Arxiv -> "arXiv:$id"
}
