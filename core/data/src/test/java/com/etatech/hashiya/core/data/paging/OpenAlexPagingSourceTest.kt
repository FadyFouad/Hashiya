package com.etatech.hashiya.core.data.paging

import androidx.paging.PagingConfig
import androidx.paging.PagingSource.LoadResult
import androidx.paging.testing.TestPager
import com.etatech.hashiya.core.data.FakeOpenAlexDataSource
import com.etatech.hashiya.core.data.repository.SearchException
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchError
import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.network.NetworkFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAlexPagingSourceTest {
    private val dataSource = FakeOpenAlexDataSource()
    private var reportedCount: Long? = null
    private val pager = TestPager(
        PagingConfig(pageSize = 25, initialLoadSize = 25, enablePlaceholders = false),
        OpenAlexPagingSource(SearchQuery("bert"), dataSource) { reportedCount = it }
    )

    private fun LoadResult<String, Paper>.page() = this as LoadResult.Page<String, Paper>

    @Test
    fun firstPageUsesStartCursorAndReportsTotal() = runTest {
        dataSource.enqueuePage("W1", "W2", nextCursor = "c2", count = 48210)

        val page = pager.refresh().page()

        assertEquals("*", dataSource.requests.single().cursor)
        assertEquals(listOf("W1", "W2"), page.data.map { it.openAlexId })
        assertEquals("c2", page.nextKey)
        assertEquals(48210L, reportedCount)
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
}
