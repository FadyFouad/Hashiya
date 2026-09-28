package com.etatech.hashiya.feature.library

import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.ReadingStatus

/** What the search field and the status chips show: the typed [query], the selected [status] (null = All) and the counts. */
data class LibraryFilter(
    val query: String = "",
    val status: ReadingStatus? = null,
    /** Papers of each status matching the applied search; every status is present. */
    val counts: Map<ReadingStatus, Int> = ReadingStatus.entries.associateWith { 0 }
) {
    /** The "All" chip's count: the total of the three. */
    val total: Int get() = counts.values.sum()
}

sealed interface LibraryUiState {
    data object Loading : LibraryUiState

    /** Nothing saved yet; the search field and chips are hidden. */
    data object Empty : LibraryUiState

    /** The library has papers, but none match the search and the chip. */
    data class NoMatches(val filter: LibraryFilter) : LibraryUiState

    data class Papers(val papers: List<LibraryPaper>, val filter: LibraryFilter) : LibraryUiState
}

enum class LibraryMessage { StatusUpdateFailed }
