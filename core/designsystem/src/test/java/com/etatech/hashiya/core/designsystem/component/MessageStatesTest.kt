package com.etatech.hashiya.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageStatesTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateActionInvokesCallback() {
        var clicks = 0
        composeRule.setContent {
            HashiyaTheme {
                EmptyState(HashiyaIcons.Library, "Nothing here", "Add something", actionLabel = "Go", onAction = { clicks++ })
            }
        }

        composeRule.onNodeWithText("Nothing here").assertIsDisplayed()
        composeRule.onNodeWithText("Go").performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun emptyStateWithoutActionShowsNoButton() {
        composeRule.setContent {
            HashiyaTheme { EmptyState(HashiyaIcons.Library, "Nothing here", "Add something") }
        }

        composeRule.onNodeWithText("Go").assertDoesNotExist()
    }

    @Test
    fun errorStateActionInvokesCallback() {
        var clicks = 0
        composeRule.setContent {
            HashiyaTheme { ErrorState("Offline", "Check your connection", "Retry", onAction = { clicks++ }) }
        }

        composeRule.onNodeWithText("Retry").performClick()
        assertEquals(1, clicks)
    }
}
