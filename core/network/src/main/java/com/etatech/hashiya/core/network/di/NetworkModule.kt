package com.etatech.hashiya.core.network.di

import com.etatech.hashiya.core.network.BuildConfig
import com.etatech.hashiya.core.network.OPENALEX_BASE_URL
import com.etatech.hashiya.core.network.OpenAlexApi
import com.etatech.hashiya.core.network.OpenAlexDataSource
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import com.etatech.hashiya.core.network.RetrofitOpenAlexDataSource
import com.etatech.hashiya.core.network.RetrofitOpenAlexLookupDataSource
import com.etatech.hashiya.core.network.UserApiKeySource
import com.etatech.hashiya.core.network.buildOpenAlexApi
import com.etatech.hashiya.core.network.buildOpenAlexOkHttpClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(userApiKeySource: UserApiKeySource): OkHttpClient = buildOpenAlexOkHttpClient(
        userApiKeySource = userApiKeySource,
        builtInKey = BuildConfig.OPENALEX_API_KEY,
        logger = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Logger.DEFAULT else null
    )

    @Provides
    @Singleton
    fun provideOpenAlexApi(client: OkHttpClient): OpenAlexApi = buildOpenAlexApi(OPENALEX_BASE_URL.toHttpUrl(), client)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkBindingsModule {
    @Binds
    abstract fun bindOpenAlexDataSource(impl: RetrofitOpenAlexDataSource): OpenAlexDataSource

    @Binds
    abstract fun bindOpenAlexLookupDataSource(impl: RetrofitOpenAlexLookupDataSource): OpenAlexLookupDataSource
}
