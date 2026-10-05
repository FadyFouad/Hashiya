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
        assertEquals("preprint", work.type)
        assertEquals(NetworkBiblio(volume = "30", issue = null, firstPage = "5998", lastPage = "6008"), work.biblio)
        assertEquals("conference", work.primaryLocation?.source?.type)
        assertEquals("Neural Information Processing Systems Foundation", work.primaryLocation?.source?.hostOrganizationName)
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
        assertNull(work.type)
        assertNull(work.biblio)
    }

    @Test
    fun parsesThePrimaryTopicIds() {
        val work = OpenAlexJson.decodeFromString<NetworkWork>(
            """{"id":"https://openalex.org/W1","primary_topic":{"id":"https://openalex.org/T1","display_name":"Name","subfield":{"id":"https://openalex.org/subfields/1707","display_name":"CV"},"field":{"id":"https://openalex.org/fields/17"},"domain":{"id":"https://openalex.org/domains/3"}}}"""
        )
        assertEquals("https://openalex.org/subfields/1707", work.primaryTopic?.subfield?.id)
        assertEquals("https://openalex.org/fields/17", work.primaryTopic?.field?.id)
        assertEquals("https://openalex.org/domains/3", work.primaryTopic?.domain?.id)
    }

    @Test
    fun aMissingOrOddPrimaryTopicDoesNotFailTheWork() {
        for (topic in listOf("null", "\"x\"", "7", "[1]", """{"subfield":{"id":1707}}""", """{"subfield":"x","field":null}""")) {
            val work = OpenAlexJson.decodeFromString<NetworkWork>("""{"id":"https://openalex.org/W1","primary_topic":$topic}""")
            assertEquals("https://openalex.org/W1", work.id)
        }
        val odd = OpenAlexJson.decodeFromString<NetworkWork>(
            """{"id":"W1","primary_topic":{"subfield":{"id":1707},"field":{"id":"https://openalex.org/fields/17"}}}"""
        )
        assertNull(odd.primaryTopic?.subfield?.id)
        assertEquals("https://openalex.org/fields/17", odd.primaryTopic?.field?.id)
    }
}
