package com.etatech.hashiya.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {
    @Test
    fun betweenRejectsReversedRange() {
        assertThrows(IllegalArgumentException::class.java) { YearFilter.Between(2021, 2020) }
    }

    @Test
    fun betweenAcceptsSingleYear() {
        YearFilter.Between(2020, 2020)
    }

    @Test
    fun sortIsNotAFilter() {
        assertFalse(SearchQuery("bert", sort = SearchSort.MostCited).hasActiveFilters)
    }

    @Test
    fun yearAndOpenAccessAreFilters() {
        assertTrue(SearchQuery("bert", years = YearFilter.Since(2020)).hasActiveFilters)
        assertTrue(SearchQuery("bert", openAccessOnly = true).hasActiveFilters)
    }
}
