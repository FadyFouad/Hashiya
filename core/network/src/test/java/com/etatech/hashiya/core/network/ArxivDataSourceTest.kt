package com.etatech.hashiya.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class ArxivDataSourceTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        if (server.started) server.close()
    }

    private fun dataSource(): ArxivDataSource = OkHttpArxivDataSource(buildArxivOkHttpClient(), server.url("/"))

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private suspend fun failureOf(block: suspend () -> Unit): NetworkFailure {
        try {
            block()
        } catch (e: NetworkException) {
            return e.failure
        }
        fail("Expected NetworkException")
        error("unreachable")
    }

    @Test
    fun requestsTheIdWithUserAgentAndNoApiKey() = runTest {
        enqueue(200, readFixture("arxiv_bert.xml"))

        dataSource().title("hep-th/9901001")

        val request = server.takeRequest()
        assertEquals("/api/query", request.url.encodedPath)
        assertEquals("hep-th/9901001", request.url.queryParameter("id_list"))
        assertNull(request.url.queryParameter("api_key"))
        assertEquals(ARXIV_USER_AGENT, request.headers["User-Agent"])
    }

    @Test
    fun readsTheEntryTitleWithCollapsedWhitespace() = runTest {
        enqueue(200, readFixture("arxiv_bert.xml"))
        assertEquals(
            "BERT: Pre-training of Deep Bidirectional Transformers for Language Understanding",
            dataSource().title("1810.04805")
        )
    }

    @Test
    fun emptyFeedMeansNoSuchPaper() = runTest {
        enqueue(200, readFixture("arxiv_empty.xml"))
        assertNull(dataSource().title("2401.99999"))
    }

    @Test
    fun errorEntryMeansNoSuchPaper() = runTest {
        enqueue(200, readFixture("arxiv_error.xml"))
        assertNull(dataSource().title("notanid"))
    }

    @Test
    fun serverErrorIsHttpFailure() = runTest {
        enqueue(503, "down")
        assertEquals(NetworkFailure.Http(code = 503, usedUserKey = false), failureOf { dataSource().title("1810.04805") })
    }

    @Test
    fun unreachableIsConnectivityFailure() = runTest {
        val dataSource = dataSource()
        server.close()
        assertEquals(NetworkFailure.Connectivity, failureOf { dataSource.title("1810.04805") })
    }

    @Test
    fun notAFeedIsMalformed() = runTest {
        enqueue(200, "<html><body>maintenance</body></html>")
        assertEquals(NetworkFailure.MalformedResponse, failureOf { dataSource().title("1810.04805") })
    }

    @Test
    fun decodesXmlEntities() {
        val xml = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><id>http://arxiv.org/abs/1</id>
            |<title>Graphs &amp; Networks: &lt;A&gt; &#8211; &#x3B1; &quot;study&quot;</title></entry></feed>
        """.trimMargin()
        assertEquals("Graphs & Networks: <A> – α \"study\"", parseArxivTitle(xml))
    }
}
