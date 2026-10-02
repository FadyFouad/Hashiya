package com.etatech.hashiya.feature.library

import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.PaperCollection
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

    /** The selected collection has no papers at all; the search field and chips are hidden. */
    data class CollectionEmpty(val collection: PaperCollection) : LibraryUiState

    /** The library (or the selected collection) has papers, but none match the search and the chip. */
    data class NoMatches(val filter: LibraryFilter) : LibraryUiState

    data class Papers(val papers: List<LibraryPaper>, val filter: LibraryFilter) : LibraryUiState
}

/** The top bar: the collection selector and whether Export is offered. */
data class LibraryHeader(
    val collections: List<PaperCollection> = emptyList(),
    /** Null is All papers. */
    val selected: PaperCollection? = null,
    /** Papers in the current view, ignoring the search and the chip; Export is offered when above 0. */
    val viewSize: Int = 0,
    /** Every saved paper: the count beside "All papers" in the selector. */
    val libraryCount: Int = 0,
    val exporting: Boolean = false
)

/** The dialog on top of the Library, if any. */
sealed interface CollectionDialog {
    data class New(val nameTaken: Boolean = false) : CollectionDialog

    data class Rename(val collection: PaperCollection, val nameTaken: Boolean = false) : CollectionDialog

    data class ConfirmDelete(val collection: PaperCollection) : CollectionDialog
}

/** A paper swiped out of [collection], for Undo. */
data class CollectionRemoval(val collection: PaperCollection, val openAlexId: String)

/** A .bib file ready to share. */
data class BibExport(val fileName: String, val bibtex: String, val complete: Boolean)

enum class LibraryMessage { StatusUpdateFailed, CollectionsUpdateFailed, ExportFailed, ExportIncomplete }
