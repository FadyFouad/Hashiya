package com.etatech.hashiya.core.network

import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal const val APP_CONFIG_URL = "https://fadyfouad.github.io/Hashiya-Privacy-Policy/app-config.json"

/** The app's remote config on GitHub Pages: the minimum supported build. Sends no key and nothing about the user. */
interface AppConfigDataSource {
    /**
     * The Android entry, or null when the file has none.
     * @throws NetworkException when the file can't be fetched, answers with an error, or can't be read.
     */
    suspend fun androidConfig(): NetworkPlatformConfig?
}

data class NetworkPlatformConfig(val minimumVersionCode: Long? = null, val storeUrl: String? = null)

@Serializable
internal data class NetworkAppConfig(val android: RawPlatformConfig? = null)

/** The minimum stays raw because the decoder reads `"2"` as 2 for a Long; only an unquoted whole number counts. */
@Serializable
internal data class RawPlatformConfig(val minimumVersionCode: JsonElement? = null, val storeUrl: String? = null)

/** A plain client with short timeouts, so a slow GitHub never holds the check for long. */
internal fun buildAppConfigOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .build()

internal class OkHttpAppConfigDataSource(private val client: OkHttpClient, private val url: HttpUrl) : AppConfigDataSource {
    override suspend fun androidConfig(): NetworkPlatformConfig? {
        val body = client.newCall(Request.Builder().url(url).build()).awaitBody()
        val raw = try {
            OpenAlexJson.decodeFromString<NetworkAppConfig>(body).android
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            throw NetworkException(NetworkFailure.MalformedResponse, e)
        } ?: return null
        return NetworkPlatformConfig(minimumVersionCode = raw.minimumVersionCode.toVersionCode(), storeUrl = raw.storeUrl)
    }
}

private fun JsonElement?.toVersionCode(): Long? {
    if (this == null || this is JsonNull) return null
    return (this as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toLongOrNull()
        ?: throw NetworkException(NetworkFailure.MalformedResponse)
}
