package com.etatech.hashiya.crash

import android.app.Application
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.etatech.hashiya.core.crash.CrashKey
import com.etatech.hashiya.core.testing.FakeCrashReporter
import com.etatech.hashiya.feature.library.navigation.LibraryRoute
import com.etatech.hashiya.feature.paperdetails.navigation.PaperDetailsRoute
import com.etatech.hashiya.feature.reader.navigation.ReaderRoute
import com.etatech.hashiya.feature.search.navigation.SearchRoute
import com.etatech.hashiya.feature.settings.navigation.RestoreRoute
import com.etatech.hashiya.feature.settings.navigation.SettingsRoute
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ScreenKeyTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val reporter = FakeCrashReporter()
    private lateinit var navController: NavHostController

    private fun setUpApp() {
        composeRule.setContent {
            navController = rememberNavController()
            ReportScreens(navController, reporter)
            NavHost(navController, startDestination = LibraryRoute) {
                composable<LibraryRoute> { Text("Library") }
                composable<SearchRoute> { Text("Search") }
                composable<PaperDetailsRoute> { Text("Details") }
                composable<ReaderRoute> { Text("Reader") }
                composable<SettingsRoute> { Text("Settings") }
                composable<RestoreRoute> { Text("Restore") }
            }
        }
        composeRule.waitForIdle()
    }

    private fun go(route: Any) {
        composeRule.runOnUiThread { navController.navigate(route) }
        composeRule.waitForIdle()
    }

    private fun screens() = reporter.keyHistory.filter { it.first == CrashKey.Screen }.map { it.second }

    @Test
    fun libraryThenDetailsThenSettingsSetTheScreenInOrder() {
        setUpApp()
        go(PaperDetailsRoute("W1"))
        go(SettingsRoute)

        assertEquals(listOf("library", "details", "settings"), screens())
    }

    @Test
    fun searchReaderAndRestoreHaveTheirOwnValues() {
        setUpApp()
        go(SearchRoute())
        go(ReaderRoute("W1"))
        go(RestoreRoute("content://docs/backup.hashiya"))

        assertEquals(listOf("library", "search", "reader", "restore"), screens())
    }
}
