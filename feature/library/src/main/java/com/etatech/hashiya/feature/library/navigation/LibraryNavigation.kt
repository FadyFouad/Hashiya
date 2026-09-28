package com.etatech.hashiya.feature.library.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.library.LibraryScreen
import kotlinx.serialization.Serializable

@Serializable
data object LibraryRoute

fun NavController.navigateToLibrary(navOptions: NavOptions? = null) = navigate(LibraryRoute, navOptions)

fun NavGraphBuilder.libraryScreen(onGoToSearch: () -> Unit, onOpenSettings: () -> Unit) {
    composable<LibraryRoute> { LibraryScreen(onGoToSearch = onGoToSearch, onOpenSettings = onOpenSettings) }
}
