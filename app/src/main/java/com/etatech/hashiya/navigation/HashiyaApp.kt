package com.etatech.hashiya.navigation

import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.etatech.hashiya.R
import com.etatech.hashiya.core.designsystem.component.UpdateRequiredScreen
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.model.RequiredUpdate
import com.etatech.hashiya.feature.library.navigation.LibraryRoute
import com.etatech.hashiya.feature.library.navigation.libraryScreen
import com.etatech.hashiya.feature.library.navigation.navigateToLibrary
import com.etatech.hashiya.feature.library.navigation.requestLibraryRemove
import com.etatech.hashiya.feature.paperdetails.navigation.navigateToPaperDetails
import com.etatech.hashiya.feature.paperdetails.navigation.paperDetailsScreen
import com.etatech.hashiya.feature.search.navigation.SearchRoute
import com.etatech.hashiya.feature.search.navigation.navigateToSearch
import com.etatech.hashiya.feature.search.navigation.requestSearchRemove
import com.etatech.hashiya.feature.search.navigation.searchScreen
import com.etatech.hashiya.feature.settings.navigation.navigateToSettings
import com.etatech.hashiya.feature.settings.navigation.settingsScreen

enum class TopLevelDestination(val icon: ImageVector, @StringRes val labelRes: Int, val matches: (NavDestination) -> Boolean) {
    Library(HashiyaIcons.Library, R.string.nav_library, { it.hasRoute<LibraryRoute>() }),
    Search(HashiyaIcons.Search, R.string.nav_search, { it.hasRoute<SearchRoute>() })
}

@Composable
fun HashiyaApp(
    navController: NavHostController = rememberNavController(),
    pendingSearch: SearchRoute? = null,
    onPendingSearchHandled: () -> Unit = {},
    requiredUpdate: RequiredUpdate? = null,
    onOpenStore: (String) -> Unit = {}
) {
    if (requiredUpdate != null) {
        UpdateRequiredScreen(onUpdate = { onOpenStore(requiredUpdate.storeUrl) })
        return
    }
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination
    val currentTopLevel = TopLevelDestination.entries.firstOrNull { topLevel ->
        destination?.hierarchy?.any(topLevel.matches) == true
    }
    val layoutType = if (currentTopLevel != null) {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    } else {
        NavigationSuiteType.None
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { topLevel ->
                item(
                    selected = topLevel == currentTopLevel,
                    onClick = { navController.navigateToTopLevel(topLevel) },
                    icon = { Icon(topLevel.icon, contentDescription = null) },
                    label = { Text(stringResource(topLevel.labelRes)) }
                )
            }
        },
        layoutType = layoutType
    ) {
        NavHost(navController = navController, startDestination = LibraryRoute) {
            libraryScreen(
                onGoToSearch = { navController.navigateToTopLevel(TopLevelDestination.Search) },
                onAddPaper = { navController.openSearch(SearchRoute(focusSearch = true)) },
                onOpenSettings = { navController.navigateToSettings() },
                onOpenPaper = { openAlexId -> navController.navigateToPaperDetails(openAlexId) }
            )
            searchScreen(
                onOpenSettings = { navController.navigateToSettings() },
                onOpenPaper = { openAlexId -> navController.navigateToPaperDetails(openAlexId) }
            )
            // Not a top-level destination, so the navigation bar is hidden, as on Settings.
            paperDetailsScreen(
                onBack = { navController.popBackStack() },
                onRemove = { openAlexId -> navController.removeFromDetails(openAlexId) }
            )
            settingsScreen(onBack = { navController.popBackStack() })
        }

        LaunchedEffect(pendingSearch) {
            pendingSearch?.let { route ->
                navController.openSearch(route)
                onPendingSearchHandled()
            }
        }
    }
}

private fun NavController.navigateToTopLevel(destination: TopLevelDestination) {
    val options = navOptions {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
    when (destination) {
        TopLevelDestination.Library -> navigateToLibrary(options)
        TopLevelDestination.Search -> navigateToSearch(options)
    }
}

/**
 * Opens a fresh Search above the Library with [route]'s arguments, instead of restoring the previous search.
 * The pop saves state like the tab navigation does; without it, tapping the Library tab afterwards does nothing.
 */
internal fun NavController.openSearch(route: SearchRoute) = navigateToSearch(
    navOptions = navOptions { popUpTo(graph.findStartDestination().id) { saveState = true } },
    route = route
)

/**
 * Details' "Remove from library": the screen below does the removal, so the Library offers its usual Undo,
 * then Details closes. Details is only ever opened from the Library or Search.
 */
private fun NavController.removeFromDetails(openAlexId: String) {
    previousBackStackEntry?.let { previous ->
        when {
            previous.destination.hasRoute<LibraryRoute>() -> previous.requestLibraryRemove(openAlexId)
            previous.destination.hasRoute<SearchRoute>() -> previous.requestSearchRemove(openAlexId)
        }
    }
    popBackStack()
}
