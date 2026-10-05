package com.etatech.hashiya.core.network.quota

import com.etatech.hashiya.core.testing.InMemoryQuotaPreferences
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenAlexQuotaTest {
    private val prefs = InMemoryQuotaPreferences()
    private var clock = millis("2026-10-05T20:00:00Z")

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()

    private fun quota(builtInKey: Boolean = true, limits: OpenAlexLimits = OpenAlexLimits.Defaults): OpenAlexQuota {
        prefs.limits = limits
        return OpenAlexQuota(prefs, builtInKey) { clock }
    }

    private fun limits(cap: Int, baseUrl: String? = null) =
        OpenAlexLimits(dailyDeviceCalls = cap, maxPagesPerQuery = 8, baseUrl = baseUrl?.toHttpUrl())

    @Test
    fun startsOnTheSharedRoute() {
        val quota = quota()
        assertEquals(OpenAlexRoute.Shared, quota.meteredRoute())
        assertEquals(OpenAlexRoute.Shared, quota.lookupRoute())
    }

    @Test
    fun withoutABuiltInKeyOrProxyEverythingIsKeyless() {
        val quota = quota(builtInKey = false)
        assertEquals(OpenAlexRoute.Keyless, quota.meteredRoute())
        assertEquals(OpenAlexRoute.Keyless, quota.lookupRoute())
    }

    @Test
    fun aProxyMakesTheSharedRouteWithoutABuiltInKey() {
        val quota = quota(builtInKey = false, limits = limits(60, "https://proxy.example"))
        assertEquals(OpenAlexRoute.Shared, quota.meteredRoute())
    }

    @Test
    fun theCapMovesMeteredCallsToKeylessButNotLookups() {
        val quota = quota(limits = limits(2))
        quota.recordSharedCall()
        assertEquals(OpenAlexRoute.Shared, quota.meteredRoute())
        quota.recordSharedCall()
        assertEquals(OpenAlexRoute.Keyless, quota.meteredRoute())
        assertEquals(OpenAlexRoute.Shared, quota.lookupRoute())
    }

    @Test
    fun aZeroCapTurnsTheSharedRouteOffForMeteredCalls() {
        val quota = quota(limits = limits(0))
        assertEquals(OpenAlexRoute.Keyless, quota.meteredRoute())
        assertEquals(OpenAlexRoute.Shared, quota.lookupRoute())
    }

    @Test
    fun theCountStartsAgainAtUtcMidnightNotLocalMidnight() {
        val quota = quota(limits = limits(1))
        quota.recordSharedCall()
        // 00:30 in UTC+3 is still the same UTC day.
        clock = millis("2026-10-05T21:30:00Z")
        assertEquals(OpenAlexRoute.Keyless, quota.meteredRoute())
        clock = millis("2026-10-06T00:00:01Z")
        assertEquals(OpenAlexRoute.Shared, quota.meteredRoute())
    }

    @Test
    fun aUsedUpSharedBudgetSendsEverythingKeylessUntilItsReset() {
        val quota = quota()
        quota.markUsedUp(OpenAlexRoute.Shared, 600.0)
        assertEquals(OpenAlexRoute.Keyless, quota.meteredRoute())
        assertEquals(OpenAlexRoute.Keyless, quota.lookupRoute())
        clock += 601_000
        assertEquals(OpenAlexRoute.Shared, quota.meteredRoute())
    }

    @Test
    fun bothBudgetsUsedUpMeansOutUntilTheEarliestReset() {
        val quota = quota()
        quota.markUsedUp(OpenAlexRoute.Shared, 3600.0)
        quota.markUsedUp(OpenAlexRoute.Keyless, 1800.0)
        assertNull(quota.meteredRoute())
        assertEquals(OpenAlexRoute.Keyless, quota.lookupRoute())
        assertEquals(clock + 1_800_000, quota.nextAvailable())
    }

    @Test
    fun theCapCountsAsUsedUpUntilUtcMidnightForNextAvailable() {
        val quota = quota(limits = limits(1))
        quota.recordSharedCall()
        quota.markUsedUp(OpenAlexRoute.Keyless, 6 * 3600.0)
        assertNull(quota.meteredRoute())
        assertEquals(millis("2026-10-06T00:00:00Z"), quota.nextAvailable())
    }

    @Test
    fun aMissingOrUnbelievableResetMeansNextUtcMidnight() {
        listOf(null, -5.0, 3 * 86_400.0, Double.NaN, Double.POSITIVE_INFINITY).forEach { reset ->
            val quota = quota()
            quota.markUsedUp(OpenAlexRoute.Shared, reset)
            quota.markUsedUp(OpenAlexRoute.Keyless, reset)
            assertEquals("reset $reset", millis("2026-10-06T00:00:00Z"), quota.nextAvailable())
        }
    }

    @Test
    fun marksAndCountsSurviveANewInstance() {
        val first = quota(limits = limits(1))
        first.recordSharedCall()
        first.markUsedUp(OpenAlexRoute.Keyless, 600.0)
        val second = OpenAlexQuota(prefs, hasBuiltInKey = true) { clock }
        assertNull(second.meteredRoute())
    }

    @Test
    fun theRouteAfterSharedIsKeylessAndAfterKeylessNothing() {
        val quota = quota()
        assertEquals(OpenAlexRoute.Keyless, quota.routeAfter(OpenAlexRoute.Shared, metered = true))
        assertNull(quota.routeAfter(OpenAlexRoute.Keyless, metered = true))
        quota.markUsedUp(OpenAlexRoute.Keyless, 600.0)
        assertNull(quota.routeAfter(OpenAlexRoute.Shared, metered = true))
        assertEquals(OpenAlexRoute.Keyless, quota.routeAfter(OpenAlexRoute.Shared, metered = false))
        assertNull(quota.routeAfter(OpenAlexRoute.Keyless, metered = false))
    }

    @Test
    fun newLimitsApplyToTheNextRoute() {
        val quota = quota()
        quota.recordSharedCall()
        prefs.limits = limits(1)
        assertEquals(OpenAlexRoute.Keyless, quota.meteredRoute())
    }
}
