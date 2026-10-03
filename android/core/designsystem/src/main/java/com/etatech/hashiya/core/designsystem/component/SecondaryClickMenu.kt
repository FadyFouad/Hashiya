package com.etatech.hashiya.core.designsystem.component

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection

/**
 * A context menu for [content] that a right-click (mouse or trackpad) opens at the pointer. Touch never opens it, so
 * phones behave as before. [menu] gets a close callback for its items.
 */
@Composable
fun SecondaryClickMenu(
    menu: @Composable ColumnScope.(close: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var clickedAt by remember { mutableStateOf<Offset?>(null) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    Box(
        modifier
            .onSizeChanged { size = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        clickedAt = event.changes.first().position
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        content()
        val position = clickedAt
        DropdownMenu(
            expanded = position != null,
            onDismissRequest = { clickedAt = null },
            // The menu hangs from the anchor's bottom start corner; move it up and across to the pointer.
            offset = if (position == null) {
                DpOffset.Zero
            } else {
                with(density) {
                    val fromStart = if (layoutDirection == LayoutDirection.Rtl) size.width - position.x else position.x
                    DpOffset(fromStart.toDp(), (position.y - size.height).toDp())
                }
            }
        ) {
            menu { clickedAt = null }
        }
    }
}
