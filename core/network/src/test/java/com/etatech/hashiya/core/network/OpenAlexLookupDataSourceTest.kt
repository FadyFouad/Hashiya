package com.etatech.hashiya.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OpenAlexLookupDataSourceTest {
    private val server = MockWebServer()
    private val keySource = object : UserApiKeySource {
        override val userKey: StateFlow<String?> = MutableStateFlow(null)
    }

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        if (server.started) server.close()
    }

    private fun dataSource(): OpenAlexLookupDataSource {
        val client = buildOpenAlexOkHttpClient(keySource, builtInKey = "built-in-key", logger = null)
        return RetrofitOpenAlexLookupDataSource(buildOpenAlexApi(server.url("/"), client))
    }

    private fun enqueue(code: Int, body: String = "{}") {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    @Test
    fun getWorkRequestsTheWorkWithSelectedFields() = runTest {
        enqueue(200, readFixture("work.json"))

        val work = dataSource().getWork("doi:10.1038/nature14539")

        val url = server.takeRequest().url
        assertEquals(listOf("works", "doi:10.1038/nature14539"), url.pathSegments)
        assertEquals(WORK_FIELDS, url.queryParameter("select"))
        assertEquals("built-in-key", url.queryParameter("api_key"))
        assertEquals("https://openalex.org/W2919115771", work?.id)
        assertEquals("Deep learning", work?.displayName)
    }

    @Test
    fun notFoundIsNull() = runTest {
        enqueue(404)
        assertNull(dataSource().getWork("doi:10.9999/does-not-exist"))
    }

    @Test
    fun badRequestIsNull() = runTest {
        enqueue(400)
        assertNull(dataSource().getWork("doi:10.1234/weird"))
    }

    @Test
    fun otherFailuresThrow() = runTest {
        enqueue(429)
        try {
            dataSource().getWork("doi:10.1038/nature14539")
            fail("Expected NetworkException")
        } catch (e: NetworkException) {
            assertEquals(NetworkFailure.Http(code = 429, usedUserKey = false), e.failure)
        }
    }

    @Test
    fun siciDoiSurvivesPathEncoding() = runTest {
        val id = "doi:10.1002/(sici)1099-1212(199901/02)9:1<8::aid-oa453>3.0.co;2-z"
        enqueue(200, readFixture("work.json"))

        dataSource().getWork(id)

        assertEquals(listOf("works", id), server.takeRequest().url.pathSegments)
    }

    @Test
    fun hashInDoiSurvivesPathEncoding() = runTest {
        val id = "doi:10.1234/abc#1"
        enqueue(200, readFixture("work.json"))

        dataSource().getWork(id)

        assertEquals(listOf("works", id), server.takeRequest().url.pathSegments)
    }

    @Test
    fun findWorksSendsFilterAndPageSize() = runTest {
        val filter = "locations.landing_page_url:http://arxiv.org/abs/1810.04805|https://arxiv.org/abs/1810.04805"
        enqueue(200, readFixture("works_page.json"))

        val response = dataSource().findWorks(filter, perPage = 2)

        val url = server.takeRequest().url
        assertEquals("/works", url.encodedPath)
        assertEquals(filter, url.queryParameter("filter"))
        assertEquals("2", url.queryParameter("per_page"))
        assertEquals(WORK_FIELDS, url.queryParameter("select"))
        assertEquals(2, response.results.size)
    }

    @Test
    fun findWorksFailuresThrow() = runTest {
        val dataSource = dataSource()
        server.close()
        try {
            dataSource.findWorks("locations.landing_page_url:http://arxiv.org/abs/1", perPage = 2)
            fail("Expected NetworkException")
        } catch (e: NetworkException) {
            assertEquals(NetworkFailure.Connectivity, e.failure)
        }
    }
}
