package com.etatech.hashiya.feature.search

import androidx.lifecycle.SavedStateHandle
import com.etatech.hashiya.core.model.SearchQuery
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter

private const val KEY_TEXT = "search_text"
private const val KEY_SORT = "search_sort"
private const val KEY_YEAR_KIND = "search_year_kind"
private const val KEY_YEAR_FROM = "search_year_from"
private const val KEY_YEAR_TO = "search_year_to"
private const val KEY_OPEN_ACCESS = "search_oa"
private const val YEAR_KIND_SINCE = "since"
private const val YEAR_KIND_BETWEEN = "between"

/** Reads the query stored by [writeSearchQuery]; anything missing or invalid falls back to defaults. */
internal fun SavedStateHandle.readSearchQuery(): SearchQuery {
    val from = get<Int>(KEY_YEAR_FROM)
    val to = get<Int>(KEY_YEAR_TO)
    val years = when (get<String>(KEY_YEAR_KIND)) {
        YEAR_KIND_SINCE -> from?.let { YearFilter.Since(it) }
        YEAR_KIND_BETWEEN -> if (from != null && to != null && from <= to) YearFilter.Between(from, to) else null
        else -> null
    } ?: YearFilter.AnyTime
    return SearchQuery(
        text = get<String>(KEY_TEXT).orEmpty(),
        sort = get<String>(KEY_SORT)?.let { name -> SearchSort.entries.firstOrNull { it.name == name } }
            ?: SearchSort.Relevance,
        years = years,
        openAccessOnly = get<Boolean>(KEY_OPEN_ACCESS) ?: false
    )
}

internal fun SavedStateHandle.writeSearchQuery(query: SearchQuery) {
    this[KEY_TEXT] = query.text
    this[KEY_SORT] = query.sort.name
    this[KEY_OPEN_ACCESS] = query.openAccessOnly
    when (val years = query.years) {
        YearFilter.AnyTime -> {
            remove<String>(KEY_YEAR_KIND)
            remove<Int>(KEY_YEAR_FROM)
            remove<Int>(KEY_YEAR_TO)
        }

        is YearFilter.Since -> {
            this[KEY_YEAR_KIND] = YEAR_KIND_SINCE
            this[KEY_YEAR_FROM] = years.year
            remove<Int>(KEY_YEAR_TO)
        }

        is YearFilter.Between -> {
            this[KEY_YEAR_KIND] = YEAR_KIND_BETWEEN
            this[KEY_YEAR_FROM] = years.from
            this[KEY_YEAR_TO] = years.to
        }
    }
}
