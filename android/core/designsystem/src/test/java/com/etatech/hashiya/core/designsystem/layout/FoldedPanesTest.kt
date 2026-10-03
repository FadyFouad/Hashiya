package com.etatech.hashiya.core.designsystem.layout

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.window.layout.FoldingFeature.Orientation
import androidx.window.layout.FoldingFeature.State
import androidx.window.testing.layout.FoldingFeature
import androidx.window.testing.layout.TestWindowLayoutInfo
import androidx.window.testing.layout.WindowLayoutInfoPublisherRule
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A foldable half open in book posture at medium width: one pane each side of the hinge, nothing across it. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w700dp-h900dp-mdpi")
class FoldedPanesTest {
    private val publisher = WindowLayoutInfoPublisherRule()
    private val composeRule = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rules: TestRule = RuleChain.outerRule(publisher).around(composeRule)

    @Test
    fun panesSitOnEitherSideOfABookPostureHinge() {
        var twoPanes = false
        composeRule.setContent {
            HashiyaTheme {
                twoPanes = showsTwoPanes()
                ListDetailPanes(
                    list = { Box(Modifier.fillMaxSize().testTag("list")) },
                    detail = { Box(Modifier.fillMaxSize().testTag("detail")) }
                )
            }
        }
        val hinge = FoldingFeature(
            activity = composeRule.activity,
            size = 20,
            state = State.HALF_OPENED,
            orientation = Orientation.VERTICAL
        )
        publisher.overrideWindowLayoutInfo(TestWindowLayoutInfo(listOf(hinge)))
        composeRule.waitForIdle()

        assertTrue("two panes with a splitting hinge", twoPanes)
        val hingeLeftDp = hinge.bounds.left.toFloat()
        val hingeRightDp = hinge.bounds.right.toFloat()
        val list = composeRule.onNodeWithTag("list").getUnclippedBoundsInRoot()
        val detail = composeRule.onNodeWithTag("detail").getUnclippedBoundsInRoot()
        val (start, end) = if (list.left < detail.left) list to detail else detail to list
        assertTrue("first pane ends before the hinge: ${start.right} <= $hingeLeftDp", start.right.value <= hingeLeftDp + 1)
        assertTrue("second pane starts after the hinge: ${end.left} >= $hingeRightDp", end.left.value >= hingeRightDp - 1)
    }
}
