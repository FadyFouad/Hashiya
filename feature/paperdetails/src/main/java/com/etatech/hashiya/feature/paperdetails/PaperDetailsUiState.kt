package com.etatech.hashiya.feature.paperdetails

import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.PaperNotes

sealed interface PaperDetailsUiState {
    data object Loading : PaperDetailsUiState

    data class Loaded(val paper: LibraryPaper, val notes: PaperNotes, val saveState: NotesSaveState) : PaperDetailsUiState
}

/** What the line beside "My notes" shows: nothing, Saving…, Saved or Couldn't save. */
enum class NotesSaveState { Idle, Saving, Saved, Failed }

enum class PaperDetailsMessage { NotesSaveFailed, StatusUpdateFailed }

/** Tells the screen to leave: [Closed] when the paper is gone, [Removed] once the user removed it and its notes are saved. */
enum class PaperDetailsExit { Closed, Removed }
