package com.etatech.hashiya.navigation

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
        modifier = Modifier.escapeGoesBack(),
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

/**
 * Esc goes back (Keyboard_Exit): it closes a pane, the sheet or the screen, even from a focused text field, which would
 * otherwise take the key. With nothing to go back to it does nothing, so it never closes the app. MainActivity does the
 * same for an Esc that arrives with nothing focused.
 */
@Composable
private fun Modifier.escapeGoesBack(): Modifier {
    val dispatcher = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    return onPreviewKeyEvent { event ->
        if (event.key != Key.Escape) return@onPreviewKeyEvent false
        if (event.type == KeyEventType.KeyUp && dispatcher?.hasEnabledCallbacks() == true) dispatcher.onBackPressed()
        true
    }
}
