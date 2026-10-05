package com.etatech.hashiya.core.network.quota

/** How a request without the user's key goes out. */
enum class OpenAlexRoute { Shared, Keyless }

/**
 * Chooses the route for requests without a user key and remembers, in [prefs]: the metered calls sent on the shared
 * route today (by UTC day, as OpenAlex resets at midnight UTC), and until when OpenAlex said each budget is used up.
 * Nothing here leaves the device.
 */
class OpenAlexQuota(
    private val prefs: QuotaPreferences,
    private val hasBuiltInKey: Boolean,
    private val now: () -> Long = System::currentTimeMillis
) {
    /** Read on every request, so limits fetched later apply to the next one. */
    val limits: OpenAlexLimits get() = prefs.limits

    /** The route for a search or filter list: Shared while under the cap and not used up, else Keyless, else null (out). */
    @Synchronized
    fun meteredRoute(): OpenAlexRoute? {
        val time = now()
        if (sharedOpensForMetered(time) <= time) return OpenAlexRoute.Shared
        return if (prefs.keylessUsedUpUntil <= time) OpenAlexRoute.Keyless else null
    }

    /** The route for a free lookup: Shared unless it is used up (the cap counts metered calls only), else Keyless. */
    @Synchronized
    fun lookupRoute(): OpenAlexRoute =
        if (sharedConfigured && prefs.sharedUsedUpUntil <= now()) OpenAlexRoute.Shared else OpenAlexRoute.Keyless

    /** Where a request goes after [route] was used up or failed: Keyless after Shared (for metered calls only while it isn't used up). */
    @Synchronized
    fun routeAfter(route: OpenAlexRoute, metered: Boolean): OpenAlexRoute? = when {
        route != OpenAlexRoute.Shared -> null
        !metered -> OpenAlexRoute.Keyless
        prefs.keylessUsedUpUntil <= now() -> OpenAlexRoute.Keyless
        else -> null
    }

    /** One metered call sent on the shared route. */
    @Synchronized
    fun recordSharedCall() {
        val today = utcDay(now())
        val calls = if (prefs.sharedCallsDay == today) prefs.sharedCalls else 0
        prefs.sharedCallsDay = today
        prefs.sharedCalls = calls + 1
    }

    /**
     * OpenAlex said [route]'s budget is used up. [resetInSeconds] is its `X-RateLimit-Reset`; null, non-finite,
     * negative or more than two days means the next midnight UTC.
     */
    @Synchronized
    fun markUsedUp(route: OpenAlexRoute, resetInSeconds: Double?) {
        val time = now()
        val until = resetInSeconds
            ?.takeIf { it.isFinite() && it >= 0 && it <= 2 * DAY_SECONDS }
            ?.let { time + (it * 1000).toLong() }
            ?: nextUtcMidnight(time)
        if (route == OpenAlexRoute.Shared) prefs.sharedUsedUpUntil = until else prefs.keylessUsedUpUntil = until
    }

    /** When a search can go out again, in epoch millis: the earlier of the shared and keyless routes opening. */
    @Synchronized
    fun nextAvailable(): Long {
        val time = now()
        return maxOf(minOf(sharedOpensForMetered(time), prefs.keylessUsedUpUntil), time)
    }

    private val sharedConfigured get() = hasBuiltInKey || prefs.limits.baseUrl != null

    /** [Long.MAX_VALUE] when the shared route is off for metered calls. */
    private fun sharedOpensForMetered(time: Long): Long {
        val cap = prefs.limits.dailyDeviceCalls
        if (!sharedConfigured || cap <= 0) return Long.MAX_VALUE
        val calls = if (prefs.sharedCallsDay == utcDay(time)) prefs.sharedCalls else 0
        val capOpens = if (calls >= cap) nextUtcMidnight(time) else time
        return maxOf(capOpens, prefs.sharedUsedUpUntil)
    }

    private companion object {
        const val DAY_SECONDS = 86_400.0
        const val DAY_MILLIS = 86_400_000L

        fun utcDay(time: Long) = Math.floorDiv(time, DAY_MILLIS)

        fun nextUtcMidnight(time: Long) = (utcDay(time) + 1) * DAY_MILLIS
    }
}
