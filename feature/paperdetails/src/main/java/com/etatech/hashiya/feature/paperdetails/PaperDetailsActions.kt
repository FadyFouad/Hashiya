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
    val onOpenLink: (String) -> Unit = {}
)
