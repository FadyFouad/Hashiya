package com.etatech.hashiya.core.data.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource.LoadResult
import androidx.paging.testing.TestPager
import com.etatech.hashiya.core.analytics.ResearchCategory
import com.etatech.hashiya.core.data.FakeOpenAlexDataSource
import com.etatech.hashiya.core.data.repository.FirstPage
import com.etatech.hashiya.core.data.repository.SearchException
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.model.NetworkTopic
import com.etatech.hashiya.core.network.model.NetworkTopicRef
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAlexPagingSourceTest {
    private val dataSource = FakeOpenAlexDataSource()
    private var reportedFirstPage: FirstPage? = null
    private var reportedPages = 0
    private var maxPages = 8
    private var reportedCap: Int? = null
    private val pager = TestPager(
        PagingConfig(pageSize = 25, initialLoadSize = 25, enablePlaceholders = false),
        OpenAlexPagingSource(
            SearchQuery("bert"),
            dataSource,
            onFirstPage = { reportedFirstPage = it },
            onPage = { reportedPages = it },
            maxPages = { maxPages },
            onCapReached = { reportedCap = it }
        )
    )

    private fun LoadResult<String, Paper>.page() = this as LoadResult.Page<String, Paper>

    @Test
    fun firstPageUsesStartCursorAndReportsTotal() = runTest {
        dataSource.enqueuePage("W1", "W2", nextCursor = "c2", count = 48210)

        val page = pager.refresh().page()

        assertEquals("*", dataSource.requests.single().cursor)
        assertEquals(listOf("W1", "W2"), page.data.map { it.openAlexId })
        assertEquals("c2", page.nextKey)
        assertEquals(48210L, reportedFirstPage?.total)
    }

    @Test
    fun appendUsesPreviousCursor() = runTest {
        dataSource.enqueuePage("W1", nextCursor = "c2")
        dataSource.enqueuePage("W2", nextCursor = "c3")

        pager.refresh()
        val page = pager.append()!!.page()

        assertEquals("c2", dataSource.requests[1].cursor)
        assertEquals(listOf("W2"), page.data.map { it.openAlexId })
    }

    @Test
    fun endsWhenThereIsNoNextCursor() = runTest {
        dataSource.enqueuePage("W1", nextCursor = null)
        assertNull(pager.refresh().page().nextKey)
    }

    @Test
    fun endsWhenAPageIsEmpty() = runTest {
        dataSource.enqueuePage(nextCursor = "c2")
        assertNull(pager.refresh().page().nextKey)
    }

    @Test
    fun dropsWorksAlreadyReturnedOnEarlierPages() = runTest {
        dataSource.enqueuePage("W1", "W2", nextCursor = "c2")
        dataSource.enqueuePage("W2", "W3", nextCursor = null)

        pager.refresh()
        val second = pager.append()!!.page()

        assertEquals(listOf("W3"), second.data.map { it.openAlexId })
    }

    @Test
    fun networkFailureBecomesSearchError() = runTest {
        dataSource.enqueueFailure(NetworkFailure.Connectivity)

        val result = pager.refresh() as LoadResult.Error
        assertEquals(SearchError.Offline, (result.throwable as SearchException).error)
    }

    @Test
    fun theFirstPageReportsItsTotalAndCategoryAndPagesAreCounted() = runTest {
        val ai = NetworkTopic(
            subfield = NetworkTopicRef("https://openalex.org/subfields/1702"),
            field = NetworkTopicRef("https://openalex.org/fields/17"),
            domain = NetworkTopicRef("https://openalex.org/domains/3")
        )
        dataSource.enqueuePage("W1", "W2", "W3", nextCursor = "c2", count = 48_210, topics = listOf(ai, ai, ai))
        dataSource.enqueuePage("W4", nextCursor = null, count = 48_210)

        pager.refresh()
        assertEquals(FirstPage(48_210, ResearchCategory.Ai), reportedFirstPage)
        assertEquals(1, reportedPages)

        reportedFirstPage = null
        pager.append()
        assertEquals(2, reportedPages)
        assertNull(reportedFirstPage)
    }

    @Test
    fun stopsAtThePageCapEvenWhenOpenAlexHasAnotherCursor() = runTest {
        maxPages = 2
        dataSource.enqueuePage("W1", nextCursor = "c2")
        dataSource.enqueuePage("W2", nextCursor = "c3")

        assertEquals("c2", pager.refresh().page().nextKey)
        assertNull(reportedCap)
        assertNull(pager.append()!!.page().nextKey)
        assertEquals(50, reportedCap)
    }

    @Test
    fun aLastPageBeforeTheCapEndsNormally() = runTest {
        maxPages = 2
        dataSource.enqueuePage("W1", nextCursor = "c2")
        dataSource.enqueuePage("W2", nextCursor = null)

        pager.refresh()
        assertNull(pager.append()!!.page().nextKey)
        assertNull(reportedCap)
    }

    @Test
    fun aLastPageOnTheCapPageIsNotACap() = runTest {
        maxPages = 1
        dataSource.enqueuePage("W1", nextCursor = null)

        assertNull(pager.refresh().page().nextKey)
        assertNull(reportedCap)
    }

    @Test
    fun pagesOfDuplicatesStillCountTowardTheCap() = runTest {
        maxPages = 2
        dataSource.enqueuePage("W1", nextCursor = "c2")
        dataSource.enqueuePage("W1", nextCursor = "c3")

        pager.refresh()
        val second = pager.append()!!.page()

        assertEquals(emptyList<String>(), second.data.map { it.openAlexId })
        assertNull(second.nextKey)
        assertEquals(50, reportedCap)
    }

    @Test
    fun theCapIsReadWhenEachPageArrives() = runTest {
        dataSource.enqueuePage("W1", nextCursor = "c2")
        dataSource.enqueuePage("W2", nextCursor = "c3")

        assertEquals("c2", pager.refresh().page().nextKey)
        maxPages = 2
        assertNull(pager.append()!!.page().nextKey)
        assertEquals(50, reportedCap)
    }
}
