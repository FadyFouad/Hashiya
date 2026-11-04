package com.etatech.hashiya.core.data.importer

import android.content.Context
import com.etatech.hashiya.core.data.repository.ImportRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ImportModule {
    @Binds
    abstract fun bindImportRepository(impl: DefaultImportRepository): ImportRepository

    @Binds
    abstract fun bindImportStore(impl: RoomImportStore): ImportStore

    @Binds
    abstract fun bindImportScheduler(impl: WorkManagerImportScheduler): ImportScheduler

    companion object {
        @Provides
        fun provideImportFileStore(@ApplicationContext context: Context): ImportFileStore =
            ImportFileStore(File(context.filesDir, "imports"))
    }
}
