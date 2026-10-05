package com.etatech.hashiya.crash

import android.content.Context
import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.core.crash.NoOpCrashReporter
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the crash reporter: Firebase in release builds, nothing in debug builds and tests. */
@Module
@InstallIn(SingletonComponent::class)
object CrashModule {
    @Provides
    @Singleton
    fun provideCrashReporter(@ApplicationContext context: Context): CrashReporter =
        if (isDebuggable(context)) NoOpCrashReporter else FirebaseCrashReporter(FirebaseCrashlytics.getInstance())
}
