package com.etatech.hashiya.update

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.analytics.NoOpAnalytics
import com.etatech.hashiya.core.crash.NoOpCrashReporter
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.model.RequiredUpdate
import com.etatech.hashiya.navigation.HashiyaApp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Plain Application: with an update required, HashiyaApp never builds the Hilt-backed screens. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpdateGateTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun aRequiredUpdateShowsOnlyTheUpdateScreen() {
        var opened: String? = null
        composeRule.setContent {
            HashiyaTheme {
                HashiyaApp(
                    crashReporter = NoOpCrashReporter,
                    analytics = NoOpAnalytics,
                    requiredUpdate = RequiredUpdate(STORE),
                    onOpenStore = { opened = it }
                )
            }
        }

        composeRule.onNodeWithText("Update required").assertIsDisplayed()
        composeRule.onNodeWithText("Library").assertDoesNotExist()
        composeRule.onNodeWithText("Search").assertDoesNotExist()

        composeRule.onNodeWithText("Update").performClick()
        assertEquals(STORE, opened)
    }

    private companion object {
        const val STORE = "https://play.google.com/store/apps/details?id=com.etatech.hashiya"
    }
}
