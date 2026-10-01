package com.etatech.hashiya.core.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

class PdfDownloadDataSourceTest {
    private val server = MockWebServer()
    private val dataSource = OkHttpPdfDownloadDataSource(buildPdfOkHttpClient())

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        if (server.started) server.close()
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
    fun handsTheBodyAndItsLengthToTheConsumer() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(PDF)).build())

        val (bytes, length) = dataSource.download(server.url("/paper.pdf").toString()) { body, contentLength ->
            body.readBytes() to contentLength
        }

        assertArrayEquals(PDF, bytes)
        assertEquals(PDF.size.toLong(), length)
    }

    @Test
    fun followsRedirects() = runTest {
        server.enqueue(MockResponse.Builder().code(302).addHeader("Location", server.url("/files/paper.pdf").toString()).build())
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(PDF)).build())

        val bytes = dataSource.download(server.url("/pdf/1706.03762").toString()) { body, _ -> body.readBytes() }

        assertArrayEquals(PDF, bytes)
        assertEquals("/pdf/1706.03762", server.takeRequest().url.encodedPath)
        assertEquals("/files/paper.pdf", server.takeRequest().url.encodedPath)
    }

    @Test
    fun anErrorStatusIsAnHttpFailure() = runTest {
        server.enqueue(MockResponse.Builder().code(403).body("Forbidden").build())

        val failure = failureOf { dataSource.download(server.url("/paper.pdf").toString()) { body, _ -> body.readBytes() } }

        assertEquals(NetworkFailure.Http(code = 403, usedUserKey = false), failure)
    }

    @Test
    fun anUnreachableServerIsAConnectivityFailure() = runTest {
        val url = server.url("/paper.pdf").toString()
        server.close()

        assertEquals(NetworkFailure.Connectivity, failureOf { dataSource.download(url) { body, _ -> body.readBytes() } })
    }

    @Test
    fun aTimeoutIsAnHttpFailureNotOffline() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(PDF)).headersDelay(5, TimeUnit.SECONDS).build())
        val client = buildPdfOkHttpClient().newBuilder().readTimeout(200, TimeUnit.MILLISECONDS).build()

        val failure = failureOf {
            OkHttpPdfDownloadDataSource(client).download(server.url("/paper.pdf").toString()) { body, _ -> body.readBytes() }
        }

        assertEquals(NetworkFailure.Http(code = 0, usedUserKey = false), failure)
    }

    @Test
    fun anUnusableLinkIsAnHttpFailure() = runTest {
        assertEquals(
            NetworkFailure.Http(code = 0, usedUserKey = false),
            failureOf { dataSource.download("not a url") { body, _ -> body.readBytes() } }
        )
    }

    @Test
    fun sendsNoApiKey() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(PDF)).build())

        dataSource.download(server.url("/paper.pdf").toString()) { body, _ -> body.readBytes() }

        val request = server.takeRequest()
        assertNull(request.url.queryParameter("api_key"))
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun exceptionsFromTheConsumerArePassedOn() = runTest {
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(PDF)).build())

        try {
            dataSource.download(server.url("/paper.pdf").toString()) { _, _ -> throw IllegalStateException("disk full") }
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("disk full", e.message)
        }
    }

    private companion object {
        val PDF = "%PDF-1.7\n%%EOF\n".toByteArray()
    }
}
