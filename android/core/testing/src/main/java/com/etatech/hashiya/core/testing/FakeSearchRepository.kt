package com.etatech.hashiya.core.testing

import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import com.etatech.hashiya.core.data.repository.FirstPage
import com.etatech.hashiya.core.data.repository.SearchException
import com.etatech.hashiya.core.data.repository.SearchRepository
import com.etatech.hashiya.core.data.repository.SearchResults
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.model.SearchQuery
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

class FakeSearchRepository : SearchRepository {
    val queries = mutableListOf<SearchQuery>()
    var papers: List<Paper> = emptyList()
    var totalCount: Long? = null
    var error: SearchError? = null
    var firstPage: FirstPage? = null
    var pagesLoaded: Int = 0
    var capReached: Int? = null

    /** The flows handed out by the latest [search], so a test can emit later values. */
    var lastFirstPage = MutableStateFlow<FirstPage?>(null)
        private set
    var lastPagesLoaded = MutableStateFlow(0)
        private set
    var lastCapReached = MutableStateFlow<Int?>(null)
        private set

    override fun search(query: SearchQuery): SearchResults {
        queries += query
        val currentError = error
        val data = if (currentError != null) {
            PagingData.empty<Paper>(
                LoadStates(
                    refresh = LoadState.Error(SearchException(currentError)),
                    prepend = LoadState.NotLoading(endOfPaginationReached = true),
                    append = LoadState.NotLoading(endOfPaginationReached = true)
                )
            )
        } else {
            // Explicit LoadStates (rather than the no-args overload) so Paging dispatches a real
            // "not loading" state; without it, androidx.paging.testing's asSnapshot() never sees the
            // load settle and hangs.
            PagingData.from(
                papers,
                sourceLoadStates = LoadStates(
                    refresh = LoadState.NotLoading(endOfPaginationReached = false),
                    prepend = LoadState.NotLoading(endOfPaginationReached = true),
                    append = LoadState.NotLoading(endOfPaginationReached = true)
                )
            )
        }
        lastFirstPage = MutableStateFlow(firstPage)
        lastPagesLoaded = MutableStateFlow(pagesLoaded)
        lastCapReached = MutableStateFlow(capReached)
        return SearchResults(
            papers = flowOf(data),
            totalCount = MutableStateFlow(totalCount),
            firstPage = lastFirstPage,
            pagesLoaded = lastPagesLoaded,
            capReached = lastCapReached
        )
    }
}
