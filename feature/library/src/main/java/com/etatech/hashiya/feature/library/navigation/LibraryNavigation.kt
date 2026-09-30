package com.etatech.hashiya.feature.library.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.library.LibraryScreen
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

/** The key in the Library entry's SavedStateHandle, which its ViewModel shares, that asks it to remove a paper. */
internal const val LIBRARY_REMOVE_REQUEST = "library_remove_request"

fun NavController.navigateToLibrary(navOptions: NavOptions? = null) = navigate(LibraryRoute, navOptions)

fun NavGraphBuilder.libraryScreen(
    onGoToSearch: () -> Unit,
    onAddPaper: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPaper: (openAlexId: String) -> Unit
) {
    composable<LibraryRoute> {
        LibraryScreen(onGoToSearch = onGoToSearch, onAddPaper = onAddPaper, onOpenSettings = onOpenSettings, onOpenPaper = onOpenPaper)
    }
}

/** Asks this Library entry to remove [openAlexId] the way a swipe does, with Undo: Details' "Remove from library". */
fun NavBackStackEntry.requestLibraryRemove(openAlexId: String) {
    savedStateHandle[LIBRARY_REMOVE_REQUEST] = openAlexId
}
