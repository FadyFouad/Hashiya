package com.etatech.hashiya.core.data.search

import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter
import com.etatech.hashiya.core.network.WorksSearchRequest

internal const val PAGE_SIZE = 25
internal const val FIRST_CURSOR = "*"

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
        search = text.trim(),
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
