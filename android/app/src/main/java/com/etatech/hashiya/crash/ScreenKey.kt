package com.etatech.hashiya.crash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.Screen
import com.etatech.hashiya.core.crash.CrashKey
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.feature.library.navigation.LibraryRoute
import com.etatech.hashiya.feature.paperdetails.navigation.PaperDetailsRoute
import com.etatech.hashiya.feature.reader.navigation.ReaderRoute
import com.etatech.hashiya.feature.search.navigation.SearchRoute
import com.etatech.hashiya.feature.settings.navigation.RestoreRoute
import com.etatech.hashiya.feature.settings.navigation.SettingsRoute

/** The closed screen value a crash report and a screen view carry for [destination], or null for one that isn't a screen. */
fun screenFor(destination: NavDestination): Screen? = when {
    destination.hasRoute<LibraryRoute>() -> Screen.Library
    destination.hasRoute<SearchRoute>() -> Screen.Search
    destination.hasRoute<PaperDetailsRoute>() -> Screen.Details
    destination.hasRoute<ReaderRoute>() -> Screen.Reader
    destination.hasRoute<SettingsRoute>() -> Screen.Settings
    destination.hasRoute<RestoreRoute>() -> Screen.Restore
    else -> null
}

/** Keeps the Screen key on the screen being shown, and counts each screen view. */
@Composable
fun ReportScreens(navController: NavController, reporter: CrashReporter, analytics: Analytics) {
    DisposableEffect(navController, reporter, analytics) {
        val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
            screenFor(destination)?.let {
                reporter.setKey(CrashKey.Screen, it.id)
                analytics.log(AnalyticsEvent.ScreenView(it))
            }
        }
        navController.addOnDestinationChangedListener(listener)
        onDispose { navController.removeOnDestinationChangedListener(listener) }
    }
}
