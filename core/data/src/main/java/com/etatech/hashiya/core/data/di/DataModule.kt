package com.etatech.hashiya.core.data.di

import com.etatech.hashiya.core.data.DataStoreUserApiKeySource
import com.etatech.hashiya.core.data.repository.DataStoreUserPreferencesRepository
import com.etatech.hashiya.core.data.repository.LibraryRepository
import com.etatech.hashiya.core.data.repository.OpenAlexSearchRepository
import com.etatech.hashiya.core.data.repository.RoomLibraryRepository
import com.etatech.hashiya.core.data.repository.SearchRepository
import com.etatech.hashiya.core.data.repository.UserPreferencesRepository
import com.etatech.hashiya.core.network.UserApiKeySource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun bindSearchRepository(impl: OpenAlexSearchRepository): SearchRepository

    @Binds
    abstract fun bindLibraryRepository(impl: RoomLibraryRepository): LibraryRepository

    @Binds
    abstract fun bindUserPreferencesRepository(impl: DataStoreUserPreferencesRepository): UserPreferencesRepository

    @Binds
    abstract fun bindUserApiKeySource(impl: DataStoreUserApiKeySource): UserApiKeySource
}
