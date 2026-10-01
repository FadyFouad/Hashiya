package com.etatech.hashiya.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class RenderWidthTest {
    private val a4 = 297f / 210f

    @Test
    fun atOneTimesEveryPageIsScreenWidth() {
        assertEquals(1080, renderWidth(viewportWidth = 1080, zoom = 1f, aspectRatio = a4, visible = true))
        assertEquals(1080, renderWidth(viewportWidth = 1080, zoom = 1f, aspectRatio = a4, visible = false))
    }

    @Test
    fun zoomSharpensOnlyVisiblePagesAndStopsAtTwoAndAHalf() {
        assertEquals(2160, renderWidth(viewportWidth = 1080, zoom = 2f, aspectRatio = a4, visible = true))
        assertEquals(2700, renderWidth(viewportWidth = 1080, zoom = 4f, aspectRatio = a4, visible = true))
        assertEquals(1080, renderWidth(viewportWidth = 1080, zoom = 4f, aspectRatio = a4, visible = false))
    }

    @Test
    fun aVeryTallPageStaysWithinThePixelBudget() {
        // A 1:10 strip at 2.5x would be 2700 x 27000 pixels; the budget caps it.
        val width = renderWidth(viewportWidth = 1080, zoom = 4f, aspectRatio = 10f, visible = true)

        assertEquals(1095, width)
        assertEquals(true, width.toLong() * (width * 10f).toLong() <= MAX_PAGE_PIXELS)
    }
}
