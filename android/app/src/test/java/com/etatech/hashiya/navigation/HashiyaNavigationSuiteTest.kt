package com.etatech.hashiya.navigation

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class HashiyaNavigationSuiteTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val selected = mutableListOf<TopLevelDestination>()
    private val reselected = mutableListOf<TopLevelDestination>()

    /** ForcedSize sets the layout size; WindowSize sets the window size class the navigation reads. */
    private fun showAt(widthDp: Int, heightDp: Int, currentTopLevel: TopLevelDestination?) {
        val size = DpSize(widthDp.dp, heightDp.dp)
        composeRule.setContent {
            DeviceConfigurationOverride(
                DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.WindowSize(size)
            ) {
                HashiyaTheme {
                    HashiyaNavigationSuite(
                        currentTopLevel = currentTopLevel,
                        selectedTopLevel = currentTopLevel ?: TopLevelDestination.Search,
                        onSelect = { selected += it },
                        onReselectFromSubScreen = { reselected += it }
                    ) { Box(Modifier.fillMaxSize().testTag(CONTENT)) }
                }
            }
        }
    }

    private fun bounds(text: String) = composeRule.onNodeWithText(text).getUnclippedBoundsInRoot()
    private fun contentStart() = composeRule.onNodeWithTag(CONTENT).getUnclippedBoundsInRoot().left

    private fun assertBar() {
        assertEquals(bounds("Library").top, bounds("Search").top)
        assertEquals(0.dp, contentStart())
    }

    private fun assertRail() = assertEquals(bounds("Library").left, bounds("Search").left)

    @Test
    fun compactShowsTheBarOnTopLevelScreens() {
        showAt(411, 891, TopLevelDestination.Library)
        assertBar()
    }

    @Test
    fun compactHidesNavigationOnSubScreens() {
        showAt(411, 891, currentTopLevel = null)
        composeRule.onNodeWithText("Library").assertDoesNotExist()
        assertEquals(0.dp, contentStart())
    }

    @Test
    fun shortWindowsKeepTheBar() {
        showAt(891, 411, TopLevelDestination.Library)
        assertBar()
    }

    @Test
    fun mediumShowsTheRailOnSubScreens() {
        showAt(700, 1000, currentTopLevel = null)
        assertRail()
        assertTrue(contentStart() > 0.dp)
    }

    @Test
    fun expandedShowsTheCollapsedRail() {
        showAt(1000, 800, TopLevelDestination.Search)
        assertRail()
        assertTrue(contentStart() in 1.dp..120.dp)
    }

    @Test
    fun largeShowsTheExpandedRail() {
        showAt(1280, 800, TopLevelDestination.Library)
        composeRule.onNodeWithText("Library").assertIsDisplayed()
        assertRail()
        assertTrue(contentStart() > 120.dp)
    }

    @Test
    fun railReselectOnASubScreenGoesBackToTheTabRoot() {
        showAt(1000, 800, currentTopLevel = null)
        composeRule.onNodeWithText("Search").performClick()
        composeRule.onNodeWithText("Library").performClick()
        assertEquals(listOf(TopLevelDestination.Search), reselected)
        assertEquals(listOf(TopLevelDestination.Library), selected)
    }

    private companion object {
        const val CONTENT = "content"
    }
}
