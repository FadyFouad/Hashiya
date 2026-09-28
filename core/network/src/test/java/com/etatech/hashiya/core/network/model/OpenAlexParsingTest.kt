package com.etatech.hashiya.core.network.model

import com.etatech.hashiya.core.network.OpenAlexJson
import com.etatech.hashiya.core.network.readFixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAlexParsingTest {
    private val response = OpenAlexJson.decodeFromString<NetworkWorksResponse>(readFixture("works_page.json"))

    @Test
    fun parsesMeta() {
        assertEquals(48210L, response.meta.count)
        assertEquals("IlsxMDAuMCwgJ1czMTc3ODI4OTA5J10i", response.meta.nextCursor)
        assertEquals(2, response.results.size)
    }

    @Test
    fun parsesCompleteWork() {
        val work = response.results[0]
        assertEquals("https://openalex.org/W2626778328", work.id)
        assertEquals("https://doi.org/10.48550/arXiv.1706.03762", work.doi)
        assertEquals("Attention Is All You Need", work.displayName)
        assertEquals(2017, work.publicationYear)
        assertEquals("Neural Information Processing Systems", work.primaryLocation?.source?.displayName)
        assertEquals(listOf("Ashish Vaswani", "Noam Shazeer", "Illia Polosukhin"), work.authorships.map { it.author.displayName })
        assertNull(work.authorships[2].author.id)
        assertEquals(128412, work.citedByCount)
        assertTrue(work.openAccess!!.isOa)
        assertEquals("https://arxiv.org/pdf/1706.03762", work.bestOaLocation?.pdfUrl)
        assertEquals(listOf(1), work.abstractInvertedIndex?.get("dominant"))
    }

    @Test
    fun toleratesNullsInSparseWork() {
        val work = response.results[1]
        assertNull(work.doi)
        assertNull(work.displayName)
        assertNull(work.publicationYear)
        assertNull(work.primaryLocation?.source)
        assertTrue(work.authorships.isEmpty())
        assertEquals(0, work.citedByCount)
        assertFalse(work.openAccess!!.isOa)
        assertNull(work.bestOaLocation)
        assertNull(work.abstractInvertedIndex)
    }
}
