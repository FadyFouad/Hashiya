package com.etatech.hashiya.core.designsystem.layout

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass
import androidx.window.core.layout.WindowSizeClass.Companion.HEIGHT_DP_MEDIUM_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_EXPANDED_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_LARGE_LOWER_BOUND
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND

/**
 * The app's one set of width breakpoints (Material: 600 / 840 / 1200dp). Layouts read this,
 * never the device type or orientation: a tablet can run the app in a phone-sized pane.
 */
enum class LayoutClass {
    Compact,
    Medium,
    Expanded,
    Large;

    val isAtLeastMedium: Boolean get() = this >= Medium
    val isAtLeastExpanded: Boolean get() = this >= Expanded

    companion object {
        fun from(sizeClass: WindowSizeClass): LayoutClass = when {
            sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_LARGE_LOWER_BOUND) -> Large
            sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_EXPANDED_LOWER_BOUND) -> Expanded
            sizeClass.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND) -> Medium
            else -> Compact
        }
    }
}

/** The current window's [LayoutClass]; recomposes on resize, fold and rotation. */
@Composable
fun currentLayoutClass(): LayoutClass = LayoutClass.from(currentWindowAdaptiveInfoV2().windowSizeClass)

/** True for short windows (landscape phones, wide foldables): adjust the layout, don't pick it. */
@Composable
fun isShortWindow(): Boolean = !currentWindowAdaptiveInfoV2().windowSizeClass.isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)
