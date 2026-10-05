package com.etatech.hashiya.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import com.etatech.hashiya.core.data.paging.OpenAlexPagingSource
import com.etatech.hashiya.core.data.search.PAGE_SIZE
import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.network.OpenAlexDataSource
import com.etatech.hashiya.core.network.quota.OpenAlexQuota
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class OpenAlexSearchRepository(private val dataSource: OpenAlexDataSource, private val maxPagesPerQuery: () -> Int) :
    SearchRepository {
    @Inject
    constructor(dataSource: OpenAlexDataSource, quota: OpenAlexQuota) : this(dataSource, { quota.limits.maxPagesPerQuery })

    override fun search(query: SearchQuery): SearchResults {
        val totalCount = MutableStateFlow<Long?>(null)
        val firstPage = MutableStateFlow<FirstPage?>(null)
        val pagesLoaded = MutableStateFlow(0)
        val capReached = MutableStateFlow<Int?>(null)
        val dailyLimitHit = MutableStateFlow(false)
        val pager = Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, enablePlaceholders = false),
            pagingSourceFactory = {
                OpenAlexPagingSource(
                    query,
                    dataSource,
                    onFirstPage = {
                        totalCount.value = it.total
                        firstPage.value = it
                    },
                    onPage = { pagesLoaded.value = it },
                    maxPages = maxPagesPerQuery,
                    onCapReached = { capReached.value = it },
                    onDailyLimit = { dailyLimitHit.value = true }
                )
            }
        )
        return SearchResults(
            papers = pager.flow,
            totalCount = totalCount.asStateFlow(),
            firstPage = firstPage.asStateFlow(),
            pagesLoaded = pagesLoaded.asStateFlow(),
            capReached = capReached.asStateFlow(),
            dailyLimitHit = dailyLimitHit.asStateFlow()
        )
    }
}
