package com.etatech.hashiya.navigation

import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.computeWindowSizeClass
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationTypeTest {
    private fun typeAt(widthDp: Int, heightDp: Int, onTopLevel: Boolean = true, tabletop: Boolean = false) = navigationTypeFor(
        WindowAdaptiveInfo(WindowSizeClass.BREAKPOINTS_V2.computeWindowSizeClass(widthDp, heightDp), Posture(isTabletop = tabletop)),
        onTopLevel
    )

    @Test
    fun phonesKeepTheBarOnTopLevelScreensOnly() {
        assertEquals(NavigationSuiteType.NavigationBar, typeAt(411, 891))
        assertEquals(NavigationSuiteType.None, typeAt(411, 891, onTopLevel = false))
    }

    @Test
    fun landscapePhonesAndTabletopKeepTheBar() {
        assertEquals(NavigationSuiteType.NavigationBar, typeAt(891, 411))
        assertEquals(NavigationSuiteType.None, typeAt(891, 411, onTopLevel = false))
        assertEquals(NavigationSuiteType.NavigationBar, typeAt(841, 900, tabletop = true))
    }

    @Test
    fun railFromMediumWidthOnEveryScreen() {
        assertEquals(NavigationSuiteType.WideNavigationRailCollapsed, typeAt(600, 900))
        assertEquals(NavigationSuiteType.WideNavigationRailCollapsed, typeAt(1199, 900, onTopLevel = false))
    }

    @Test
    fun theRailStartsCompactAtEveryWidth() {
        assertEquals(NavigationSuiteType.WideNavigationRailCollapsed, typeAt(1200, 800))
        assertEquals(NavigationSuiteType.WideNavigationRailCollapsed, typeAt(1600, 1000, onTopLevel = false))
    }
}
