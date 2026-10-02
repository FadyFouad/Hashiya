package com.etatech.hashiya.update

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppUpdateModule {
    @Provides
    fun provideInstalledVersionCode(@ApplicationContext context: Context): InstalledVersionCode = InstalledVersionCode {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
    }
}
