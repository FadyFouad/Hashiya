package com.etatech.hashiya.core.data.di

import android.content.ContentResolver
import android.content.Context
import com.etatech.hashiya.core.data.pdf.PdfFileStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object PdfModule {
    /** `filesDir/pdfs`: private to the app and, unlike the cache, never cleared by the system. */
    @Provides
    @Singleton
    fun providePdfFileStore(@ApplicationContext context: Context): PdfFileStore = PdfFileStore(File(context.filesDir, "pdfs"))

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver
}
