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

private const val KEY_ROUTE_APPLIED = "search_route_applied"
private const val KEY_PAGE_TITLE = "search_page_title"

// Names of SearchRoute's properties: navigation stores route arguments in the SavedStateHandle under these keys.
private const val ARG_QUERY = "query"
private const val ARG_PAGE_TITLE = "pageTitle"
private const val ARG_FOCUS_SEARCH = "focusSearch"
private const val ARG_NOTE = "note"

internal data class SearchRouteArgs(val query: String?, val pageTitle: String?, val focusSearch: Boolean, val note: SearchNote?)

/** The navigation arguments the first time this screen's state is created; null afterwards, including after process death. */
internal fun SavedStateHandle.consumeRouteArgs(): SearchRouteArgs? {
    if (get<Boolean>(KEY_ROUTE_APPLIED) == true || contains(KEY_TEXT)) return null
    this[KEY_ROUTE_APPLIED] = true
    return SearchRouteArgs(
        query = get<String>(ARG_QUERY)?.takeIf { it.isNotBlank() },
        pageTitle = get<String>(ARG_PAGE_TITLE)?.takeIf { it.isNotBlank() },
        focusSearch = get<Boolean>(ARG_FOCUS_SEARCH) ?: false,
        note = get<String>(ARG_NOTE)?.let { name -> SearchNote.entries.firstOrNull { it.name == name } }
    )
}

internal var SavedStateHandle.savedPageTitle: String?
    get() = get(KEY_PAGE_TITLE)
    set(value) {
        this[KEY_PAGE_TITLE] = value
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
