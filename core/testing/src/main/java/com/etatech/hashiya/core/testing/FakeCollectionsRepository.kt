package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.CollectionResult
import com.etatech.hashiya.core.data.repository.CollectionsRepository
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.collectionNameKey
import com.etatech.hashiya.core.model.isValidCollectionName
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Collections kept in [library], so filtering the library by collection and Remove/Undo behave like Room. */
class FakeCollectionsRepository(private val library: FakeLibraryRepository) : CollectionsRepository {
    private var nextId = 1L

    /** When true, every write throws like a failing disk would. */
    var failOnChange = false

    override fun observeCollections(): Flow<List<PaperCollection>> =
        combine(library.collectionNames, library.memberships, library.observeSavedIds()) { names, members, saved ->
            names.map { (id, name) -> PaperCollection(id, name, members[id].orEmpty().count { it in saved }) }
                .sortedBy { collectionNameKey(it.name) }
        }

    override fun observeCollectionIds(openAlexId: String): Flow<Set<Long>> =
        library.memberships.map { all -> all.filterValues { openAlexId in it }.keys }

    override suspend fun create(name: String): CollectionResult {
        failIfAsked()
        if (!isValidCollectionName(name)) return CollectionResult.InvalidName
        if (taken(name, except = null)) return CollectionResult.NameTaken
        val id = nextId++
        library.collectionNames.update { it + (id to name.trim()) }
        return CollectionResult.Done(id)
    }

    override suspend fun rename(id: Long, name: String): CollectionResult {
        failIfAsked()
        if (!isValidCollectionName(name)) return CollectionResult.InvalidName
        if (id !in library.collectionNames.value) return CollectionResult.NotFound
        if (taken(name, except = id)) return CollectionResult.NameTaken
        library.collectionNames.update { it + (id to name.trim()) }
        return CollectionResult.Done(id)
    }

    override suspend fun delete(id: Long) {
        failIfAsked()
        library.collectionNames.update { it - id }
        library.memberships.update { it - id }
    }

    override suspend fun setMembership(collectionId: Long, openAlexId: String, member: Boolean) {
        failIfAsked()
        val exists = collectionId in library.collectionNames.value
        if (member) {
            // Like Room: an unsaved paper inserts nothing; a saved one into a missing collection breaks the foreign key.
            if (!library.isSavedPaper(openAlexId)) return
            check(exists) { "FOREIGN KEY constraint failed: no collection $collectionId" }
        } else if (!exists) {
            return
        }
        library.memberships.update { all ->
            val current = all[collectionId].orEmpty()
            all + (collectionId to if (member) current + openAlexId else current - openAlexId)
        }
    }

    private fun taken(name: String, except: Long?) =
        library.collectionNames.value.any { (id, existing) -> id != except && collectionNameKey(existing) == collectionNameKey(name) }

    private fun failIfAsked() {
        if (failOnChange) throw IOException("disk full")
    }
}
