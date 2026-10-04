package com.etatech.hashiya.navigation

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.navigation.toRoute
import com.etatech.hashiya.R
import com.etatech.hashiya.core.designsystem.component.UpdateRequiredScreen
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.designsystem.layout.showsTwoPanes
import com.etatech.hashiya.core.model.RequiredUpdate
import com.etatech.hashiya.feature.library.navigation.LibraryRoute
import com.etatech.hashiya.feature.library.navigation.libraryScreen
import com.etatech.hashiya.feature.library.navigation.navigateToLibrary
import com.etatech.hashiya.feature.library.navigation.requestLibraryFind
import com.etatech.hashiya.feature.library.navigation.requestLibraryRemove
import com.etatech.hashiya.feature.library.navigation.requestLibrarySelect
import com.etatech.hashiya.feature.paperdetails.navigation.PaperDetailsPane
import com.etatech.hashiya.feature.paperdetails.navigation.PaperDetailsRoute
import com.etatech.hashiya.feature.paperdetails.navigation.navigateToPaperDetails
import com.etatech.hashiya.feature.paperdetails.navigation.paperDetailsScreen
import com.etatech.hashiya.feature.reader.navigation.navigateToReader
import com.etatech.hashiya.feature.reader.navigation.readerScreen
import com.etatech.hashiya.feature.search.navigation.SearchRoute
import com.etatech.hashiya.feature.search.navigation.navigateToSearch
import com.etatech.hashiya.feature.search.navigation.requestSearchFind
import com.etatech.hashiya.feature.search.navigation.requestSearchRemove
import com.etatech.hashiya.feature.search.navigation.searchScreen
import com.etatech.hashiya.feature.settings.navigation.SettingsRoute
import com.etatech.hashiya.feature.settings.navigation.navigateToRestore
import com.etatech.hashiya.feature.settings.navigation.navigateToSettings
import com.etatech.hashiya.feature.settings.navigation.restoreScreen
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
    pendingRestore: String? = null,
    onPendingRestoreHandled: () -> Unit = {},
    requiredUpdate: RequiredUpdate? = null,
    onOpenStore: (String) -> Unit = {},
    shortcut: AppShortcut? = null,
    onShortcutHandled: () -> Unit = {}
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
    // The rail stays on Details, Reader and Settings, where it keeps the tab they were opened from selected.
    var lastTopLevel by rememberSaveable { mutableStateOf(TopLevelDestination.Library) }
    LaunchedEffect(currentTopLevel) { currentTopLevel?.let { lastTopLevel = it } }

    HashiyaNavigationSuite(
        currentTopLevel = currentTopLevel,
        selectedTopLevel = currentTopLevel ?: lastTopLevel,
        onSelect = { topLevel -> navController.navigateToTopLevel(topLevel) },
        onReselectFromSubScreen = { topLevel -> navController.popToTopLevel(topLevel) }
    ) {
        NavHost(navController = navController, startDestination = LibraryRoute) {
            libraryScreen(
                onGoToSearch = { navController.navigateToTopLevel(TopLevelDestination.Search) },
                onAddPaper = { navController.openSearch(SearchRoute(focusSearch = true)) },
                onOpenSettings = { navController.navigateToSettings() },
                onOpenPaper = { openAlexId -> navController.navigateToPaperDetails(openAlexId) },
                detailPane = { openAlexId, onClose, onRemove ->
                    PaperDetailsPane(
                        openAlexId = openAlexId,
                        onClose = onClose,
                        onRemove = onRemove,
                        onReadPdf = { navController.navigateToReader(it) }
                    )
                }
            )
            searchScreen(
                onOpenSettings = { navController.navigateToSettings() },
                onOpenPaper = { openAlexId -> navController.navigateToPaperDetails(openAlexId) }
            )
            // Not top-level destinations: the bar hides on compact windows; the rail stays from medium width.
            // From 840dp the Library shows Details in its detail pane instead; Search still opens this screen.
            paperDetailsScreen(
                onBack = { navController.popBackStack() },
                onRemove = { openAlexId -> navController.removeFromDetails(openAlexId) },
                onReadPdf = { openAlexId -> navController.navigateToReader(openAlexId) }
            )
            readerScreen(onBack = { navController.popBackStack() })
            settingsScreen(
                onBack = { navController.popBackStack() },
                onOpenRestore = { uri -> navController.navigateToRestore(uri) }
            )
            restoreScreen(onDone = { navController.popBackStack() })
        }

        // Details opened from the Library on a narrow window moves into the Library's pane once the window is wide
        // enough (unfold, rotation, resize), so the paper stays open in the layout the window now has.
        val twoPanes = showsTwoPanes()
        LaunchedEffect(twoPanes, backStackEntry) {
            if (twoPanes) navController.moveDetailsIntoLibraryPane()
        }

        LaunchedEffect(shortcut) {
            shortcut?.let {
                navController.onShortcut(it)
                onShortcutHandled()
            }
        }

        LaunchedEffect(pendingSearch) {
            pendingSearch?.let { route ->
                navController.openSearch(route)
                onPendingSearchHandled()
            }
        }

        LaunchedEffect(pendingRestore) {
            pendingRestore?.let { uri ->
                navController.navigateToRestore(uri)
                onPendingRestoreHandled()
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

private fun NavController.onShortcut(shortcut: AppShortcut) {
    val current = currentBackStackEntry ?: return
    when (shortcut) {
        AppShortcut.Find -> when {
            current.destination.hasRoute<LibraryRoute>() -> current.requestLibraryFind()

            current.destination.hasRoute<SearchRoute>() -> current.requestSearchFind()

            else -> {
                navigateToTopLevel(TopLevelDestination.Search)
                currentBackStackEntry?.requestSearchFind()
            }
        }

        AppShortcut.AddPaper -> openSearch(SearchRoute(focusSearch = true))

        AppShortcut.Settings -> if (!current.destination.hasRoute<SettingsRoute>()) navigateToSettings()
    }
}

private fun NavController.moveDetailsIntoLibraryPane() {
    val current = currentBackStackEntry ?: return
    val library = previousBackStackEntry?.takeIf { it.destination.hasRoute<LibraryRoute>() } ?: return
    if (!current.destination.hasRoute<PaperDetailsRoute>()) return
    library.requestLibrarySelect(current.toRoute<PaperDetailsRoute>().openAlexId)
    popBackStack()
}

/** The selected tab's item, tapped on one of its sub-screens (rail only): back to that tab's root. */
private fun NavController.popToTopLevel(destination: TopLevelDestination) {
    when (destination) {
        TopLevelDestination.Library -> popBackStack<LibraryRoute>(inclusive = false)
        TopLevelDestination.Search -> popBackStack<SearchRoute>(inclusive = false)
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
