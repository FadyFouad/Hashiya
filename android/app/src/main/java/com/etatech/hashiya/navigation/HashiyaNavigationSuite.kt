package com.etatech.hashiya.navigation

import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailDefaults
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuite
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldLayout
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import com.etatech.hashiya.R
import com.etatech.hashiya.core.designsystem.icon.HashiyaIcons
import com.etatech.hashiya.core.designsystem.layout.LayoutClass
import kotlinx.coroutines.launch

/**
 * Compact and short windows (phones, landscape phones, tabletop) keep the navigation bar, shown on the
 * top-level screens only. From medium width the wide rail stays on every screen, so Details, Reader and
 * Settings don't lose the app's navigation. The rail starts compact; its menu button expands it.
 */
internal fun navigationTypeFor(adaptiveInfo: WindowAdaptiveInfo, onTopLevel: Boolean): NavigationSuiteType {
    val sizeClass = adaptiveInfo.windowSizeClass
    val usesBar = LayoutClass.from(sizeClass) == LayoutClass.Compact ||
        adaptiveInfo.windowPosture.isTabletop ||
        !sizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)
    return when {
        usesBar && onTopLevel -> NavigationSuiteType.NavigationBar
        usesBar -> NavigationSuiteType.None
        else -> NavigationSuiteType.WideNavigationRailCollapsed
    }
}

/**
 * The app's navigation around [content]. [currentTopLevel] is null on sub-screens (Details, Reader, Settings);
 * [selectedTopLevel] is then the tab they were opened from.
 *
 * The scaffold's layout with the suite drawn here: the bar is the library's, the rail is ours, so one rail state
 * animates between compact and expanded (the library draws those as two rails, and would jump between them).
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
    val railState = rememberWideNavigationRailState()
    val onItemClick: (TopLevelDestination) -> Unit = { topLevel ->
        if (topLevel == selectedTopLevel && currentTopLevel == null) onReselectFromSubScreen(topLevel) else onSelect(topLevel)
    }
    Surface(
        modifier = Modifier.escapeGoesBack(),
        color = NavigationSuiteScaffoldDefaults.containerColor,
        contentColor = NavigationSuiteScaffoldDefaults.contentColor
    ) {
        NavigationSuiteScaffoldLayout(
            navigationSuite = {
                if (navigationType == NavigationSuiteType.WideNavigationRailCollapsed) {
                    HashiyaRail(railState, selectedTopLevel, onItemClick)
                } else {
                    NavigationSuite(navigationSuiteType = navigationType) {
                        TopLevelDestination.entries.forEach { topLevel ->
                            NavigationSuiteItem(
                                selected = topLevel == selectedTopLevel,
                                onClick = { onItemClick(topLevel) },
                                icon = { Icon(topLevel.icon, contentDescription = null) },
                                label = { Text(stringResource(topLevel.labelRes)) },
                                navigationSuiteType = navigationType
                            )
                        }
                    }
                }
            },
            navigationSuiteType = navigationType,
            content = { Box(Modifier.consumeWindowInsets(navigationInsets(navigationType))) { content() } }
        )
    }
}

/** The wide rail with a menu button that expands it (labels beside the icons) and collapses it again. */
@Composable
private fun HashiyaRail(state: WideNavigationRailState, selectedTopLevel: TopLevelDestination, onItemClick: (TopLevelDestination) -> Unit) {
    val scope = rememberCoroutineScope()
    val expanded = state.targetValue == WideNavigationRailValue.Expanded
    WideNavigationRail(
        state = state,
        header = {
            IconButton(onClick = { scope.launch { state.toggle() } }, modifier = Modifier.padding(start = 24.dp)) {
                Icon(
                    if (expanded) HashiyaIcons.MenuOpen else HashiyaIcons.Menu,
                    contentDescription = stringResource(if (expanded) R.string.nav_collapse else R.string.nav_expand)
                )
            }
        }
    ) {
        TopLevelDestination.entries.forEach { topLevel ->
            WideNavigationRailItem(
                selected = topLevel == selectedTopLevel,
                onClick = { onItemClick(topLevel) },
                icon = { Icon(topLevel.icon, contentDescription = null) },
                label = { Text(stringResource(topLevel.labelRes)) },
                railExpanded = expanded
            )
        }
    }
}

/** The system bar insets the navigation takes, so the content doesn't pad for them again. */
@Composable
private fun navigationInsets(type: NavigationSuiteType): WindowInsets = when (type) {
    NavigationSuiteType.WideNavigationRailCollapsed -> WideNavigationRailDefaults.windowInsets.only(WindowInsetsSides.Start)
    NavigationSuiteType.NavigationBar -> NavigationBarDefaults.windowInsets.only(WindowInsetsSides.Bottom)
    else -> WindowInsets(0)
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
