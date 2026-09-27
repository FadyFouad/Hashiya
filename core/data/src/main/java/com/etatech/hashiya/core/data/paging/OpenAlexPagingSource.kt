package com.etatech.hashiya.core.data.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.etatech.hashiya.core.data.mapping.asPaper
import com.etatech.hashiya.core.data.repository.SearchException
import com.etatech.hashiya.core.data.search.FIRST_CURSOR
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
    private val onTotalCount: (Long) -> Unit
) : PagingSource<String, Paper>() {
    private val seenIds = mutableSetOf<String>()

    override suspend fun load(params: LoadParams<String>): LoadResult<String, Paper> = try {
        val response = dataSource.searchWorks(query.toWorksSearchRequest(params.key ?: FIRST_CURSOR))
        if (params.key == null) onTotalCount(response.meta.count)
        LoadResult.Page(
            data = response.results.map { it.asPaper() }.filter { seenIds.add(it.openAlexId) },
            prevKey = null,
            nextKey = response.meta.nextCursor?.takeIf { response.results.isNotEmpty() }
        )
    } catch (e: NetworkException) {
        LoadResult.Error(SearchException(e.failure.asSearchError()))
    }

    /** Cursors cannot be resumed mid-list, so a refresh always starts from the first page. */
    override fun getRefreshKey(state: PagingState<String, Paper>): String? = null
}
