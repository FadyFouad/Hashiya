package com.etatech.hashiya.core.data.importer

import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import com.etatech.hashiya.core.network.model.NetworkMeta
import com.etatech.hashiya.core.network.model.NetworkWork
import com.etatech.hashiya.core.network.model.NetworkWorksResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAlexDoiMatcherTest {
    /** Answers with a work for every DOI in [known]; the requests whose number (from 1) is in [failures] fail. */
    private class FakeSource(private val known: Set<String> = emptySet(), private val failures: Map<Int, NetworkFailure> = emptyMap()) :
        OpenAlexLookupDataSource {
        val requests = mutableListOf<String>()

        private fun fail() = failures[requests.size]?.let { throw NetworkException(it) }

        override suspend fun getWork(id: String): NetworkWork? {
            requests += "get $id"
            fail()
            val doi = id.removePrefix("doi:")
            return if (doi in known) work(doi) else null
        }

        override suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse = error("imports use findWorksForImport")

        override suspend fun findWorksForImport(filter: String, perPage: Int): NetworkWorksResponse {
            requests += "import $perPage $filter"
            fail()
            val results = filter.removePrefix("doi:").split('|').filter { it in known }.map(::work)
            return NetworkWorksResponse(meta = NetworkMeta(count = results.size.toLong()), results = results)
        }
    }

    private val pauses = mutableListOf<Long>()

    private fun matcher(source: OpenAlexLookupDataSource) = OpenAlexDoiMatcher(source, pause = { pauses += it })

    private fun dois(count: Int) = (1..count).map { "10.1/$it" }

    private val dailyLimit = NetworkFailure.DailyLimit(resetAtMillis = 0)

    @Test
    fun `DOIs go fifty to an import call, with per_page fifty, a hundred milliseconds apart`() = runTest {
        val source = FakeSource(known = dois(120).toSet())
        val result = matcher(source).match(dois(120))
        assertEquals(3, source.requests.size)
        assertEquals("import 50 doi:" + dois(50).joinToString("|"), source.requests[0])
        assertEquals("import 50 doi:" + dois(120).drop(100).joinToString("|"), source.requests[2])
        assertEquals(listOf(100L, 100L), pauses)
        assertEquals(dois(120).toSet(), result.found.keys)
        assertTrue(result.notFound.isEmpty() && result.notChecked.isEmpty())
    }

    @Test
    fun `there is no cap of its own - the quota decides`() = runTest {
        val source = FakeSource()
        val result = matcher(source).match(dois(2_050))
        assertEquals(41, source.requests.size)
        assertEquals(2_050, result.notFound.size)
    }

    @Test
    fun `found works map back by normalised DOI and the rest are not found`() = runTest {
        val source = object : OpenAlexLookupDataSource {
            override suspend fun getWork(id: String): NetworkWork? = error("unused")

            override suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse = error("unused")

            override suspend fun findWorksForImport(filter: String, perPage: Int) = NetworkWorksResponse(
                meta = NetworkMeta(count = 3),
                results = listOf(
                    work("10.1/a", id = "W1").copy(doi = "https://doi.org/10.1/A"),
                    work("10.1/a", id = "W2"),
                    work("10.9/unasked", id = "W3")
                )
            )
        }
        val result = matcher(source).match(listOf("10.1/a", "10.1/b"))
        assertEquals("W1", result.found.getValue("10.1/a").openAlexId)
        assertEquals(setOf("10.1/b"), result.notFound)
    }

    @Test
    fun `repeated DOIs are asked once`() = runTest {
        val source = FakeSource()
        matcher(source).match(listOf("10.1/a", "10.1/a"))
        assertEquals(listOf("import 50 doi:10.1/a"), source.requests)
    }

    @Test
    fun `a DOI that can't go in a filter is looked up on its own, for free`() = runTest {
        val odd = listOf("10.1/a|b", "10.1/a,b", "10.1/a b")
        val source = FakeSource(known = setOf("10.1/a,b"))
        val result = matcher(source).match(listOf("10.1/ok") + odd)
        assertEquals(listOf("import 50 doi:10.1/ok", "get doi:10.1/a|b", "get doi:10.1/a,b", "get doi:10.1/a b"), source.requests)
        assertEquals(setOf("10.1/a,b"), result.found.keys)
        assertEquals(setOf("10.1/ok", "10.1/a|b", "10.1/a b"), result.notFound)
    }

    @Test
    fun `when the quota has no metered route left, the rest are looked up one by one for free`() = runTest {
        val source = FakeSource(known = dois(53).toSet(), failures = mapOf(2 to dailyLimit))
        val result = matcher(source).match(dois(53))
        assertEquals("import 50 doi:" + dois(50).joinToString("|"), source.requests[0])
        assertEquals("import 50 doi:" + dois(53).drop(50).joinToString("|"), source.requests[1])
        assertEquals(listOf("get doi:10.1/51", "get doi:10.1/52", "get doi:10.1/53"), source.requests.drop(2))
        assertEquals(dois(53).toSet(), result.found.keys)
        assertTrue(result.notChecked.isEmpty())
        assertEquals(4, pauses.size)
    }

    @Test
    fun `after the daily limit every later batch goes one by one too`() = runTest {
        val source = FakeSource(failures = mapOf(1 to dailyLimit))
        matcher(source).match(dois(60))
        assertEquals(1 + 60, source.requests.size)
        assertEquals(60, source.requests.count { it.startsWith("get ") })
    }

    @Test
    fun `losing the connection stops every later request`() = runTest {
        val source = FakeSource(failures = mapOf(1 to NetworkFailure.Connectivity))
        val result = matcher(source).match(dois(120))
        assertEquals(1, source.requests.size)
        assertEquals(dois(120).toSet(), result.notChecked)
    }

    @Test
    fun `a single lookup that loses the connection stops the rest too`() = runTest {
        val source = FakeSource(failures = mapOf(1 to dailyLimit, 3 to NetworkFailure.Connectivity))
        val result = matcher(source).match(dois(5))
        assertEquals(3, source.requests.size)
        assertEquals(setOf("10.1/1"), result.notFound)
        assertEquals(dois(5).drop(1).toSet(), result.notChecked)
    }

    @Test
    fun `any other failure skips that batch only`() = runTest {
        val source = FakeSource(known = dois(120).toSet(), failures = mapOf(2 to NetworkFailure.Http(503, usedUserKey = false)))
        val result = matcher(source).match(dois(120))
        assertEquals(3, source.requests.size)
        assertEquals(dois(120).drop(50).take(50).toSet(), result.notChecked)
        assertEquals(70, result.found.size)
    }

    @Test
    fun `nothing to match sends nothing`() = runTest {
        val source = FakeSource()
        assertEquals(DoiMatches(), matcher(source).match(emptyList()))
        assertTrue(source.requests.isEmpty())
    }

    private companion object {
        fun work(doi: String, id: String = "W" + doi.hashCode().toUInt()) =
            NetworkWork(id = "https://openalex.org/$id", doi = "https://doi.org/$doi", displayName = "Title of $doi")
    }
}
