package com.etatech.hashiya.core.data.repository

import androidx.paging.testing.asSnapshot
import com.etatech.hashiya.core.data.FakeOpenAlexDataSource
import com.etatech.hashiya.core.model.SearchQuery
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAlexSearchRepositoryTest {
    private val dataSource = FakeOpenAlexDataSource()
    private val repository = OpenAlexSearchRepository(dataSource)

    @Test
    fun loadsFirstPageAndExposesTotalCount() = runTest {
        dataSource.enqueuePage("W1", "W2", nextCursor = null, count = 2)
        val results = repository.search(SearchQuery("bert"))

        assertNull(results.totalCount.value)
        assertEquals(listOf("W1", "W2"), results.papers.asSnapshot().map { it.openAlexId })
        assertEquals(2L, results.totalCount.value)
    }
}
