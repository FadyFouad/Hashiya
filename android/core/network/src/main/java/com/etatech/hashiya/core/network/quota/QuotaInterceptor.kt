package com.etatech.hashiya.core.network.quota

import com.etatech.hashiya.core.network.API_KEY_PARAM
import com.etatech.hashiya.core.network.ApiKeyKind
import com.etatech.hashiya.core.network.DailyLimitException
import com.etatech.hashiya.core.network.UserApiKeySource
import com.etatech.hashiya.core.network.model.RequestRoute
import java.io.IOException
import java.io.InterruptedIOException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** Set on the response the interceptor returns, to say which route answered; never sent to a server. */
internal const val ROUTE_HEADER = "X-Hashiya-Route"

/**
 * Sends each OpenAlex request on its route. With the user's key, that key is the only route. Otherwise [quota] picks
 * the route (shared: the built-in key, or the limits' proxy with no key; else keyless) and learns from 429s when a
 * budget is used up. `GET /works` with `search` or `filter` is metered; `GET /works/{id}` is free and always goes out.
 */
internal class QuotaInterceptor(
    private val userApiKeySource: UserApiKeySource,
    private val builtInKey: String,
    private val quota: OpenAlexQuota,
    private val sleep: (Long) -> Unit = Thread::sleep
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request().newBuilder().removeHeader(ROUTE_HEADER).build()
        val userKey = userApiKeySource.userKey.value?.trim()?.takeIf { it.isNotEmpty() }
        if (userKey != null) {
            val response = chain.proceed(original.routed(routedUrl(original.url, proxy = null, key = userKey), ApiKeyKind.User))
            return response.tagged(RequestRoute.User)
        }

        val metered = original.isMetered()
        var route: OpenAlexRoute? = if (metered) quota.meteredRoute() else quota.lookupRoute()
        var waitedOnThisRoute = false
        while (route != null) {
            val current = route
            val proxy = if (current == OpenAlexRoute.Shared) quota.limits.baseUrl else null
            val key = if (current == OpenAlexRoute.Shared && proxy == null) builtInKey.trim().takeIf { it.isNotEmpty() } else null
            if (metered && current == OpenAlexRoute.Shared) quota.recordSharedCall()

            val request = original.routed(routedUrl(original.url, proxy, key), if (key != null) ApiKeyKind.BuiltIn else null)
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                if (proxy == null || chain.call().isCanceled()) throw e
                route = quota.routeAfter(current, metered)
                waitedOnThisRoute = false
                continue
            }

            when {
                response.isSuccessful -> {
                    return response.tagged(if (current == OpenAlexRoute.Shared) RequestRoute.Shared else RequestRoute.Keyless)
                }

                response.code == TOO_MANY_REQUESTS && response.budgetUsedUp() -> {
                    response.close()
                    quota.markUsedUp(current, response.number(RESET))
                    route = quota.routeAfter(current, metered)
                    waitedOnThisRoute = false
                }

                response.code == TOO_MANY_REQUESTS && !waitedOnThisRoute -> {
                    response.close()
                    waitedOnThisRoute = true
                    val seconds = minOf(response.number(RETRY_AFTER)?.coerceAtLeast(0.0) ?: 1.0, MAX_WAIT_SECONDS)
                    try {
                        sleep((seconds * 1000).toLong())
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        throw InterruptedIOException("Canceled")
                    }
                    if (chain.call().isCanceled()) throw IOException("Canceled")
                }

                response.code in SERVER_ERRORS && proxy != null -> {
                    response.close()
                    route = quota.routeAfter(current, metered)
                    waitedOnThisRoute = false
                }

                else -> return response
            }
        }
        if (metered) {
            // Out of routes but not out for the day: the proxy failed and keyless is used up.
            if (quota.meteredRoute() != null) return original.failed(SERVICE_UNAVAILABLE, "Service Unavailable")
            throw DailyLimitException(resetAtMillis = quota.nextAvailable())
        }
        return original.failed(TOO_MANY_REQUESTS, "Too Many Requests")
    }

    private fun Request.isMetered(): Boolean =
        url.encodedPath.endsWith("/works") && (url.queryParameter("search") != null || url.queryParameter("filter") != null)

    private fun Request.routed(url: HttpUrl, kind: ApiKeyKind?): Request =
        newBuilder().url(url).apply { if (kind != null) tag(ApiKeyKind::class.java, kind) }.build()

    private fun Request.failed(code: Int, message: String): Response = Response.Builder()
        .request(this)
        .protocol(Protocol.HTTP_1_1)
        .code(code)
        .message(message)
        .body("".toResponseBody())
        .build()

    private fun Response.tagged(route: RequestRoute): Response = newBuilder().header(ROUTE_HEADER, route.name).build()

    /** `X-RateLimit-Remaining` at or below zero: the budget is used up for today. */
    private fun Response.budgetUsedUp(): Boolean = number(REMAINING)?.let { it <= 0 } ?: false

    /** A header as a number; "nan" and "Infinity" mean nothing here. */
    private fun Response.number(name: String): Double? = header(name)?.trim()?.toDoubleOrNull()?.takeIf { it.isFinite() }

    private companion object {
        const val TOO_MANY_REQUESTS = 429
        const val SERVICE_UNAVAILABLE = 503
        val SERVER_ERRORS = 500..599
        const val MAX_WAIT_SECONDS = 3.0
        const val REMAINING = "X-RateLimit-Remaining"
        const val RESET = "X-RateLimit-Reset"
        const val RETRY_AFTER = "Retry-After"
    }
}

/**
 * [url] sent to [proxy] when set (its scheme, userinfo, host, port and path prefix; the request's path and query) or
 * else to its own host, without any `api_key` it had and with [key] when set.
 */
internal fun routedUrl(url: HttpUrl, proxy: HttpUrl?, key: String?): HttpUrl {
    val builder = if (proxy == null) {
        url.newBuilder()
    } else {
        // A trailing empty segment ("/openalex/") is replaced by the first segment added.
        proxy.newBuilder()
            .fragment(null)
            .apply { url.encodedPathSegments.forEach(::addEncodedPathSegment) }
            .encodedQuery(url.encodedQuery)
    }
    builder.removeAllQueryParameters(API_KEY_PARAM)
    if (key != null) builder.addQueryParameter(API_KEY_PARAM, key)
    return builder.build()
}
