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

class AppConfigDataSourceTest {
    private val server = MockWebServer()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        if (server.started) server.close()
    }

    private fun dataSource(): AppConfigDataSource =
        OkHttpAppConfigDataSource(buildAppConfigOkHttpClient(), server.url("/Hashiya-Privacy-Policy/app-config.json"))

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
    fun readsTheAndroidEntry() = runTest {
        enqueue(200, SAMPLE)

        assertEquals(NetworkPlatformConfig(minimumVersionCode = 3, storeUrl = PLAY_URL), dataSource().androidConfig())
    }

    @Test
    fun requestsTheFileWithNoQueryOrKey() = runTest {
        enqueue(200, SAMPLE)
        dataSource().androidConfig()

        val request = server.takeRequest()
        assertEquals("/Hashiya-Privacy-Policy/app-config.json", request.url.encodedPath)
        assertNull(request.url.query)
        assertNull(request.headers["Authorization"])
    }

    @Test
    fun ignoresUnknownKeys() = runTest {
        enqueue(200, """{"android":{"minimumVersionCode":2,"storeUrl":"$PLAY_URL","message":"x"},"web":{}}""")

        assertEquals(NetworkPlatformConfig(2, PLAY_URL), dataSource().androidConfig())
    }

    @Test
    fun aMissingAndroidEntryIsNull() = runTest {
        enqueue(200, """{"ios":{"minimumBuild":5}}""")

        assertNull(dataSource().androidConfig())
    }

    @Test
    fun aMinimumWrittenAsTextIsMalformed() = runTest {
        enqueue(200, """{"android":{"minimumVersionCode":"2","storeUrl":"$PLAY_URL"}}""")

        assertEquals(NetworkFailure.MalformedResponse, failureOf { dataSource().androidConfig() })
    }

    @Test
    fun aFractionalMinimumIsMalformed() = runTest {
        enqueue(200, """{"android":{"minimumVersionCode":2.5,"storeUrl":"$PLAY_URL"}}""")

        assertEquals(NetworkFailure.MalformedResponse, failureOf { dataSource().androidConfig() })
    }

    @Test
    fun notJsonIsMalformed() = runTest {
        enqueue(200, "<html>Not found</html>")

        assertEquals(NetworkFailure.MalformedResponse, failureOf { dataSource().androidConfig() })
    }

    @Test
    fun aMissingFileIsAnHttpFailure() = runTest {
        enqueue(404, "Not found")

        assertEquals(NetworkFailure.Http(code = 404, usedUserKey = false), failureOf { dataSource().androidConfig() })
    }

    @Test
    fun anUnreachableServerIsConnectivity() = runTest {
        server.close()

        assertEquals(NetworkFailure.Connectivity, failureOf { dataSource().androidConfig() })
    }

    private companion object {
        const val PLAY_URL = "https://play.google.com/store/apps/details?id=com.etatech.hashiya"
        val SAMPLE = """
            {
              "android": { "minimumVersionCode": 3, "storeUrl": "$PLAY_URL" },
              "ios": { "minimumBuild": 1, "storeUrl": "https://apps.apple.com/app/id0000000000" }
            }
        """.trimIndent()
    }
}
