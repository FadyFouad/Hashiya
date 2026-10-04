package com.etatech.hashiya.core.data.search

import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter
import com.etatech.hashiya.core.network.WorksSearchRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchRequestMappingTest {
    @Test
    fun plainQueryHasNoFilterOrSort() {
        assertEquals(
            WorksSearchRequest(search = "bert", filter = null, sort = null, cursor = "*", perPage = 25),
            SearchQuery("  bert ").toWorksSearchRequest(FIRST_CURSOR)
        )
    }

    /** OpenAlex reads ? and * as wildcards and rejects them in its default (stemmed) search with a 400. */
    @Test
    fun dropsWildcardCharacters() {
        assertEquals(
            "ChatGPT for good On opportunities",
            SearchQuery("ChatGPT for good? On opportunities").toWorksSearchRequest("*").search
        )
        assertEquals("what is it", SearchQuery("what is it?").toWorksSearchRequest("*").search)
        assertEquals("transform models", SearchQuery("transform* models").toWorksSearchRequest("*").search)
        assertEquals("", SearchQuery("?").toWorksSearchRequest("*").search)
    }

    @Test
    fun mapsSortOptions() {
        assertEquals(null, SearchQuery("x", sort = SearchSort.Relevance).toWorksSearchRequest("*").sort)
        assertEquals("cited_by_count:desc", SearchQuery("x", sort = SearchSort.MostCited).toWorksSearchRequest("*").sort)
        assertEquals("publication_date:desc", SearchQuery("x", sort = SearchSort.Newest).toWorksSearchRequest("*").sort)
    }

    @Test
    fun sinceBecomesStrictlyGreaterThanPreviousYear() {
        assertEquals(
            "publication_year:>2019",
            SearchQuery("x", years = YearFilter.Since(2020)).toWorksSearchRequest("*").filter
        )
    }

    @Test
    fun betweenBecomesRange() {
        assertEquals(
            "publication_year:2015-2020",
            SearchQuery("x", years = YearFilter.Between(2015, 2020)).toWorksSearchRequest("*").filter
        )
    }

    @Test
    fun combinesYearAndOpenAccessFilters() {
        assertEquals(
            "publication_year:>2019,is_oa:true",
            SearchQuery("x", years = YearFilter.Since(2020), openAccessOnly = true).toWorksSearchRequest("*").filter
        )
        assertEquals("is_oa:true", SearchQuery("x", openAccessOnly = true).toWorksSearchRequest("*").filter)
    }

    @Test
    fun passesCursorThrough() {
        assertEquals("abc", SearchQuery("x").toWorksSearchRequest("abc").cursor)
    }

    @Test
    fun stripsArabicTashkeelFromSearchText() {
        assertEquals(
            "التعلم",
            SearchQuery(" التَّعلُّم ").toWorksSearchRequest(FIRST_CURSOR).search
        )
    }

    @Test
    fun keepsLatinAccentsInSearchText() {
        assertEquals(
            "Schrödinger",
            SearchQuery("Schrödinger").toWorksSearchRequest(FIRST_CURSOR).search
        )
    }
}
