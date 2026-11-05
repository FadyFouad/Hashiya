package com.etatech.hashiya.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextTest {
    @Test
    fun `collapse joins every kind of whitespace and trims`() {
        assertEquals("a b c", collapse(" a\t\tb\r\n\u000B\u000C\u00A0c "))
    }

    @Test
    fun `collapse gives null for blank text`() {
        assertNull(collapse(" \n\t"))
        assertNull(collapse(null))
    }

    @Test
    fun `collapse leaves other Unicode spaces alone, so both platforms agree`() {
        assertEquals("a\u2003b", collapse("a\u2003b"))
    }

    @Test
    fun `firstYear takes the first run of exactly four digits`() {
        assertEquals(2015, firstYear("2015/05//"))
        assertEquals(2016, firstYear("2016-06"))
        assertEquals(2020, firstYear("Spring 2020a"))
        assertEquals(1999, firstYear("12345 or 1999"))
        assertNull(firstYear("in press"))
        assertNull(firstYear("123"))
        assertNull(firstYear(null))
    }

    @Test
    fun `splitPages splits on the first run of hyphens or an en dash`() {
        assertEquals("770" to "778", splitPages("770--778"))
        assertEquals("436" to "444", splitPages("436-444"))
        assertEquals("12" to "34", splitPages("12 – 34"))
        assertEquals("e1001" to null, splitPages("e1001"))
        assertEquals("12+" to null, splitPages("12+"))
        assertEquals(null to null, splitPages(null))
        assertEquals(null to "9", splitPages("-9"))
    }

    @Test
    fun `cleanRaw drops braces and the escapes a DOI or URL can carry`() {
        assertEquals("10.1007/978-3-319-24574-4_28", cleanRaw("10.1007/978-3-319-24574-4\\_28"))
        assertEquals("https://x.org/a_b?c=1&d=50%", cleanRaw("{https://x.org/a{\\_}b?c=1\\&d=50\\%}"))
        assertEquals("a#b", cleanRaw("a\\#b"))
    }
}
