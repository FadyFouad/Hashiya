package com.etatech.hashiya.feature.search

import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.SearchSort
import com.etatech.hashiya.core.model.YearFilter

/** Every user action on the search screen. Defaults are no-ops so tests set only what they check. */
internal data class SearchActions(
    val onTextChange: (String) -> Unit = {},
    val onSearchAction: () -> Unit = {},
    val onSuggestion: (String) -> Unit = {},
    val onSortChange: (SearchSort) -> Unit = {},
    val onYearFilterChange: (YearFilter) -> Unit = {},
    val onOpenAccessToggle: () -> Unit = {},
    val onClearFilters: () -> Unit = {},
    val onPaperClick: (Paper) -> Unit = {},
    val onOpenDetails: (Paper) -> Unit = {},
    val onToggleSave: (PaperItem) -> Unit = {},
    val onDismissPreview: () -> Unit = {},
    val onOpenDoi: (String) -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onRetryLookup: () -> Unit = {},
    val onFocusHandled: () -> Unit = {}
)
