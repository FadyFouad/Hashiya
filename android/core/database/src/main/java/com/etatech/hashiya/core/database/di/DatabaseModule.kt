package com.etatech.hashiya.core.database.di

import android.content.Context
import androidx.room.Room
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.dao.CitationDao
import com.etatech.hashiya.core.database.dao.CollectionDao
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.database.migration.MIGRATION_1_2
import com.etatech.hashiya.core.database.migration.MIGRATION_2_3
import com.etatech.hashiya.core.database.migration.MIGRATION_3_4
import com.etatech.hashiya.core.database.migration.MIGRATION_4_5
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    // No destructive fallback: a failing migration must never delete the user's library.
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HashiyaDatabase =
        Room.databaseBuilder(context, HashiyaDatabase::class.java, "hashiya.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()

    @Provides
    fun providePaperDao(database: HashiyaDatabase): PaperDao = database.paperDao()

    @Provides
    fun provideCollectionDao(database: HashiyaDatabase): CollectionDao = database.collectionDao()

    @Provides
    fun provideCitationDao(database: HashiyaDatabase): CitationDao = database.citationDao()
}
