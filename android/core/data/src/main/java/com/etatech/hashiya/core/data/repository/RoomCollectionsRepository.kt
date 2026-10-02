package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.database.dao.CollectionDao
import com.etatech.hashiya.core.model.PaperCollection
import com.etatech.hashiya.core.model.collectionNameKey
import com.etatech.hashiya.core.model.isValidCollectionName
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomCollectionsRepository(private val collectionDao: CollectionDao, private val now: () -> Long) : CollectionsRepository {
    @Inject
    constructor(collectionDao: CollectionDao) : this(collectionDao, System::currentTimeMillis)

    override fun observeCollections(): Flow<List<PaperCollection>> =
        collectionDao.observeCollections().map { rows -> rows.map { PaperCollection(it.id, it.name, it.paperCount) } }

    override fun observeCollectionIds(openAlexId: String): Flow<Set<Long>> =
        collectionDao.observeCollectionIdsForPaper(openAlexId).map { it.toSet() }

    override suspend fun create(name: String): CollectionResult {
        if (!isValidCollectionName(name)) return CollectionResult.InvalidName
        val id = collectionDao.insertCollection(name.trim(), collectionNameKey(name), now()) ?: return CollectionResult.NameTaken
        return CollectionResult.Done(id)
    }

    override suspend fun rename(id: Long, name: String): CollectionResult {
        if (!isValidCollectionName(name)) return CollectionResult.InvalidName
        if (collectionDao.renameCollection(id, name.trim(), collectionNameKey(name))) return CollectionResult.Done(id)
        return if (collectionDao.collectionExists(id)) CollectionResult.NameTaken else CollectionResult.NotFound
    }

    override suspend fun delete(id: Long) = collectionDao.deleteCollection(id)

    override suspend fun setMembership(collectionId: Long, openAlexId: String, member: Boolean) {
        if (member) {
            collectionDao.addToCollection(collectionId, openAlexId, now())
        } else {
            collectionDao.removeFromCollection(collectionId, openAlexId)
        }
    }
}
