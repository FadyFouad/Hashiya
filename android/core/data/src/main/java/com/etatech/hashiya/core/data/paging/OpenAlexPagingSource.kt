package com.etatech.hashiya.core.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.etatech.hashiya.core.analytics.ResearchCategory
import com.etatech.hashiya.core.analytics.TopicIds
import com.etatech.hashiya.core.data.mapping.asPaper
import com.etatech.hashiya.core.data.repository.FirstPage
import com.etatech.hashiya.core.data.repository.SearchException
import com.etatech.hashiya.core.data.search.FIRST_CURSOR
import com.etatech.hashiya.core.data.search.PAGE_SIZE
import com.etatech.hashiya.core.data.search.asSearchError
import com.etatech.hashiya.core.data.search.toWorksSearchRequest
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.OpenAlexDataSource

/** Cursor-paged OpenAlex search. Drops works already returned on an earlier page. */
internal class OpenAlexPagingSource(
    private val query: SearchQuery,
    private val dataSource: OpenAlexDataSource,
    private val onFirstPage: (FirstPage) -> Unit,
    private val onPage: (Int) -> Unit,
    /** Pages one search may load; read as each page arrives, so a config change applies to the next page. */
    private val maxPages: () -> Int = { Int.MAX_VALUE },
    /** Called with the number of results a search may show when the cap stopped it. */
    private val onCapReached: (Int) -> Unit = {}
) : PagingSource<String, Paper>() {
    private val seenIds = mutableSetOf<String>()
    private var pages = 0

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Paper> = try {
        val response = dataSource.searchWorks(query.toWorksSearchRequest(params.key ?: FIRST_CURSOR))
        pages += 1
        onPage(pages)
        if (params.key == null) {
            val topics = response.results.map { work ->
                TopicIds(work.primaryTopic?.subfield?.id, work.primaryTopic?.field?.id, work.primaryTopic?.domain?.id)
            }
            onFirstPage(FirstPage(response.meta.count, ResearchCategory.classify(topics)))
        }
        val next = response.meta.nextCursor?.takeIf { response.results.isNotEmpty() }
        // Pages fetched count, not pages shown: a page made only of works already seen still used a request.
        val cap = maxPages()
        val capped = next != null && pages >= cap
        if (capped) onCapReached(cap * PAGE_SIZE)
        LoadResult.Page(
            data = response.results.map { it.asPaper() }.filter { seenIds.add(it.openAlexId) },
            prevKey = null,
            nextKey = next.takeUnless { capped }
        )
    } catch (e: NetworkException) {
        LoadResult.Error(SearchException(e.failure.asSearchError()))
    }

    /** Cursors cannot be resumed mid-list, so a refresh always starts from the first page. */
    override fun getRefreshKey(state: PagingState<String, Paper>): String? = null
}
