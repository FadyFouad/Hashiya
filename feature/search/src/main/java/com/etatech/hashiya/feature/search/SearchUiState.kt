package com.etatech.hashiya.feature.search

import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperIdentifier
import com.etatech.hashiya.core.model.SearchError
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

/** What Search shows when the submitted text is a DOI or arXiv ID ("ID mode"). */
sealed interface LookupUiState {
    data class Looking(val identifier: PaperIdentifier) : LookupUiState

    data class Found(val paper: Paper) : LookupUiState

    /** [searchTitle] is arXiv's title or the shared page's title, offered as a keyword search; null offers none. */
    data class NotFound(val identifier: PaperIdentifier, val searchTitle: String?) : LookupUiState

    data class Failed(val error: SearchError) : LookupUiState

    /** The submitted text is a single link with no DOI or arXiv ID in it; no search or lookup runs. */
    data object NoIdInLink : LookupUiState
}
