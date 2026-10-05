package com.etatech.hashiya.core.network.di

import android.content.Context
import com.etatech.hashiya.core.network.APP_CONFIG_URL
import com.etatech.hashiya.core.network.ARXIV_BASE_URL
import com.etatech.hashiya.core.network.AppConfigDataSource
import com.etatech.hashiya.core.network.ArxivDataSource
import com.etatech.hashiya.core.network.BuildConfig
import com.etatech.hashiya.core.network.OPENALEX_BASE_URL
import com.etatech.hashiya.core.network.OkHttpAppConfigDataSource
import com.etatech.hashiya.core.network.OkHttpArxivDataSource
import com.etatech.hashiya.core.network.OkHttpPdfDownloadDataSource
import com.etatech.hashiya.core.network.OpenAlexApi
import com.etatech.hashiya.core.network.OpenAlexDataSource
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import com.etatech.hashiya.core.network.OpenAlexPdfLinksDataSource
import com.etatech.hashiya.core.network.PdfDownloadDataSource
import com.etatech.hashiya.core.network.RetrofitOpenAlexDataSource
import com.etatech.hashiya.core.network.RetrofitOpenAlexLookupDataSource
import com.etatech.hashiya.core.network.UserApiKeySource
import com.etatech.hashiya.core.network.buildAppConfigOkHttpClient
import com.etatech.hashiya.core.network.buildArxivOkHttpClient
import com.etatech.hashiya.core.network.buildOpenAlexApi
import com.etatech.hashiya.core.network.buildOpenAlexOkHttpClient
import com.etatech.hashiya.core.network.buildPdfOkHttpClient
import com.etatech.hashiya.core.network.quota.OpenAlexQuota
import com.etatech.hashiya.core.network.quota.QuotaInterceptor
import com.etatech.hashiya.core.network.quota.QuotaPreferences
import com.etatech.hashiya.core.network.quota.SearchCache
import com.etatech.hashiya.core.network.quota.SharedPreferencesQuotaPreferences
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor

@Module
@InstallIn(SingletonComponent::class)
internal object NetworkModule {
    @Provides
    @Singleton
    fun provideOkHttpClient(userApiKeySource: UserApiKeySource, quota: OpenAlexQuota): OkHttpClient = buildOpenAlexOkHttpClient(
        quotaInterceptor = QuotaInterceptor(userApiKeySource, BuildConfig.OPENALEX_API_KEY, quota),
        logger = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Logger.DEFAULT else null
    )

    /** One per process: it serializes the counters it keeps in [QuotaPreferences]. */
    @Provides
    @Singleton
    fun provideOpenAlexQuota(prefs: QuotaPreferences): OpenAlexQuota =
        OpenAlexQuota(prefs, hasBuiltInKey = BuildConfig.OPENALEX_API_KEY.isNotBlank())

    /** One per process: it locks per instance, so two over the same directory could interleave writes. */
    @Provides
    @Singleton
    fun provideSearchCache(@ApplicationContext context: Context): SearchCache = SearchCache(File(context.cacheDir, "openalex-search"))

    @Provides
    @Singleton
    fun provideOpenAlexApi(client: OkHttpClient): OpenAlexApi = buildOpenAlexApi(OPENALEX_BASE_URL.toHttpUrl(), client)

    @Provides
    @Singleton
    fun provideArxivDataSource(): ArxivDataSource = OkHttpArxivDataSource(buildArxivOkHttpClient(), ARXIV_BASE_URL.toHttpUrl())

    @Provides
    @Singleton
    fun provideAppConfigDataSource(): AppConfigDataSource =
        OkHttpAppConfigDataSource(buildAppConfigOkHttpClient(), APP_CONFIG_URL.toHttpUrl())

    @Provides
    @Singleton
    fun provideQuotaPreferences(@ApplicationContext context: Context): QuotaPreferences = SharedPreferencesQuotaPreferences(context)

    @Provides
    @Singleton
    fun providePdfDownloadDataSource(): PdfDownloadDataSource = OkHttpPdfDownloadDataSource(buildPdfOkHttpClient())
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NetworkBindingsModule {
    @Binds
    abstract fun bindOpenAlexDataSource(impl: RetrofitOpenAlexDataSource): OpenAlexDataSource

    @Binds
    abstract fun bindOpenAlexLookupDataSource(impl: RetrofitOpenAlexLookupDataSource): OpenAlexLookupDataSource

    @Binds
    abstract fun bindOpenAlexPdfLinksDataSource(impl: RetrofitOpenAlexLookupDataSource): OpenAlexPdfLinksDataSource
}
