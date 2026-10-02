package com.etatech.hashiya.core.designsystem.component

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UpdateRequiredScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsTheMessageAndTheUpdateButtonCallsBack() {
        var updates = 0
        composeRule.setContent { HashiyaTheme { UpdateRequiredScreen(onUpdate = { updates++ }) } }

        composeRule.onNodeWithText("Update required").assertIsDisplayed()
        composeRule.onNodeWithText(
            "This version of Hashiya is no longer supported. Update to keep using it. Your saved papers stay on your device."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Update").performClick()
        assertEquals(1, updates)
    }
}
