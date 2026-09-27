package com.etatech.hashiya.core.network

import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** [logger] is null in release builds; when set, logs request lines with the API key redacted. */
internal fun buildOpenAlexOkHttpClient(
    userApiKeySource: UserApiKeySource,
    builtInKey: String,
    logger: HttpLoggingInterceptor.Logger?
): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .addInterceptor(ApiKeyInterceptor(userApiKeySource, builtInKey))
    .apply {
        if (logger != null) {
            addInterceptor(
                HttpLoggingInterceptor(logger).apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactQueryParams(API_KEY_PARAM)
                }
            )
        }
    }
    .build()

internal fun buildOpenAlexApi(baseUrl: HttpUrl, client: OkHttpClient): OpenAlexApi = Retrofit.Builder()
    .baseUrl(baseUrl)
    .client(client)
    .addConverterFactory(OpenAlexJson.asConverterFactory("application/json".toMediaType()))
    .build()
    .create(OpenAlexApi::class.java)
