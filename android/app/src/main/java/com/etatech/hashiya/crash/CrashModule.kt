package com.etatech.hashiya.crash

import com.etatech.hashiya.core.crash.CrashReporter
import com.etatech.hashiya.core.crash.NoOpCrashReporter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the crash reporter. For now every build gets the no-op one; release builds get Firebase later. */
@Module
@InstallIn(SingletonComponent::class)
object CrashModule {
    @Provides
    @Singleton
    fun provideCrashReporter(): CrashReporter = NoOpCrashReporter
}
