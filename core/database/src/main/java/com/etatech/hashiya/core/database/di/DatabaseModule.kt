package com.etatech.hashiya.core.database.di

import android.content.Context
import androidx.room.Room
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.database.dao.PaperDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HashiyaDatabase =
        Room.databaseBuilder(context, HashiyaDatabase::class.java, "hashiya.db").build()

    @Provides
    fun providePaperDao(database: HashiyaDatabase): PaperDao = database.paperDao()
}
