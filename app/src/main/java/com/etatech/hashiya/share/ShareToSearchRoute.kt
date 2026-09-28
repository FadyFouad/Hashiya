package com.etatech.hashiya.share

import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.extractPaperIdentifier
import com.etatech.hashiya.feature.search.SearchNote
import com.etatech.hashiya.feature.search.navigation.SearchRoute

/**
 * Where a share from another app should land: a lookup when the shared text holds a DOI or arXiv ID,
 * otherwise a keyword search for the page title, otherwise an empty Search with an explanation.
 */
internal fun shareToSearchRoute(text: String?, subject: String?): SearchRoute {
    val title = subject?.trim()?.takeIf { it.isNotEmpty() }
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
