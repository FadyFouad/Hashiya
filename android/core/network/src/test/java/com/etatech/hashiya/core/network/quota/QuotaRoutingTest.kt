package com.etatech.hashiya.core.network.quota

import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.RetrofitOpenAlexDataSource
import com.etatech.hashiya.core.network.RetrofitOpenAlexLookupDataSource
import com.etatech.hashiya.core.network.UserApiKeySource
import com.etatech.hashiya.core.network.WorksSearchRequest
import com.etatech.hashiya.core.network.buildOpenAlexApi
import com.etatech.hashiya.core.network.buildOpenAlexOkHttpClient
import com.etatech.hashiya.core.network.model.RequestRoute
import com.etatech.hashiya.core.network.readFixture
import com.etatech.hashiya.core.testing.InMemoryQuotaPreferences
import java.io.File
import java.io.IOException
import java.time.Instant
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QuotaRoutingTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val api = MockWebServer()
    private val proxy = MockWebServer()
    private val apiRequests = Collections.synchronizedList(mutableListOf<RecordedRequest>())
    private val proxyRequests = Collections.synchronizedList(mutableListOf<RecordedRequest>())

    private val page = readFixture("works_page.json")
    private val work = readFixture("work.json")
    private val prefs = InMemoryQuotaPreferences()
    private var clock = millis("2026-10-05T20:00:00Z")
    private val userKey = MutableStateFlow<String?>(null)
    private val keySource = object : UserApiKeySource {
        override val userKey: StateFlow<String?> = this@QuotaRoutingTest.userKey
    }

    /** Every wait the interceptor asked for, in millis; returns at once unless a test replaces [sleep]. */
    private val sleeps = Collections.synchronizedList(mutableListOf<Long>())
    private var sleep: (Long) -> Unit = { sleeps += it }

    private val request = WorksSearchRequest(search = "bert", filter = null, sort = null, cursor = "*", perPage = 25)

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    @Before
    fun setUp() {
        api.start()
        proxy.start()
        apiReplies { ok(page) }
        proxyReplies { ok(page) }
    }

    @After
    fun tearDown() {
        if (api.started) api.close()
        if (proxy.started) proxy.close()
    }

    private fun apiReplies(reply: (RecordedRequest) -> MockResponse) {
        api.dispatcher = recording(apiRequests, reply)
    }

    private fun proxyReplies(reply: (RecordedRequest) -> MockResponse) {
        proxy.dispatcher = recording(proxyRequests, reply)
    }

    private fun recording(requests: MutableList<RecordedRequest>, reply: (RecordedRequest) -> MockResponse) = object : Dispatcher() {
        override fun dispatch(request: RecordedRequest): MockResponse {
            requests += request
            return reply(request)
        }
    }

    private fun ok(body: String) = MockResponse.Builder().code(200).body(body).build()

    private fun status(code: Int, vararg headers: Pair<String, String>) = MockResponse.Builder().code(code).body("{}")
        .apply { headers.forEach { (name, value) -> addHeader(name, value) } }
        .build()

    private fun usedUp(reset: String = "600") = status(429, "X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to reset)

    private fun RecordedRequest.key() = url.queryParameter("api_key")

    /** The proxy server under an `/openalex` path, as the config would give it. */
    private fun proxyUrl(): HttpUrl = proxy.url("/openalex")

    private fun limits(cap: Int = 60, baseUrl: HttpUrl? = null) =
        OpenAlexLimits(dailyDeviceCalls = cap, maxPagesPerQuery = 8, baseUrl = baseUrl)

    private fun quota(limits: OpenAlexLimits = OpenAlexLimits.Defaults, builtInKey: Boolean = true): OpenAlexQuota {
        prefs.limits = limits
        return OpenAlexQuota(prefs, builtInKey) { clock }
    }

    private val cacheDirectory by lazy { File(folder.root, "openalex-search") }

    private fun client(quota: OpenAlexQuota) =
        buildOpenAlexOkHttpClient(QuotaInterceptor(keySource, "built-in", quota) { sleep(it) }, logger = null)

    /** [cache] false gives each data source an empty cache of its own, so nothing is answered from disk. */
    private fun search(quota: OpenAlexQuota, cache: Boolean = false): RetrofitOpenAlexDataSource {
        val directory = if (cache) cacheDirectory else folder.newFolder()
        return RetrofitOpenAlexDataSource(buildOpenAlexApi(api.url("/"), client(quota)), SearchCache(directory) { clock })
    }

    private fun lookup(quota: OpenAlexQuota) =
        RetrofitOpenAlexLookupDataSource(buildOpenAlexApi(api.url("/"), client(quota)), SearchCache(folder.newFolder()) { clock })

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
    fun aSharedSearchSendsTheBuiltInKeyAndCounts() = runTest {
        val quota = quota(limits(cap = 1))
        search(quota).searchWorks(request)
        assertEquals("built-in", apiRequests.last().key())
        assertEquals(OpenAlexRoute.Keyless, quota.meteredRoute())
    }

    @Test
    fun aUsedUpSharedBudgetRetriesOnceWithoutAKey() = runTest {
        apiReplies { if (it.key() == null) ok(page) else usedUp() }
        val quota = quota()
        search(quota).searchWorks(request)
        assertEquals(2, apiRequests.size)
        assertNull(apiRequests[1].key())
        assertEquals(OpenAlexRoute.Keyless, quota.meteredRoute())
    }

    @Test
    fun remainingAtOrBelowZeroIsUsedUp() = runTest {
        for (remaining in listOf("0.0", "-1")) {
            apiRequests.clear()
            prefs.sharedUsedUpUntil = 0
            apiReplies { if (it.key() == null) ok(page) else status(429, "X-RateLimit-Remaining" to remaining) }
            search(quota()).searchWorks(request)
            assertEquals(remaining, 2, apiRequests.size)
            assertNull(remaining, apiRequests[1].key())
            assertTrue(remaining, sleeps.isEmpty())
        }
    }

    @Test
    fun aNaNRetryAfterWaitsTheDefaultSecond() = runTest {
        apiReplies { if (apiRequests.size == 1) status(429, "Retry-After" to "nan") else ok(page) }
        search(quota()).searchWorks(request)
        assertEquals(listOf(1000L), sleeps.toList())
        assertEquals(2, apiRequests.size)
    }

    @Test
    fun aNaNResetMeansTheNextMidnightUTC() = runTest {
        apiReplies { usedUp(reset = "nan") }
        val failure = failureOf { search(quota()).searchWorks(request) }
        assertEquals(NetworkFailure.DailyLimit(millis("2026-10-06T00:00:00Z")), failure)
        assertEquals(2, apiRequests.size)
    }

    @Test
    fun bothBudgetsUsedUpGivesTheDailyLimitWithTheEarliestReset() = runTest {
        apiReplies { if (it.key() == null) usedUp(reset = "1800") else usedUp(reset = "3600") }
        val failure = failureOf { search(quota()).searchWorks(request) }
        assertEquals(NetworkFailure.DailyLimit(clock + 1_800_000), failure)
        assertEquals(2, apiRequests.size)
    }

    @Test
    fun whenOutASearchSendsNothing() = runTest {
        val quota = quota()
        quota.markUsedUp(OpenAlexRoute.Shared, 600.0)
        quota.markUsedUp(OpenAlexRoute.Keyless, 900.0)
        val failure = failureOf { search(quota).searchWorks(request) }
        assertEquals(NetworkFailure.DailyLimit(clock + 600_000), failure)
        assertTrue(apiRequests.isEmpty())
    }

    @Test
    fun aLookupStillGoesOutWhenSearchIsOutAndNeverCounts() = runTest {
        apiReplies { ok(work) }
        val quota = quota(limits(cap = 1))
        lookup(quota).getWork("W1")
        assertEquals(OpenAlexRoute.Shared, quota.meteredRoute())
        quota.markUsedUp(OpenAlexRoute.Shared, 600.0)
        quota.markUsedUp(OpenAlexRoute.Keyless, 600.0)
        lookup(quota).getWork("W1")
        assertEquals(2, apiRequests.size)
        assertNull(apiRequests[1].key())
    }

    @Test
    fun aFilterListIsMetered() = runTest {
        val quota = quota(limits(cap = 1))
        lookup(quota).findWorks(filter = "doi:10.1/x", perPage = 1)
        assertEquals(OpenAlexRoute.Keyless, quota.meteredRoute())
    }

    @Test
    fun aPerSecondLimitWaitsAndRetriesOnceOnTheSameRoute() = runTest {
        apiReplies { if (apiRequests.size == 1) status(429, "X-RateLimit-Remaining" to "50") else ok(page) }
        search(quota()).searchWorks(request)
        assertEquals(listOf(1000L), sleeps.toList())
        assertEquals(2, apiRequests.size)
        assertEquals("built-in", apiRequests[1].key())
    }

    @Test
    fun aSecondPerSecondLimitIsTheRateLimitedError() = runTest {
        for (remaining in listOf("abc", "nan")) {
            apiRequests.clear()
            sleeps.clear()
            apiReplies { status(429, "X-RateLimit-Remaining" to remaining, "Retry-After" to "10") }
            val quota = quota()
            val failure = failureOf { search(quota).searchWorks(request) }
            assertEquals(remaining, NetworkFailure.Http(code = 429, usedUserKey = false), failure)
            // Retry-After 10 is capped at 3 seconds.
            assertEquals(remaining, listOf(3000L), sleeps.toList())
            assertEquals(remaining, 2, apiRequests.size)
            assertEquals(remaining, OpenAlexRoute.Shared, quota.meteredRoute())
        }
    }

    @Test
    fun cancellingDuringTheWaitMarksNothing() {
        apiReplies { status(429) }
        val quota = quota()
        val sleeping = CountDownLatch(1)
        val release = CountDownLatch(1)
        sleep = {
            sleeping.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val call = client(quota).newCall(Request.Builder().url(api.url("/works?search=bert")).build())
        var failure: Throwable? = null
        val worker = thread { failure = runCatching { call.execute() }.exceptionOrNull() }
        assertTrue(sleeping.await(5, TimeUnit.SECONDS))
        call.cancel()
        release.countDown()
        worker.join(5_000)

        assertTrue("$failure", failure is IOException)
        assertEquals("Canceled", failure?.message)
        assertEquals(1, apiRequests.size)
        assertEquals(OpenAlexRoute.Shared, quota.meteredRoute())
    }

    @Test
    fun theUserKeyIsTheOnlyRoute() = runTest {
        apiReplies { usedUp() }
        userKey.value = "mine"
        val quota = quota()
        val failure = failureOf { search(quota).searchWorks(request) }
        assertEquals(NetworkFailure.Http(code = 429, usedUserKey = true), failure)
        assertEquals(1, apiRequests.size)
        assertEquals("mine", apiRequests.last().key())
        assertEquals(OpenAlexRoute.Shared, quota.meteredRoute())
    }

    @Test
    fun aProxyGetsSharedRequestsUnderItsPathWithNoKey() = runTest {
        search(quota(limits(baseUrl = proxyUrl()))).searchWorks(request)
        assertTrue(apiRequests.isEmpty())
        val url = proxyRequests.single().url
        assertEquals("/openalex/works", url.encodedPath)
        assertNull(url.queryParameter("api_key"))
        assertEquals("bert", url.queryParameter("search"))
    }

    @Test
    fun aProxyKeepsItsPathButNotItsOwnQuery() = runTest {
        apiReplies { ok(work) }
        proxyReplies { ok(work) }
        val base = proxy.url("/openalex/").newBuilder().addQueryParameter("token", "x").build()
        lookup(quota(limits(baseUrl = base))).getWork("doi:10.1/a b")
        val url = proxyRequests.single().url
        assertEquals(listOf("openalex", "works", "doi:10.1/a b"), url.pathSegments)
        assertNull(url.queryParameter("token"))
        assertNull(url.queryParameter("api_key"))
        assertTrue(url.queryParameter("select") != null)
    }

    @Test
    fun aProxyUrlKeepsItsUserinfoAndPrefixAndTakesTheRequestsQuery() {
        val original = "https://api.openalex.org/works/doi:10.1%2Fx?select=id&api_key=secret".toHttpUrl()
        val base = "https://u:p@proxy.example/openalex/?token=x".toHttpUrl()
        assertEquals(
            "https://u:p@proxy.example/openalex/works/doi:10.1%2Fx?select=id",
            routedUrl(original, proxy = base, key = null).toString()
        )
        assertEquals(
            "https://api.openalex.org/works/doi:10.1%2Fx?select=id&api_key=k",
            routedUrl(original, proxy = null, key = "k").toString()
        )
        assertEquals("https://api.openalex.org/works/doi:10.1%2Fx?select=id", routedUrl(original, proxy = null, key = null).toString())
    }

    @Test
    fun aFailingProxyWith503FallsBackToKeylessWithoutBeingMarked() = runTest {
        proxyReplies { status(503) }
        val quota = quota(limits(baseUrl = proxyUrl()))
        search(quota).searchWorks(request)
        assertEquals(1, proxyRequests.size)
        assertEquals(1, apiRequests.size)
        assertNull(apiRequests.last().key())
        assertEquals(OpenAlexRoute.Shared, quota.meteredRoute())
    }

    @Test
    fun anUnreachableProxyFallsBackToKeylessWithoutBeingMarked() = runTest {
        val base = proxyUrl()
        proxy.close()
        val quota = quota(limits(baseUrl = base))
        search(quota).searchWorks(request)
        assertEquals(1, apiRequests.size)
        assertNull(apiRequests.last().key())
        assertEquals(OpenAlexRoute.Shared, quota.meteredRoute())
    }

    @Test
    fun aFailingProxyWithKeylessUsedUpIsUnavailableNotTheDailyLimit() = runTest {
        proxyReplies { status(503) }
        val quota = quota(limits(baseUrl = proxyUrl()))
        quota.markUsedUp(OpenAlexRoute.Keyless, 600.0)
        val failure = failureOf { search(quota).searchWorks(request) }
        assertEquals(NetworkFailure.Http(code = 503, usedUserKey = false), failure)
        assertEquals(1, proxyRequests.size)
        assertTrue(apiRequests.isEmpty())
    }

    @Test
    fun theUserKeyNeverGoesToTheProxy() = runTest {
        userKey.value = "mine"
        search(quota(limits(baseUrl = proxyUrl()))).searchWorks(request)
        assertTrue(proxyRequests.isEmpty())
        assertEquals("mine", apiRequests.single().key())
    }

    @Test
    fun theRouteHeaderIsNeverSent() = runTest {
        apiReplies { if (it.key() == null) ok(page) else usedUp() }
        search(quota()).searchWorks(request)
        assertTrue(apiRequests.all { it.headers["X-Hashiya-Route"] == null })
    }

    @Test
    fun aRepeatedSearchIsAnsweredFromTheCacheWithoutCounting() = runTest {
        val quota = quota(limits(cap = 1))
        val dataSource = search(quota, cache = true)
        dataSource.searchWorks(request)
        clock += 60_000
        dataSource.searchWorks(request)
        assertEquals(1, apiRequests.size)
        dataSource.searchWorks(request.copy(cursor = "abc"))
        assertEquals(2, apiRequests.size)
    }

    @Test
    fun aCachedSearchIsSharedAcrossRoutes() = runTest {
        search(quota(), cache = true).searchWorks(request)
        userKey.value = "mine"
        assertEquals(RequestRoute.Cached, search(quota(), cache = true).searchWorks(request).route)
        assertEquals(1, apiRequests.size)
    }

    @Test
    fun failuresAreNotCached() = runTest {
        apiReplies { if (apiRequests.size == 1) status(500) else ok(page) }
        val dataSource = search(quota(), cache = true)
        assertEquals(NetworkFailure.Http(code = 500, usedUserKey = false), failureOf { dataSource.searchWorks(request) })
        dataSource.searchWorks(request)
        assertEquals(2, apiRequests.size)
    }

    @Test
    fun anUndecodableReplyIsNotCached() = runTest {
        apiReplies { if (apiRequests.size == 1) ok("not json") else ok(page) }
        val dataSource = search(quota(), cache = true)
        assertEquals(NetworkFailure.MalformedResponse, failureOf { dataSource.searchWorks(request) })
        dataSource.searchWorks(request)
        assertEquals(2, apiRequests.size)
        dataSource.searchWorks(request)
        assertEquals(2, apiRequests.size)
    }

    @Test
    fun aSearchReportsTheRouteItUsed() = runTest {
        assertEquals(RequestRoute.Shared, search(quota()).searchWorks(request).route)
        userKey.value = "mine"
        assertEquals(RequestRoute.User, search(quota()).searchWorks(request).route)
    }

    @Test
    fun aCachedSearchReportsCached() = runTest {
        val dataSource = search(quota(), cache = true)
        dataSource.searchWorks(request)
        assertEquals(RequestRoute.Cached, dataSource.searchWorks(request).route)
    }

    @Test
    fun aSearchAfterTheSharedBudgetIsUsedUpReportsKeyless() = runTest {
        apiReplies { if (it.key() == null) ok(page) else usedUp() }
        assertEquals(RequestRoute.Keyless, search(quota()).searchWorks(request).route)
    }
}
