package com.etatech.hashiya.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.etatech.hashiya.core.data.paging.OpenAlexPagingSource
import com.etatech.hashiya.core.data.search.PAGE_SIZE
import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.network.OpenAlexDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class OpenAlexSearchRepository @Inject constructor(private val dataSource: OpenAlexDataSource) : SearchRepository {
    override fun search(query: SearchQuery): SearchResults {
        val totalCount = MutableStateFlow<Long?>(null)
        val pager = Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = { OpenAlexPagingSource(query, dataSource) { totalCount.value = it } }
        )
        return SearchResults(papers = pager.flow, totalCount = totalCount.asStateFlow())
    }
}
