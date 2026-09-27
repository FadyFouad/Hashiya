package com.etatech.hashiya.core.network

import kotlinx.coroutines.flow.StateFlow
import okhttp3.Interceptor
import okhttp3.Response

internal const val API_KEY_PARAM = "api_key"

/** The user's own OpenAlex key, or null to use the built-in one. Implemented in `core/data`. */
interface UserApiKeySource {
    val userKey: StateFlow<String?>
}

internal enum class ApiKeyKind { User, BuiltIn }

/** Adds `api_key` to every request and tags the request with which kind of key was used. */
internal class ApiKeyInterceptor(private val userApiKeySource: UserApiKeySource, private val builtInKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val userKey = userApiKeySource.userKey.value?.takeIf { it.isNotBlank() }
        val (key, kind) = if (userKey != null) userKey to ApiKeyKind.User else builtInKey to ApiKeyKind.BuiltIn
        val original = chain.request()
        val url = if (key.isBlank()) {
            original.url
        } else {
            original.url.newBuilder().addQueryParameter(API_KEY_PARAM, key).build()
        }
        val request = original.newBuilder()
            .url(url)
            .tag(ApiKeyKind::class.java, kind)
            .build()
        return chain.proceed(request)
    }
}
