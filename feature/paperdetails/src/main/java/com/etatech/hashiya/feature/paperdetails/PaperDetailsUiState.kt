package com.etatech.hashiya.feature.paperdetails

import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.PaperNotes

sealed interface PaperDetailsUiState {
    data object Loading : PaperDetailsUiState

    data class Loaded(
        val paper: LibraryPaper,
        val notes: PaperNotes,
        val saveState: NotesSaveState,
        /** Every collection, for the checklist. */
        val collections: List<PaperCollection> = emptyList(),
        /** The collections this paper is in. */
        val memberOf: Set<Long> = emptySet()
    ) : PaperDetailsUiState
}

/** What the line beside "My notes" shows: nothing, Saving…, Saved or Couldn't save. */
enum class NotesSaveState { Idle, Saving, Saved, Failed }

enum class PaperDetailsMessage {
    NotesSaveFailed,
    StatusUpdateFailed,
    CollectionsUpdateFailed,
    BibTeXCopied,
    BibTeXIncomplete,
    CopyFailed,
    PdfAttachNotPdf,
    PdfAttachTooLarge,
    PdfAttachFailed
}

/** Tells the screen to leave: [Closed] when the paper is gone, [Removed] once the user removed it and its notes are saved. */
enum class PaperDetailsExit { Closed, Removed }

/** The "New collection" dialog opened from the checklist. */
data class NewCollectionDialog(val nameTaken: Boolean = false)

/** An entry for the screen to put on the clipboard; the screen then calls onCopyHandled. */
data class CopiedBibTeX(val text: String, val complete: Boolean)
