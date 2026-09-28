package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.data.lookup.ARXIV_DOI_PREFIX
import com.etatech.hashiya.core.data.lookup.arxivLandingPageFilter
import com.etatech.hashiya.core.data.lookup.titlesMatch
import com.etatech.hashiya.core.data.mapping.asPaper
import com.etatech.hashiya.core.data.search.asSearchError
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.network.ArxivDataSource
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import com.etatech.hashiya.core.network.model.NetworkWork
import javax.inject.Inject

/**
 * DOIs resolve directly. arXiv IDs try the arXiv DOI first (trusted), then OpenAlex's landing-page filter, whose
 * single match is accepted only if its title matches arXiv's title — OpenAlex sometimes attaches the wrong work.
 */
internal class OpenAlexPaperLookupRepository @Inject constructor(
    private val openAlex: OpenAlexLookupDataSource,
    private val arxiv: ArxivDataSource
) : PaperLookupRepository {
    override suspend fun lookup(identifier: PaperIdentifier): LookupResult = try {
        when (identifier) {
            is PaperIdentifier.Doi -> openAlex.getWork("doi:${identifier.value}").toLookupResult()
            is PaperIdentifier.Arxiv -> lookupArxiv(identifier.id)
        }
    } catch (e: NetworkException) {
        LookupResult.Failed(e.failure.asSearchError())
    }

    private suspend fun lookupArxiv(id: String): LookupResult {
        openAlex.getWork("doi:$ARXIV_DOI_PREFIX$id")?.let { return LookupResult.Found(it.asPaper()) }
        val matches = openAlex.findWorks(arxivLandingPageFilter(id), perPage = 2).results.distinctBy { it.id }
        if (matches.size != 1) return LookupResult.NotFound(arxivTitleOrNull(id))
        val arxivTitle = try {
            arxiv.title(id)
        } catch (e: NetworkException) {
            // OpenAlex just answered, so any arXiv trouble is the service being unavailable, not the user offline.
            return LookupResult.Failed(SearchError.ServiceUnavailable)
        } ?: return LookupResult.NotFound(arxivTitle = null)
        val paper = matches.single().asPaper()
        return if (titlesMatch(paper.title, arxivTitle)) LookupResult.Found(paper) else LookupResult.NotFound(arxivTitle)
    }

    /** Only labels the "Search for …" button, so a failure here is not an error. */
    private suspend fun arxivTitleOrNull(id: String): String? = try {
        arxiv.title(id)
    } catch (e: NetworkException) {
        null
    }

    private fun NetworkWork?.toLookupResult(): LookupResult =
        this?.let { LookupResult.Found(it.asPaper()) } ?: LookupResult.NotFound(arxivTitle = null)
}
