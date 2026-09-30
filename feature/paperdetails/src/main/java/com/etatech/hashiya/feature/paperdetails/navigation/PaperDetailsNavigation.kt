package com.etatech.hashiya.feature.paperdetails.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.paperdetails.PaperDetailsScreen
import kotlinx.serialization.Serializable

/** A saved paper's details and notes. The property name is the ViewModel's ARG_OPEN_ALEX_ID. */
@Serializable
data class PaperDetailsRoute(val openAlexId: String)

fun NavController.navigateToPaperDetails(openAlexId: String) = navigate(PaperDetailsRoute(openAlexId))

fun NavGraphBuilder.paperDetailsScreen(onBack: () -> Unit, onRemove: (openAlexId: String) -> Unit) {
    composable<PaperDetailsRoute> { PaperDetailsScreen(onBack = onBack, onRemove = onRemove) }
}
