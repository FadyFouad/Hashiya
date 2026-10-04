package com.etatech.hashiya.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.settings.restore.RestoreScreen
import kotlinx.serialization.Serializable

/** [uri]: the picked or opened `.hashiya` file. */
@Serializable
data class RestoreRoute(val uri: String)

fun NavController.navigateToRestore(uri: String) = navigate(RestoreRoute(uri))

fun NavGraphBuilder.restoreScreen(onDone: () -> Unit) {
    composable<RestoreRoute> { RestoreScreen(onDone = onDone) }
}
