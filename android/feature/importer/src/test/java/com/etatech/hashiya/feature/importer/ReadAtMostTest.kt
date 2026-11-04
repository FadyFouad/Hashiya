package com.etatech.hashiya.feature.importer

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadAtMostTest {
    @Test
    fun `a small stream is read whole`() {
        val bytes = "@misc{k}".toByteArray()
        assertArrayEquals(bytes, ByteArrayInputStream(bytes).readAtMost(100))
    }

    @Test
    fun `a large stream stops at the limit`() {
        val bytes = ByteArray(300_000) { it.toByte() }
        val read = ByteArrayInputStream(bytes).readAtMost(200_001)
        assertEquals(200_001, read.size)
        assertArrayEquals(bytes.copyOf(200_001), read)
    }
}
