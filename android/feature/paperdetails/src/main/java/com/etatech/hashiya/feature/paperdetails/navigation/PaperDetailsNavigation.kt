package com.etatech.hashiya.feature.paperdetails.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.etatech.hashiya.feature.paperdetails.PaperDetailsScreen
import kotlinx.serialization.Serializable

/** A saved paper's details and notes, as a screen of its own (one-pane windows, and from Search). */
@Serializable
data class PaperDetailsRoute(val openAlexId: String)

fun NavController.navigateToPaperDetails(openAlexId: String) = navigate(PaperDetailsRoute(openAlexId))

/** [onReadPdf] opens the reader for this paper's PDF, once Details has saved its typed notes. */
fun NavGraphBuilder.paperDetailsScreen(
    onBack: () -> Unit,
    onRemove: (openAlexId: String) -> Unit,
    onReadPdf: (openAlexId: String) -> Unit = {}
) {
    composable<PaperDetailsRoute> { entry ->
        PaperDetailsScreen(
            openAlexId = entry.toRoute<PaperDetailsRoute>().openAlexId,
            onBack = onBack,
            onRemove = onRemove,
            onReadPdf = onReadPdf
        )
    }
}
