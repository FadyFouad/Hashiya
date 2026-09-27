package com.etatech.hashiya.core.data.repository

import androidx.paging.PagingData
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.model.SearchQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface SearchRepository {
    fun search(query: SearchQuery): SearchResults
}

/** [totalCount] is null until the first page has loaded. */
data class SearchResults(val papers: Flow<PagingData<Paper>>, val totalCount: StateFlow<Long?>)

/** Carried inside Paging's `LoadState.Error` so the UI can show the right message. */
class SearchException(val error: SearchError) : Exception(error.toString())
