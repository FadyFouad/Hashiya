package com.etatech.hashiya.analytics

import android.content.Context
import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.NoOpAnalytics
import com.etatech.hashiya.crash.isDebuggable
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AnalyticsModule {
    @Provides
    @Singleton
    fun provideAnalytics(@ApplicationContext context: Context): Analytics =
        if (isDebuggable(context)) NoOpAnalytics else FirebaseAnalyticsTracker(FirebaseAnalytics.getInstance(context))
}
