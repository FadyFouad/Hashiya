package com.etatech.hashiya.core.designsystem.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.testing.ROOMY_QUALIFIERS
import com.etatech.hashiya.core.testing.TestWindow
import com.etatech.hashiya.core.testing.setContentInWindow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = ROOMY_QUALIFIERS)
class PaneLayoutsTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun twoPanesIn(window: TestWindow): Boolean {
        var twoPanes: Boolean? = null
        composeRule.setContentInWindow(window) { twoPanes = showsTwoPanes() }
        composeRule.waitForIdle()
        return checkNotNull(twoPanes)
    }

    @Test
    fun onePaneOnPhones() = assertEquals(false, twoPanesIn(TestWindow.Compact))

    @Test
    fun onePaneAtMediumWidth() = assertEquals(false, twoPanesIn(TestWindow.Medium))

    @Test
    fun twoPanesFromExpandedWidth() = assertEquals(true, twoPanesIn(TestWindow.Expanded))

    @Test
    fun onePaneOnLandscapePhones() = assertEquals(false, twoPanesIn(TestWindow.ShortExpanded))

    @Test
    fun centeredMaxWidthCapsAndCentersOnWideWindows() {
        composeRule.setContentInWindow(TestWindow.Expanded) {
            Box(Modifier.centeredMaxWidth(600.dp).height(10.dp).testTag("column"))
        }
        val bounds = composeRule.onNodeWithTag("column").getUnclippedBoundsInRoot()
        assertEquals(200f, bounds.left.value, PIXEL_ROUNDING)
        assertEquals(600f, (bounds.right - bounds.left).value, PIXEL_ROUNDING)
    }

    @Test
    fun centeredMaxWidthFillsNarrowWindows() {
        composeRule.setContentInWindow(TestWindow.Compact) {
            Box(Modifier.centeredMaxWidth().height(10.dp).testTag("column"))
        }
        val bounds = composeRule.onNodeWithTag("column").getUnclippedBoundsInRoot()
        assertEquals(0f, bounds.left.value, PIXEL_ROUNDING)
        assertEquals(411f, bounds.right.value, PIXEL_ROUNDING)
    }

    private companion object {
        // Sizes in dp land on whole pixels.
        const val PIXEL_ROUNDING = 1f
    }
}
