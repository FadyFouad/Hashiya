package com.etatech.hashiya.feature.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import com.etatech.hashiya.core.testing.PHONE_QUALIFIERS
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// PHONE_QUALIFIERS gives a full-height phone viewport; Robolectric's tiny default window would
// place the last language row below the fold, where a real performClick() touch cannot land.
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = PHONE_QUALIFIERS)
class SettingsContentTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val events = mutableListOf<String>()

    private fun show(state: SettingsUiState) = composeRule.setContent {
        HashiyaTheme {
            SettingsContent(
                uiState = state,
                onBack = { events += "back" },
                onKeyInputChange = { events += "input:$it" },
                onSaveKey = { events += "save" },
                onResetKey = { events += "reset" },
                onLanguageSelected = { events += "language:$it" }
            )
        }
    }

    @Test
    fun builtInKeyStatusAndResetDisabled() {
        show(SettingsUiState(usingUserKey = false))

        composeRule.onNodeWithText("Using built-in key").assertIsDisplayed()
        composeRule.onNodeWithText("Reset to built-in").assertIsNotEnabled()
    }

    @Test
    fun saveAndResetInvokeCallbacks() {
        show(SettingsUiState(usingUserKey = true, keyInput = "abc"))

        composeRule.onNodeWithText("Using your key").assertIsDisplayed()
        composeRule.onNodeWithText("Save").performClick()
        composeRule.onNodeWithText("Reset to built-in").performClick()
        assertEquals(listOf("save", "reset"), events)
    }

    @Test
    fun choosingArabicInvokesCallback() {
        show(SettingsUiState())

        composeRule.onNodeWithText("العربية").performClick()
        assertEquals(listOf("language:Arabic"), events)
    }

    @Test
    fun backInvokesCallback() {
        show(SettingsUiState())

        composeRule.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), events)
    }
}
