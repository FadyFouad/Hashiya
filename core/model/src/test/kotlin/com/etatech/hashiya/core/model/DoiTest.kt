package com.etatech.hashiya.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DoiTest {
    @Test
    fun stripsDoiOrgUrlAndLowercases() {
        assertEquals("10.48550/arxiv.1706.03762", normalizeDoi("https://doi.org/10.48550/arXiv.1706.03762"))
    }

    @Test
    fun stripsOtherKnownPrefixes() {
        assertEquals("10.1000/xyz", normalizeDoi("http://dx.doi.org/10.1000/XYZ"))
        assertEquals("10.1000/xyz", normalizeDoi("doi:10.1000/xyz"))
    }

    @Test
    fun trimsWhitespace() {
        assertEquals("10.1000/xyz", normalizeDoi("  10.1000/xyz \n"))
    }

    @Test
    fun keepsBareDoi() {
        assertEquals("10.1000/xyz", normalizeDoi("10.1000/xyz"))
    }

    @Test
    fun rejectsValuesThatAreNotDois() {
        assertNull(normalizeDoi(""))
        assertNull(normalizeDoi("https://example.com/paper"))
        assertNull(normalizeDoi("10.1000"))
    }
}
