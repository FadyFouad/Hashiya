package com.etatech.hashiya.feature.search.navigation

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

fun NavController.navigateToSearch(navOptions: NavOptions? = null, route: SearchRoute = SearchRoute()) = navigate(route, navOptions)

fun NavGraphBuilder.searchScreen(onOpenSettings: () -> Unit) {
    composable<SearchRoute> { SearchScreen(onOpenSettings = onOpenSettings) }
}
