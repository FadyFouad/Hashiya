package com.etatech.hashiya.feature.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

internal const val MIN_ZOOM = 1f
internal const val MAX_ZOOM = 4f
internal const val DOUBLE_TAP_ZOOM = 2.5f
internal const val PILL_HIDE_DELAY_MS = 1_500L

/**
 * The PDF's pages in a vertical list, with pinch zoom (1x to 4x), double-tap (1x / 2.5x), horizontal pan while zoomed, and the
 * page pill. While a pinch runs the existing bitmaps scale; the view model renders sharper ones once it ends.
 */
@Composable
internal fun PdfPages(
    ready: ReaderState.Ready,
    pages: Map<Int, Bitmap>,
    onViewport: (firstVisible: Int, lastVisible: Int, widthPx: Int, zoom: Float) -> Unit,
    onPageChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    pillAlwaysVisible: Boolean = false
) {
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ready.startPage)
    var scale by remember { mutableFloatStateOf(MIN_ZOOM) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var settledScale by remember { mutableFloatStateOf(MIN_ZOOM) }
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex } }

    BoxWithConstraints(modifier.fillMaxSize().clipToBounds()) {
        val widthPx = constraints.maxWidth
        fun panLimit() = widthPx * (scale - 1f) / 2f

        LaunchedEffect(listState, widthPx, settledScale) {
            snapshotFlow { listState.layoutInfo.visibleItemsInfo.map { it.index } }
                .distinctUntilChanged()
                .collect { visible -> if (visible.isNotEmpty()) onViewport(visible.first(), visible.last(), widthPx, settledScale) }
        }
        LaunchedEffect(listState) {
            snapshotFlow { listState.firstVisibleItemIndex }.distinctUntilChanged().collect { onPageChanged(it) }
        }

        // Pages are never mirrored: a PDF reads the way it was typeset, whatever the app's language.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .pinchToZoom(
                        onZoom = { zoomChange, pan ->
                            scale = (scale * zoomChange).coerceIn(MIN_ZOOM, MAX_ZOOM)
                            offsetX = (offsetX + pan.x).coerceIn(-panLimit(), panLimit())
                        },
                        onEnd = { settledScale = scale }
                    )
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            scale = if (scale > MIN_ZOOM) MIN_ZOOM else DOUBLE_TAP_ZOOM
                            offsetX = 0f
                            settledScale = scale
                        })
                    }
                    .draggable(
                        orientation = Orientation.Horizontal,
                        enabled = scale > MIN_ZOOM,
                        state = rememberDraggableState { delta -> offsetX = (offsetX + delta).coerceIn(-panLimit(), panLimit()) }
                    )
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        transformOrigin = TransformOrigin(0.5f, 0f)
                    }
            ) {
                items(ready.pageCount, key = { it }) { index ->
                    PdfPage(index, ready.pageCount, ready.pageAspectRatios[index], pages[index])
                }
            }
        }

        var pillVisible by remember { mutableStateOf(pillAlwaysVisible) }
        LaunchedEffect(listState.isScrollInProgress) {
            if (listState.isScrollInProgress) {
                pillVisible = true
            } else if (!pillAlwaysVisible) {
                delay(PILL_HIDE_DELAY_MS)
                pillVisible = false
            }
        }
        AnimatedVisibility(
            visible = pillVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            ) {
                Text(
                    stringResource(R.string.reader_page, currentPage + 1, ready.pageCount),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun PdfPage(index: Int, pageCount: Int, aspectRatio: Float, bitmap: Bitmap?) {
    val label = stringResource(R.string.reader_page, index + 1, pageCount)
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .aspectRatio(1f / aspectRatio)
            .shadow(1.dp)
            .background(Color.White)
            .semantics { contentDescription = label }
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Two-finger pinch only: one finger is left to the list's scrolling and the horizontal pan, so scrolling never fights the zoom.
 */
private fun Modifier.pinchToZoom(onZoom: (zoomChange: Float, pan: Offset) -> Unit, onEnd: () -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zooming = false
        do {
            val event = awaitPointerEvent()
            if (event.changes.count { it.pressed } >= 2) {
                zooming = true
                onZoom(event.calculateZoom(), event.calculatePan())
                event.changes.forEach { if (it.positionChanged()) it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (zooming) onEnd()
    }
}
