package com.etatech.hashiya.feature.library

import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.ReadingStatus

/** Every user action on the Library screen. Defaults are no-ops so tests set only what they check. */
internal data class LibraryActions(
    val onQueryChange: (String) -> Unit = {},
    val onSearch: () -> Unit = {},
    val onClearQuery: () -> Unit = {},
    val onStatusFilterChange: (ReadingStatus?) -> Unit = {},
    val onClearSearchAndFilters: () -> Unit = {},
    val onPaperClick: (Paper) -> Unit = {},
    val onStatusChange: (Paper, ReadingStatus) -> Unit = { _, _ -> },
    val onRemove: (Paper) -> Unit = {},
    val onUndo: () -> Unit = {},
    val onUndoDismissed: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onGoToSearch: () -> Unit = {},
    val onAddPaper: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onSelectCollection: (Long?) -> Unit = {},
    val onNewCollection: () -> Unit = {},
    val onRenameCollection: (PaperCollection) -> Unit = {},
    val onDeleteCollection: (PaperCollection) -> Unit = {},
    val onDialogNameEdited: () -> Unit = {},
    val onDialogConfirm: (String) -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDialogDismiss: () -> Unit = {},
    val onUndoCollection: () -> Unit = {},
    val onCollectionUndoDismissed: () -> Unit = {},
    val onExport: () -> Unit = {}
)
