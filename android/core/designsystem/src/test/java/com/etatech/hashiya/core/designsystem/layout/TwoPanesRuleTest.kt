package com.etatech.hashiya.core.designsystem.layout

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.geometry.Rect
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class TwoPanesRuleTest {
    private fun hinge(vertical: Boolean, separating: Boolean = true) =
        HingeInfo(Rect(0f, 0f, 1f, 1f), isFlat = !separating, isVertical = vertical, isSeparating = separating, isOccluding = false)

    private fun twoPanesAt(widthDp: Int, heightDp: Int, vararg hinges: HingeInfo) = twoPanesFor(
        WindowAdaptiveInfo(WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp, heightDp), Posture(hingeList = hinges.toList()))
    )

    @Test
    fun widthDecidesWithoutAHinge() {
        assertEquals(false, twoPanesAt(411, 891))
        assertEquals(false, twoPanesAt(700, 900))
        assertEquals(true, twoPanesAt(1000, 800))
        assertEquals(false, twoPanesAt(900, 411))
    }

    @Test
    fun aSplittingVerticalHingeGivesTwoPanesFromMediumWidth() {
        assertEquals(true, twoPanesAt(700, 900, hinge(vertical = true)))
    }

    @Test
    fun aFlatFoldOrAHorizontalHingeChangesNothing() {
        assertEquals(false, twoPanesAt(700, 900, hinge(vertical = true, separating = false)))
        assertEquals(false, twoPanesAt(700, 900, hinge(vertical = false)))
    }

    @Test
    fun compactWindowsStayOnePaneEvenWithAHinge() {
        assertEquals(false, twoPanesAt(500, 800, hinge(vertical = true)))
    }
}
