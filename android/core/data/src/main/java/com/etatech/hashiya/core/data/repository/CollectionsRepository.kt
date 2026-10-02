package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.model.PaperCollection
import kotlinx.coroutines.flow.Flow

interface CollectionsRepository {
    /** Every collection with its paper count, sorted by name ignoring case. */
    fun observeCollections(): Flow<List<PaperCollection>>

    /** The collections [openAlexId] is in; empty when it is in none or isn't saved. */
    fun observeCollectionIds(openAlexId: String): Flow<Set<Long>>

    /**
     * Trims the name. [CollectionResult.InvalidName] unless it is 1–60 characters; [CollectionResult.NameTaken] if another collection has
     * it, ignoring case.
     */
    suspend fun create(name: String): CollectionResult

    /**
     * Same rules as [create]; renaming a collection to a new case of its own name is allowed. [CollectionResult.NotFound] when the
     * collection no longer exists.
     */
    suspend fun rename(id: Long, name: String): CollectionResult

    /** Deletes the collection; its papers stay in the library. */
    suspend fun delete(id: Long)

    /**
     * Adds or removes the paper. Adding a paper that isn't saved, or is already in the collection, does nothing.
     * Adding to a collection that no longer exists throws, like any other failed change.
     */
    suspend fun setMembership(collectionId: Long, openAlexId: String, member: Boolean)
}

sealed interface CollectionResult {
    /** Created or renamed; [id] is the collection's. */
    data class Done(val id: Long) : CollectionResult

    data object NameTaken : CollectionResult

    data object InvalidName : CollectionResult

    /** Only from [CollectionsRepository.rename]: the collection was deleted, so nothing was renamed. */
    data object NotFound : CollectionResult
}
