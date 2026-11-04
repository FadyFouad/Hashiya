package com.etatech.hashiya.feature.importer.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.importer.ImportScreen
import kotlinx.serialization.Serializable

/** [uri] is a file shared or opened from another app; null opens the screen on the current job or the picker's start. */
@Serializable
data class ImportRoute(val uri: String? = null)

fun NavController.navigateToImport(uri: String? = null, navOptions: NavOptions? = null) = navigate(ImportRoute(uri), navOptions)

fun NavGraphBuilder.importScreen(onBack: () -> Unit, onShowLibrary: () -> Unit) {
    composable<ImportRoute> { ImportScreen(onBack = onBack, onShowLibrary = onShowLibrary) }
}
