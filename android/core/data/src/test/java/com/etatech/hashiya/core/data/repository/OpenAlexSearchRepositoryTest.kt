package com.etatech.hashiya.core.data.repository

import androidx.paging.testing.asSnapshot
import com.etatech.hashiya.core.analytics.ResearchCategory
import com.etatech.hashiya.core.analytics.SearchRoute
import com.etatech.hashiya.core.data.FakeOpenAlexDataSource
import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.model.RequestRoute
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun theResponseRouteReachesTheFirstPage() = runTest {
        dataSource.enqueuePage("W1", nextCursor = null, route = RequestRoute.Keyless)
        val results = repository.search(SearchQuery("bert"))

        results.papers.asSnapshot()

        assertEquals(SearchRoute.Keyless, results.firstPage.value?.route)
    }

    @Test
    fun aDailyLimitPageSetsDailyLimitHit() = runTest {
        dataSource.enqueueFailure(NetworkFailure.DailyLimit(1_000))
        val results = repository.search(SearchQuery("bert"))
        assertFalse(results.dailyLimitHit.value)

        runCatching { results.papers.asSnapshot() }

        assertTrue(results.dailyLimitHit.value)
    }

    @Test
    fun aNewPagingSourceClearsTheCapOfTheLastOne() = runTest {
        maxPages = 1
        dataSource.enqueuePage("W1", nextCursor = "c2", count = 500)
        val results = repository.search(SearchQuery("bert"))
        results.papers.asSnapshot()
        assertEquals(25, results.capReached.value)

        // A fresh collection builds a new paging source; this refresh ends before the cap.
        dataSource.enqueuePage("W1", nextCursor = null, count = 1)
        results.papers.asSnapshot()

        assertNull(results.capReached.value)
    }
}
