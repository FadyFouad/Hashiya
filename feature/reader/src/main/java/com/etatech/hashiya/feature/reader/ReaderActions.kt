package com.etatech.hashiya.feature.reader

import com.etatech.hashiya.core.model.NoteSection

/** Every user action on the reader. Defaults are no-ops so tests set only what they check. */
internal data class ReaderActions(
    val onBack: () -> Unit = {},
    val onShare: () -> Unit = {},
    val onOpenNotes: () -> Unit = {},
    val onCloseNotes: () -> Unit = {},
    val onNoteChange: (NoteSection, String) -> Unit = { _, _ -> },
    val onRetrySave: () -> Unit = {},
    val onViewport: (firstVisible: Int, lastVisible: Int, widthPx: Int, zoom: Float) -> Unit = { _, _, _, _ -> },
    val onPageChanged: (Int) -> Unit = {},
    val onReplace: () -> Unit = {},
    val onRemovePdf: () -> Unit = {},
    val onMessageShown: () -> Unit = {}
)
