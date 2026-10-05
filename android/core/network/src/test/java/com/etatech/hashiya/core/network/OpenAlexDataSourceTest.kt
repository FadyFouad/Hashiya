package com.etatech.hashiya.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class OpenAlexDataSourceTest {
    private val server = MockWebServer()
    private val storedUserKey = MutableStateFlow<String?>(null)
    private val keySource = object : UserApiKeySource {
        override val userKey: StateFlow<String?> = storedUserKey
    }
    private val logLines = mutableListOf<String>()

    private val request = WorksSearchRequest(
        search = "transformer attention",
        filter = "publication_year:>2016,is_oa:true",
        sort = "cited_by_count:desc",
        cursor = "*",
        perPage = 25
    )

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        if (server.started) {
            server.close()
        }
    }

    private fun dataSource(builtInKey: String = "built-in-key"): OpenAlexDataSource {
        val client = buildOpenAlexOkHttpClient(keySource, builtInKey, logger = { logLines += it })
        return RetrofitOpenAlexDataSource(buildOpenAlexApi(server.url("/"), client))
    }

    private fun enqueue(code: Int, body: String = readFixture("works_page.json")) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private suspend fun failureOf(block: suspend () -> Unit): NetworkException {
        try {
            block()
        } catch (e: NetworkException) {
            return e
        }
        fail("Expected NetworkException")
        error("unreachable")
    }

    @Test
    fun sendsSearchParameters() = runTest {
        enqueue(200)
        val response = dataSource().searchWorks(request)

        val url = server.takeRequest().url
        assertEquals("/works", url.encodedPath)
        assertEquals("transformer attention", url.queryParameter("search"))
        assertEquals("publication_year:>2016,is_oa:true", url.queryParameter("filter"))
        assertEquals("cited_by_count:desc", url.queryParameter("sort"))
        assertEquals("25", url.queryParameter("per_page"))
        assertEquals("*", url.queryParameter("cursor"))
        assertEquals(SEARCH_FIELDS, url.queryParameter("select"))
        assertTrue(SEARCH_FIELDS.split(",").contains("primary_topic"))
        assertEquals(48210L, response.meta.count)
    }

    @Test
    fun omitsFilterAndSortWhenNull() = runTest {
        enqueue(200)
        dataSource().searchWorks(request.copy(filter = null, sort = null))

        val url = server.takeRequest().url
        assertNull(url.queryParameter("filter"))
        assertNull(url.queryParameter("sort"))
    }

    @Test
    fun encodesArabicAndPunctuationInSearchText() = runTest {
        val text = "تعلم الآلة: \"deep\", 100% & more"
        enqueue(200)
        dataSource().searchWorks(request.copy(search = text))

        assertEquals(text, server.takeRequest().url.queryParameter("search"))
    }

    @Test
    fun usesBuiltInKeyWhenUserHasNone() = runTest {
        enqueue(200)
        dataSource().searchWorks(request)

        assertEquals("built-in-key", server.takeRequest().url.queryParameter("api_key"))
    }

    @Test
    fun userKeyOverridesBuiltInKey() = runTest {
        storedUserKey.value = "user-key"
        enqueue(200)
        dataSource().searchWorks(request)

        assertEquals("user-key", server.takeRequest().url.queryParameter("api_key"))
    }

    @Test
    fun sendsNoKeyWhenNeitherKeyIsSet() = runTest {
        enqueue(200)
        dataSource(builtInKey = "").searchWorks(request)

        assertNull(server.takeRequest().url.queryParameter("api_key"))
    }

    @Test
    fun rejectedUserKeyIsReportedAsUserKeyFailure() = runTest {
        storedUserKey.value = "user-key"
        enqueue(401, "{}")

        val e = failureOf { dataSource().searchWorks(request) }
        assertEquals(NetworkFailure.Http(code = 401, usedUserKey = true), e.failure)
    }

    @Test
    fun rejectedBuiltInKeyIsNotBlamedOnTheUser() = runTest {
        enqueue(403, "{}")

        val e = failureOf { dataSource().searchWorks(request) }
        assertEquals(NetworkFailure.Http(code = 403, usedUserKey = false), e.failure)
    }

    @Test
    fun rateLimitIsReportedWithItsCode() = runTest {
        enqueue(429, "{}")

        val e = failureOf { dataSource().searchWorks(request) }
        assertEquals(NetworkFailure.Http(code = 429, usedUserKey = false), e.failure)
    }

    @Test
    fun unreadableBodyIsMalformedResponse() = runTest {
        enqueue(200, """{"unexpected": true}""")

        val e = failureOf { dataSource().searchWorks(request) }
        assertEquals(NetworkFailure.MalformedResponse, e.failure)
    }

    @Test
    fun unreachableServerIsConnectivityFailure() = runTest {
        val dataSource = dataSource()
        server.close()

        val e = failureOf { dataSource.searchWorks(request) }
        assertEquals(NetworkFailure.Connectivity, e.failure)
    }

    @Test
    fun apiKeyNeverAppearsInLogsOrErrors() = runTest {
        storedUserKey.value = "user-key-secret"
        enqueue(200)
        dataSource().searchWorks(request)
        enqueue(401, "{}")
        val e = failureOf { dataSource().searchWorks(request) }

        assertTrue(logLines.any { it.contains("/works") })
        assertFalse(logLines.any { it.contains("user-key-secret") })
        assertFalse("${e.message} ${e.cause?.message}".contains("user-key-secret"))
    }
}
