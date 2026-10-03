package com.etatech.hashiya.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import com.etatech.hashiya.core.designsystem.layout.LayoutClass

/**
 * Compact and short windows (phones, landscape phones, tabletop) keep the navigation bar, shown on the
 * top-level screens only. From medium width the wide rail stays on every screen, so Details, Reader and
 * Settings don't lose the app's navigation; it expands with labels from 1200dp.
 */
internal fun navigationTypeFor(adaptiveInfo: WindowAdaptiveInfo, onTopLevel: Boolean): NavigationSuiteType {
    val sizeClass = adaptiveInfo.windowSizeClass
    val layoutClass = LayoutClass.from(sizeClass)
    val usesBar = layoutClass == LayoutClass.Compact ||
        adaptiveInfo.windowPosture.isTabletop ||
        !sizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)
    return when {
        usesBar && onTopLevel -> NavigationSuiteType.NavigationBar
        usesBar -> NavigationSuiteType.None
        layoutClass == LayoutClass.Large -> NavigationSuiteType.WideNavigationRailExpanded
        else -> NavigationSuiteType.WideNavigationRailCollapsed
    }
}

/**
 * The app's navigation around [content]. [currentTopLevel] is null on sub-screens (Details, Reader, Settings);
 * [selectedTopLevel] is then the tab they were opened from.
 */
@Composable
internal fun HashiyaNavigationSuite(
    currentTopLevel: TopLevelDestination?,
    selectedTopLevel: TopLevelDestination,
    onSelect: (TopLevelDestination) -> Unit,
    onReselectFromSubScreen: (TopLevelDestination) -> Unit,
    content: @Composable () -> Unit
) {
    val navigationType = navigationTypeFor(currentWindowAdaptiveInfoV2(), onTopLevel = currentTopLevel != null)
    NavigationSuiteScaffold(
        navigationItems = {
            TopLevelDestination.entries.forEach { topLevel ->
                NavigationSuiteItem(
                    selected = topLevel == selectedTopLevel,
                    onClick = {
                        if (topLevel == selectedTopLevel && currentTopLevel == null) {
                            onReselectFromSubScreen(topLevel)
                        } else {
                            onSelect(topLevel)
                        }
                    },
                    icon = { Icon(topLevel.icon, contentDescription = null) },
                    label = { Text(stringResource(topLevel.labelRes)) },
                    navigationSuiteType = navigationType
                )
            }
        },
        navigationSuiteType = navigationType,
        content = content
    )
}
