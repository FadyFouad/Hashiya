package com.etatech.hashiya.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.etatech.hashiya.core.database.model.CollectionEntity
import com.etatech.hashiya.core.database.model.CollectionWithCount
import kotlinx.coroutines.flow.Flow

@Dao
abstract class CollectionDao {
    /** Every collection with its paper count, sorted by the name key, so case never changes the order. */
    @Query(
        """
        SELECT collections.id, collections.name, COUNT(collection_papers.paper_id) AS paper_count FROM collections
        LEFT JOIN collection_papers ON collection_papers.collection_id = collections.id
        GROUP BY collections.id
        ORDER BY collections.name_key
        """
    )
    abstract fun observeCollections(): Flow<List<CollectionWithCount>>

    @Query(
        """
        SELECT collection_papers.collection_id FROM collection_papers
        JOIN papers ON papers.id = collection_papers.paper_id
        WHERE papers.open_alex_id = :openAlexId
        ORDER BY collection_papers.collection_id
        """
    )
    abstract fun observeCollectionIdsForPaper(openAlexId: String): Flow<List<Long>>

    @Query("SELECT EXISTS(SELECT 1 FROM collections WHERE id = :id)")
    abstract suspend fun collectionExists(id: Long): Boolean

    @Query("SELECT id FROM collections WHERE name_key = :nameKey")
    protected abstract suspend fun idForNameKey(nameKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insert(collection: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name, name_key = :nameKey WHERE id = :id")
    protected abstract suspend fun updateName(id: Long, name: String, nameKey: String): Int

    /** Returns the new id, or null when another collection already has [nameKey]. */
    @Transaction
    open suspend fun insertCollection(name: String, nameKey: String, createdAt: Long): Long? {
        if (idForNameKey(nameKey) != null) return null
        return insert(CollectionEntity(name = name, nameKey = nameKey, createdAt = createdAt))
    }

    /**
     * Returns false, changing nothing, when another collection already has [nameKey] or no collection has [id]. Renaming to a new case
     * of the same name is allowed.
     */
    @Transaction
    open suspend fun renameCollection(id: Long, name: String, nameKey: String): Boolean {
        val owner = idForNameKey(nameKey)
        if (owner != null && owner != id) return false
        return updateName(id, name, nameKey) > 0
    }

    /** Its links cascade; its papers stay. */
    @Query("DELETE FROM collections WHERE id = :id")
    abstract suspend fun deleteCollection(id: Long)

    /** Does nothing when the paper isn't saved or is already in the collection. */
    @Query(
        """
        INSERT OR IGNORE INTO collection_papers (collection_id, paper_id, added_at)
        SELECT :collectionId, id, :addedAt FROM papers WHERE open_alex_id = :openAlexId
        """
    )
    abstract suspend fun addToCollection(collectionId: Long, openAlexId: String, addedAt: Long)

    @Query(
        """
        DELETE FROM collection_papers
        WHERE collection_id = :collectionId AND paper_id IN (SELECT id FROM papers WHERE open_alex_id = :openAlexId)
        """
    )
    abstract suspend fun removeFromCollection(collectionId: Long, openAlexId: String)
}
