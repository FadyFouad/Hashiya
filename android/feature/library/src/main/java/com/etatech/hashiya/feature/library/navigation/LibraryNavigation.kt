package com.etatech.hashiya.feature.library.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.library.LibraryDetailPane
import com.etatech.hashiya.feature.library.LibraryScreen
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

/** The key in the Library back stack entry's SavedStateHandle that asks it to remove a paper. */
private const val LIBRARY_REMOVE_REQUEST = "library_remove_request"

/** The key that asks the Library to focus its search field: Ctrl+F. */
private const val LIBRARY_FIND_REQUEST = "library_find_request"

/** The key that asks the Library to show a paper in its detail pane. */
private const val LIBRARY_SELECT_REQUEST = "library_select_request"

fun NavController.navigateToLibrary(navOptions: NavOptions? = null) = navigate(LibraryRoute, navOptions)

fun NavGraphBuilder.libraryScreen(
    onGoToSearch: () -> Unit,
    onAddPaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPaper: (openAlexId: String) -> Unit,
    detailPane: LibraryDetailPane? = null
) {
    composable<LibraryRoute> { entry ->
        // Written by Details through requestLibraryRemove; the entry's handle survives process death, so no request is lost.
        val removeRequest by entry.savedStateHandle.getStateFlow<String?>(LIBRARY_REMOVE_REQUEST, null).collectAsStateWithLifecycle()
        val findRequest by entry.savedStateHandle.getStateFlow(LIBRARY_FIND_REQUEST, false).collectAsStateWithLifecycle()
        val selectRequest by entry.savedStateHandle.getStateFlow<String?>(LIBRARY_SELECT_REQUEST, null).collectAsStateWithLifecycle()
        LibraryScreen(
            onGoToSearch = onGoToSearch,
            onAddPaper = onAddPaper,
            onOpenSettings = onOpenSettings,
            onOpenPaper = onOpenPaper,
            removeRequest = removeRequest,
            onRemoveRequestHandled = { entry.savedStateHandle[LIBRARY_REMOVE_REQUEST] = null },
            detailPane = detailPane,
            selectRequest = selectRequest,
            onSelectRequestHandled = { entry.savedStateHandle[LIBRARY_SELECT_REQUEST] = null },
            findRequested = findRequest,
            onFindHandled = { entry.savedStateHandle[LIBRARY_FIND_REQUEST] = false }
        )
    }
}

/** Asks this Library entry to remove [openAlexId] the way a swipe does, with Undo: Details' "Remove from library". */
fun NavBackStackEntry.requestLibraryRemove(openAlexId: String) {
    savedStateHandle[LIBRARY_REMOVE_REQUEST] = openAlexId
}

/** Asks this Library entry to show [openAlexId] in its detail pane: Details moving into the pane as the window widens. */
fun NavBackStackEntry.requestLibrarySelect(openAlexId: String) {
    savedStateHandle[LIBRARY_SELECT_REQUEST] = openAlexId
}

/** Asks this Library entry to focus its search field: Ctrl+F. */
fun NavBackStackEntry.requestLibraryFind() {
    savedStateHandle[LIBRARY_FIND_REQUEST] = true
}
