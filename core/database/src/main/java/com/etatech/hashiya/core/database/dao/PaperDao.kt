package com.etatech.hashiya.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PaperDao {
    @Transaction
    @Query("SELECT * FROM papers ORDER BY saved_at DESC")
    abstract fun observeSavedPapers(): Flow<List<PaperWithAuthors>>

    @Query("SELECT open_alex_id FROM papers WHERE open_alex_id IS NOT NULL")
    abstract fun observeSavedOpenAlexIds(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM papers WHERE open_alex_id = :openAlexId")
    abstract suspend fun getByOpenAlexId(openAlexId: String): PaperWithAuthors?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPaper(paper: PaperEntity): Long

    @Insert
    abstract suspend fun insertAuthors(authors: List<PaperAuthorEntity>)

    @Query("DELETE FROM papers WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    /** Writes the paper and its authors atomically. Returns false, writing nothing, if it is already saved. */
    @Transaction
    open suspend fun insertPaperWithAuthors(paper: PaperEntity, authors: List<PaperAuthorEntity>): Boolean {
        if (insertPaper(paper) == -1L) return false
        insertAuthors(authors)
        return true
    }

    /** Deletes the paper (authors cascade) and returns what was deleted, so it can be restored. */
    @Transaction
    open suspend fun deleteByOpenAlexId(openAlexId: String): PaperWithAuthors? {
        val existing = getByOpenAlexId(openAlexId) ?: return null
        deleteById(existing.paper.id)
        return existing
    }
}
