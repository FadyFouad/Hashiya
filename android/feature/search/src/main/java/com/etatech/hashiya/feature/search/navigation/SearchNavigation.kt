package com.etatech.hashiya.feature.search.navigation

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.search.SearchScreen
import kotlinx.serialization.Serializable

/**
 * [query] is submitted immediately; [pageTitle] is a shared page's title for the not-found fallback;
 * [focusSearch] opens the keyboard; [note] is a `SearchNote` name. All are applied once.
 */
@Serializable
data class SearchRoute(val query: String? = null, val pageTitle: String? = null, val focusSearch: Boolean = false, val note: String? = null)

/** The key in the Search back stack entry's SavedStateHandle that asks it to remove a paper. */
private const val SEARCH_REMOVE_REQUEST = "search_remove_request"

/** The key that asks Search to focus its field: Ctrl+F. */
private const val SEARCH_FIND_REQUEST = "search_find_request"

fun NavController.navigateToSearch(navOptions: NavOptions? = null, route: SearchRoute = SearchRoute()) = navigate(route, navOptions)

fun NavGraphBuilder.searchScreen(onOpenSettings: () -> Unit, onOpenPaper: (openAlexId: String) -> Unit) {
    composable<SearchRoute> { entry ->
        // Written by Details through requestSearchRemove.
        val removeRequest by entry.savedStateHandle.getStateFlow<String?>(SEARCH_REMOVE_REQUEST, null).collectAsStateWithLifecycle()
        val findRequest by entry.savedStateHandle.getStateFlow(SEARCH_FIND_REQUEST, false).collectAsStateWithLifecycle()
        SearchScreen(
            onOpenSettings = onOpenSettings,
            onOpenPaper = onOpenPaper,
            removeRequest = removeRequest,
            onRemoveRequestHandled = { entry.savedStateHandle[SEARCH_REMOVE_REQUEST] = null },
            findRequested = findRequest,
            onFindHandled = { entry.savedStateHandle[SEARCH_FIND_REQUEST] = false }
        )
    }
}

/** Asks this Search entry to remove [openAlexId] from the library, as its sheet's Remove does: Details' "Remove from library". */
fun NavBackStackEntry.requestSearchRemove(openAlexId: String) {
    savedStateHandle[SEARCH_REMOVE_REQUEST] = openAlexId
}

/** Asks this Search entry to focus its field, keeping the current search: Ctrl+F. */
fun NavBackStackEntry.requestSearchFind() {
    savedStateHandle[SEARCH_FIND_REQUEST] = true
}
