package com.etatech.hashiya.core.data.search

import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter
import com.etatech.hashiya.core.model.withoutArabicMarks
import com.etatech.hashiya.core.network.WorksSearchRequest

internal const val PAGE_SIZE = 25
internal const val FIRST_CURSOR = "*"

private val WILDCARDS_AND_SPACES = Regex("[?*\\s]+")

/** OpenAlex reads ? and * as wildcards and rejects them in its default (stemmed) search with a 400, so a title such as
 * "ChatGPT for good? …" failed. They carry no meaning for a keyword search. */
private fun String.withoutWildcards(): String = replace(WILDCARDS_AND_SPACES, " ").trim()

internal fun SearchQuery.toWorksSearchRequest(cursor: String): WorksSearchRequest {
    val filters = buildList {
        when (val range = years) {
            YearFilter.AnyTime -> Unit
            is YearFilter.Since -> add("publication_year:>${range.year - 1}")
            is YearFilter.Between -> add("publication_year:${range.from}-${range.to}")
        }
        if (openAccessOnly) add("is_oa:true")
    }
    return WorksSearchRequest(
        search = withoutArabicMarks(text).withoutWildcards(),
        filter = filters.joinToString(",").ifEmpty { null },
        sort = when (sort) {
            SearchSort.Relevance -> null
            SearchSort.MostCited -> "cited_by_count:desc"
            SearchSort.Newest -> "publication_date:desc"
        },
        cursor = cursor,
        perPage = PAGE_SIZE
    )
}
