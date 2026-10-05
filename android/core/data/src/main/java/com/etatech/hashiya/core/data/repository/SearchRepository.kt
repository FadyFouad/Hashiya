package com.etatech.hashiya.core.data.repository

import androidx.paging.PagingData
import com.etatech.hashiya.core.analytics.ResearchCategory
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.model.SearchQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface SearchRepository {
    fun search(query: SearchQuery): SearchResults
}

/** What the first page of a search says about it: OpenAlex's total and the research area of its results. */
data class FirstPage(val total: Long, val category: ResearchCategory)

/** [totalCount] is null until the first page has loaded. */
data class SearchResults(
    val papers: Flow<PagingData<Paper>>,
    val totalCount: StateFlow<Long?>,
    /** Set when the first page arrives. */
    val firstPage: StateFlow<FirstPage?> = MutableStateFlow(null),
    /** Pages fetched so far for this search: 1 after the first page. */
    val pagesLoaded: StateFlow<Int> = MutableStateFlow(0),
    /** The number of results shown when the page cap stopped the search; null while paging can go on or has ended. */
    val capReached: StateFlow<Int?> = MutableStateFlow(null)
)

/** Carried inside Paging's `LoadState.Error` so the UI can show the right message. */
class SearchException(val error: SearchError) : Exception(error.toString())
