package com.etatech.hashiya.crash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.etatech.hashiya.core.crash.CrashKey
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.feature.library.navigation.LibraryRoute
import com.etatech.hashiya.feature.paperdetails.navigation.PaperDetailsRoute
import com.etatech.hashiya.feature.reader.navigation.ReaderRoute
import com.etatech.hashiya.feature.search.navigation.SearchRoute
import com.etatech.hashiya.feature.settings.navigation.RestoreRoute
import com.etatech.hashiya.feature.settings.navigation.SettingsRoute

/** The closed screen value a crash report carries for [destination], or null for one that isn't a screen. */
fun screenFor(destination: NavDestination): String? = when {
    destination.hasRoute<LibraryRoute>() -> "library"
    destination.hasRoute<SearchRoute>() -> "search"
    destination.hasRoute<PaperDetailsRoute>() -> "details"
    destination.hasRoute<ReaderRoute>() -> "reader"
    destination.hasRoute<SettingsRoute>() -> "settings"
    destination.hasRoute<RestoreRoute>() -> "restore"
    else -> null
}

/** Keeps the Screen key on the screen being shown. */
@Composable
fun ReportScreens(navController: NavController, reporter: CrashReporter) {
    DisposableEffect(navController, reporter) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            screenFor(destination)?.let { reporter.setKey(CrashKey.Screen, it) }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}
