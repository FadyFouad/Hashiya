package com.etatech.hashiya.feature.reader.di

import com.etatech.hashiya.feature.reader.pdf.AndroidPdfPageSource
import com.etatech.hashiya.feature.reader.pdf.PdfPageSourceFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal object ReaderModule {
    @Provides
    fun providePdfPageSourceFactory(): PdfPageSourceFactory = PdfPageSourceFactory { file -> AndroidPdfPageSource.open(file) }
}
