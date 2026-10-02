package com.etatech.hashiya.core.model

data class SearchQuery(
    val text: String,
    val sort: SearchSort = SearchSort.Relevance,
    val years: YearFilter = YearFilter.AnyTime,
    val openAccessOnly: Boolean = false
) {
    val hasActiveFilters: Boolean
        get() = years != YearFilter.AnyTime || openAccessOnly
}

enum class SearchSort { Relevance, MostCited, Newest }

sealed interface YearFilter {
    data object AnyTime : YearFilter

    data class Since(val year: Int) : YearFilter

    data class Between(val from: Int, val to: Int) : YearFilter {
        init {
            require(from <= to) { "from ($from) must not be after to ($to)" }
        }
    }
}
