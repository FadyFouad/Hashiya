package com.etatech.hashiya.core.network

import com.etatech.hashiya.core.network.quota.OpenAlexLimits
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

internal const val APP_CONFIG_URL = "https://fadyfouad.github.io/Hashiya-Privacy-Policy/app-config.json"

/** The app's remote config on GitHub Pages: the minimum supported build. Sends no key and nothing about the user. */
interface AppConfigDataSource {
    /**
     * The Android entry (null when the file has none) and the OpenAlex limits (defaults when absent or invalid).
     * @throws NetworkException when the file can't be fetched, answers with an error, or can't be read.
     */
    suspend fun fetch(): RemoteAppConfig
}

data class RemoteAppConfig(val android: NetworkPlatformConfig?, val openAlex: OpenAlexLimits)

data class NetworkPlatformConfig(val minimumVersionCode: Long? = null, val storeUrl: String? = null)

/** The minimum stays raw because the decoder reads `"2"` as 2 for a Long; only an unquoted whole number counts. */
@Serializable
internal data class RawPlatformConfig(val minimumVersionCode: JsonElement? = null, val storeUrl: String? = null)

/** A plain client with short timeouts, so a slow GitHub never holds the check for long. */
internal fun buildAppConfigOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(5, TimeUnit.SECONDS)
    .callTimeout(10, TimeUnit.SECONDS)
    .build()

internal class OkHttpAppConfigDataSource(private val client: OkHttpClient, private val url: HttpUrl) : AppConfigDataSource {
    override suspend fun fetch(): RemoteAppConfig {
        val body = client.newCall(Request.Builder().url(url).build()).awaitBody()
        val raw: RawPlatformConfig?
        val json: JsonObject
        try {
            json = OpenAlexJson.parseToJsonElement(body) as? JsonObject ?: throw NetworkException(NetworkFailure.MalformedResponse)
            raw = json["android"]?.takeUnless { it is JsonNull }?.let { OpenAlexJson.decodeFromJsonElement<RawPlatformConfig>(it) }
        } catch (e: IllegalArgumentException) {
            // SerializationException is an IllegalArgumentException.
            throw NetworkException(NetworkFailure.MalformedResponse, e)
        }
        val android = raw?.let {
            NetworkPlatformConfig(minimumVersionCode = it.minimumVersionCode.toVersionCode(), storeUrl = it.storeUrl)
        }
        return RemoteAppConfig(android, OpenAlexLimits.parse(json["openAlex"]))
    }
}

private fun JsonElement?.toVersionCode(): Long? {
    if (this == null || this is JsonNull) return null
    return (this as? JsonPrimitive)?.takeUnless { it.isString }?.content?.toLongOrNull()
        ?: throw NetworkException(NetworkFailure.MalformedResponse)
}
