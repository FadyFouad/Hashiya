package com.etatech.hashiya.feature.reader

import java.io.File

sealed interface ReaderState {
    data object Loading : ReaderState

    data class Ready(
        val title: String,
        val pageCount: Int,
        /** Each page's height divided by its width, so every page has its size before it renders and the list never jumps. */
        val pageAspectRatios: List<Float>,
        /** Zero-based page to open on: the stored last page. */
        val startPage: Int,
        /** The stored PDF, for Share. */
        val file: File
    ) : ReaderState

    /** PdfRenderer can't read the file (damaged or password-protected). */
    data class CantOpen(val title: String) : ReaderState
}

enum class ReaderMessage { NotesSaveFailed, NotPdf, TooLarge, AttachFailed }

/** Tells the screen to leave: [Back] once typed notes are saved, [Closed] when the PDF or the paper is gone. */
enum class ReaderExit { Back, Closed }
