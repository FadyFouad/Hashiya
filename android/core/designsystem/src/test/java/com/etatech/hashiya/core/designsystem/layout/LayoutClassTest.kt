package com.etatech.hashiya.core.designsystem.layout

import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class LayoutClassTest {
    private fun at(widthDp: Int) = LayoutClass.from(WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp, 800))

    @Test
    fun mapsWidthsToMaterialBreakpoints() {
        assertEquals(LayoutClass.Compact, at(599))
        assertEquals(LayoutClass.Medium, at(600))
        assertEquals(LayoutClass.Medium, at(839))
        assertEquals(LayoutClass.Expanded, at(840))
        assertEquals(LayoutClass.Expanded, at(1199))
        assertEquals(LayoutClass.Large, at(1200))
        assertEquals(LayoutClass.Large, at(1600))
    }
}
