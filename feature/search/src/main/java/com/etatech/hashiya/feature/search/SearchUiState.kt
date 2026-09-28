package com.etatech.hashiya.feature.search

import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter

/** [text] is exactly what is in the field; [isIdle] means no search is active (blank query). */
data class SearchUiState(
    val text: String = "",
    val sort: SearchSort = SearchSort.Relevance,
    val years: YearFilter = YearFilter.AnyTime,
    val openAccessOnly: Boolean = false,
    val isIdle: Boolean = true,
    val totalCount: Long? = null
) {
    val hasActiveFilters: Boolean
        get() = years != YearFilter.AnyTime || openAccessOnly
}

data class PaperItem(val paper: Paper, val inLibrary: Boolean)

enum class SearchMessage { SaveFailed, RemoveFailed }
