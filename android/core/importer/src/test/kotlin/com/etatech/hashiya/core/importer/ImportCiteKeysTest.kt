package com.etatech.hashiya.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportCiteKeysTest {
    @Test
    fun `keys BibTeX accepts are kept as written`() {
        listOf("Devlin2019", "ronneberger_u-net_2015", "heDeepResidualLearning2016", "smith:2020", "doe/2001.a", "müller2019").forEach {
            assertEquals(it, ImportCiteKeys.sanitize(it))
        }
    }

    @Test
    fun `keys BibTeX can't take are dropped`() {
        listOf("a b", "a,b", "a{b", "a}b", "a\"b", "a#b", "a%b", "a'b", "a(b", "a)b", "a=b", "a\\b", "", null).forEach {
            assertNull(it, ImportCiteKeys.sanitize(it))
        }
    }

    @Test
    fun `a free key is kept, a taken one gets the first free suffix`() {
        assertEquals("smith2020", ImportCiteKeys.resolve("smith2020", setOf("doe2019")))
        assertEquals("smith2020a", ImportCiteKeys.resolve("smith2020", setOf("smith2020")))
        assertEquals("smith2020c", ImportCiteKeys.resolve("smith2020", setOf("smith2020", "smith2020a", "smith2020b")))
        val all = setOf("k") + ('a'..'z').map { "k$it" }
        assertEquals("kaa", ImportCiteKeys.resolve("k", all))
    }

    @Test
    fun `keys are compared exactly, like the unique index`() {
        assertEquals("Smith2020", ImportCiteKeys.resolve("Smith2020", setOf("smith2020")))
    }
}
