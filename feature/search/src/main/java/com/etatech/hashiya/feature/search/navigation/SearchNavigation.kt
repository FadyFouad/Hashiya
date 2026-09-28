package com.etatech.hashiya.feature.search.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.search.SearchScreen
import kotlinx.serialization.Serializable

@Serializable
data object SearchRoute

fun NavController.navigateToSearch(navOptions: NavOptions? = null) = navigate(SearchRoute, navOptions)

fun NavGraphBuilder.searchScreen(onOpenSettings: () -> Unit) {
    composable<SearchRoute> { SearchScreen(onOpenSettings = onOpenSettings) }
}
