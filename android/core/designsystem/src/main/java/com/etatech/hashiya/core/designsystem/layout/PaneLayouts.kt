package com.etatech.hashiya.core.designsystem.layout

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.ThreePaneScaffoldValue
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * True when two panes sit side by side: expanded width (840dp and up), except on short windows, so a large
 * phone in landscape keeps one pane, as on any phone.
 */
@Composable
fun showsTwoPanes(): Boolean = currentLayoutClass().isAtLeastExpanded && !isShortWindow()

// Both panes always shown: callers use these only when showsTwoPanes(), and keep their one-pane layout otherwise.
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
private val BothPanes = ThreePaneScaffoldValue(
    primary = PaneAdaptedValue.Expanded,
    secondary = PaneAdaptedValue.Expanded,
    tertiary = PaneAdaptedValue.Hidden
)

/** [list] beside [detail], with Material's pane widths, spacing and hinge avoidance. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun ListDetailPanes(list: @Composable () -> Unit, detail: @Composable () -> Unit, modifier: Modifier = Modifier) {
    ListDetailPaneScaffold(
        directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()),
        value = BothPanes,
        listPane = { AnimatedPane { list() } },
        detailPane = { AnimatedPane { detail() } },
        modifier = modifier
    )
}

/** [main] with [supporting] beside it (notes next to a document). */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun SupportingPanes(main: @Composable () -> Unit, supporting: @Composable () -> Unit, modifier: Modifier = Modifier) {
    SupportingPaneScaffold(
        directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2()),
        value = BothPanes,
        mainPane = { AnimatedPane { main() } },
        supportingPane = { AnimatedPane { supporting() } },
        modifier = modifier
    )
}
