package com.etatech.hashiya.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class WheelZoomTest {
    @Test
    fun wheelUpZoomsInAndDownZoomsOut() {
        assertEquals(1.1f, wheelZoom(1f, scrollY = -1f), 0.001f)
        assertEquals(2f, wheelZoom(2.2f, scrollY = 1f), 0.001f)
        assertEquals(1.5f, wheelZoom(1.5f, scrollY = 0f), 0.001f)
    }

    @Test
    fun zoomStaysInRange() {
        assertEquals(MIN_ZOOM, wheelZoom(MIN_ZOOM, scrollY = 1f), 0.001f)
        assertEquals(MAX_ZOOM, wheelZoom(MAX_ZOOM, scrollY = -1f), 0.001f)
    }
}
