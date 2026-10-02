package com.etatech.hashiya.feature.paperdetails

import com.etatech.hashiya.core.model.NoteSection
import com.etatech.hashiya.core.model.ReadingStatus

/** Every user action on the Details screen. Defaults are no-ops so tests set only what they check. */
internal data class PaperDetailsActions(
    val onBack: () -> Unit = {},
    val onRemove: () -> Unit = {},
    val onStatusChange: (ReadingStatus) -> Unit = {},
    val onNoteChange: (NoteSection, String) -> Unit = { _, _ -> },
    val onRetrySave: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onOpenLink: (String) -> Unit = {},
    val onCopyBibTeX: () -> Unit = {},
    val onToggleCollection: (Long, Boolean) -> Unit = { _, _ -> },
    val onNewCollection: () -> Unit = {},
    val onNewCollectionNameEdited: () -> Unit = {},
    val onNewCollectionConfirm: (String) -> Unit = {},
    val onNewCollectionDismiss: () -> Unit = {},
    val onReadPdf: () -> Unit = {},
    val onDownloadPdf: () -> Unit = {},
    val onCancelPdfDownload: () -> Unit = {},
    /** Asks for a file; the screen opens the system picker and passes the result to the view model. */
    val onAttachPdf: () -> Unit = {},
    val onRemovePdf: () -> Unit = {}
)
