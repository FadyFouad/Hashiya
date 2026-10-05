package com.etatech.hashiya.core.data.repository

import androidx.paging.testing.asSnapshot
import com.etatech.hashiya.core.analytics.ResearchCategory
import com.etatech.hashiya.core.data.FakeOpenAlexDataSource
import com.etatech.hashiya.core.model.SearchQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAlexSearchRepositoryTest {
    private val dataSource = FakeOpenAlexDataSource()
    private var maxPages = 8
    private val repository = OpenAlexSearchRepository(dataSource) { maxPages }

    @Test
    fun loadsFirstPageAndExposesTotalCount() = runTest {
        dataSource.enqueuePage("W1", "W2", nextCursor = null, count = 2)
        val results = repository.search(SearchQuery("bert"))

        assertNull(results.totalCount.value)
        assertNull(results.firstPage.value)
        assertEquals(0, results.pagesLoaded.value)
        assertEquals(listOf("W1", "W2"), results.papers.asSnapshot().map { it.openAlexId })
        assertEquals(2L, results.totalCount.value)
        assertEquals(FirstPage(2, ResearchCategory.Unknown), results.firstPage.value)
        assertEquals(1, results.pagesLoaded.value)
        assertNull(results.capReached.value)
    }

    @Test
    fun exposesTheResultCountWhenTheCapStopsPaging() = runTest {
        maxPages = 1
        dataSource.enqueuePage("W1", nextCursor = "c2", count = 500)
        val results = repository.search(SearchQuery("bert"))

        assertEquals(listOf("W1"), results.papers.asSnapshot().map { it.openAlexId })
        assertEquals(25, results.capReached.value)
        assertEquals(1, dataSource.requests.size)
    }
}
