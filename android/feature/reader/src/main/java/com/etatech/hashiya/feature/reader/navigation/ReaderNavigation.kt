package com.etatech.hashiya.feature.reader.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.etatech.hashiya.feature.reader.ReaderScreen
import kotlinx.serialization.Serializable

/** A saved paper's PDF. The property name is the ViewModel's ARG_OPEN_ALEX_ID. */
@Serializable
data class ReaderRoute(val openAlexId: String)

fun NavController.navigateToReader(openAlexId: String) = navigate(ReaderRoute(openAlexId))

fun NavGraphBuilder.readerScreen(onBack: () -> Unit) {
    composable<ReaderRoute> { ReaderScreen(onBack = onBack) }
}
