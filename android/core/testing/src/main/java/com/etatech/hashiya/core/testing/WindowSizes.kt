package com.etatech.hashiya.core.testing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.WindowSize
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.etatech.hashiya.core.designsystem.theme.HashiyaTheme

/**
 * A screen larger than every [TestWindow], for `@Config(qualifiers = ROOMY_QUALIFIERS)` on window-size tests:
 * on a smaller screen ForcedSize shrinks the density to fit, and content falls off the bottom.
 */
const val ROOMY_QUALIFIERS = "w1300dp-h1100dp-mdpi"

/** Window sizes for layout tests: a phone, a portrait foldable or small tablet, a landscape tablet, a landscape phone. */
enum class TestWindow(val widthDp: Int, val heightDp: Int) {
    Compact(411, 891),
    Medium(700, 1000),
    Expanded(1000, 800),
    ShortExpanded(900, 411)
}

/**
 * Shows [content] in the Hashiya theme in a [window]-sized window. ForcedSize sets the layout size; WindowSize sets
 * the window size class that the adaptive layouts read (ForcedSize alone doesn't change it).
 */
fun ComposeContentTestRule.setContentInWindow(window: TestWindow, content: @Composable () -> Unit) =
    setContentInWindow(mutableStateOf(window), content)

/** As above, in a window that resizes when [window] changes, like a fold or a drag of a free-form window. */
fun ComposeContentTestRule.setContentInWindow(window: State<TestWindow>, content: @Composable () -> Unit) {
    setContent {
        val size = DpSize(window.value.widthDp.dp, window.value.heightDp.dp)
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size) then DeviceConfigurationOverride.WindowSize(size)) {
            HashiyaTheme { content() }
        }
    }
}
