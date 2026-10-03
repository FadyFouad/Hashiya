package com.etatech.hashiya.navigation

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Esc goes back, even from a focused text field, but only when there is somewhere to go back to. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
class EscapeKeyTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var backs = 0

    private fun show(canGoBack: Boolean) = composeRule.setContent {
        HashiyaTheme {
            HashiyaNavigationSuite(
                currentTopLevel = TopLevelDestination.Library,
                selectedTopLevel = TopLevelDestination.Library,
                onSelect = {},
                onReselectFromSubScreen = {}
            ) { Screen(canGoBack) }
        }
    }

    @Composable
    private fun Screen(canGoBack: Boolean) {
        BackHandler(enabled = canGoBack) { backs++ }
        TextField(value = "", onValueChange = {}, modifier = Modifier.testTag("field"))
    }

    @Test
    fun escapeFromAFocusedFieldGoesBack() {
        show(canGoBack = true)
        val field = composeRule.onNodeWithTag("field")
        field.performClick()
        field.performKeyInput { pressKey(Key.Escape) }
        assertEquals(1, backs)
    }

    @Test
    fun escapeNeverClosesTheApp() {
        show(canGoBack = false)
        val field = composeRule.onNodeWithTag("field")
        field.performClick()
        field.performKeyInput { pressKey(Key.Escape) }
        composeRule.waitForIdle()
        assertEquals(0, backs)
        assertFalse(composeRule.activity.isFinishing)
    }
}
