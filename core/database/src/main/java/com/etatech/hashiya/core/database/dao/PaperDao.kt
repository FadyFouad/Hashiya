package com.etatech.hashiya.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperSearchEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.database.model.StatusCount
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PaperDao {
    /** Newest saved first. [match] is an FTS MATCH expression and [status] a stored status; null means "any". */
    @Transaction
    @Query(
        """
        SELECT papers.* FROM papers
        WHERE (:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))
          AND (:status IS NULL OR papers.reading_status = :status)
        ORDER BY papers.saved_at DESC
        """
    )
    abstract fun observeLibrary(match: String?, status: String?): Flow<List<PaperWithAuthors>>

    /** How many papers matching [match] (null = all) have each stored status. Statuses with none are missing. */
    @Query(
        """
        SELECT reading_status, COUNT(*) AS count FROM papers
        WHERE (:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))
        GROUP BY reading_status
        """
    )
    abstract fun observeStatusCounts(match: String?): Flow<List<StatusCount>>

    @Query("SELECT open_alex_id FROM papers WHERE open_alex_id IS NOT NULL")
    abstract fun observeSavedOpenAlexIds(): Flow<List<String>>

    @Transaction
    @Query("SELECT * FROM papers WHERE open_alex_id = :openAlexId")
    abstract suspend fun getByOpenAlexId(openAlexId: String): PaperWithAuthors?

    /** Returns the number of papers changed: 0 when the paper isn't saved. The search index is not touched. */
    @Query("UPDATE papers SET reading_status = :status WHERE open_alex_id = :openAlexId")
    abstract suspend fun setStatus(openAlexId: String, status: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertPaper(paper: PaperEntity): Long

    @Insert
    abstract suspend fun insertAuthors(authors: List<PaperAuthorEntity>)

    @Insert
    abstract suspend fun insertSearch(search: PaperSearchEntity)

    @Query("DELETE FROM papers WHERE id = :id")
    abstract suspend fun deleteById(id: String)

    // FTS rows don't cascade, so every paper deletion deletes its search row too.
    @Query("DELETE FROM paper_search WHERE paper_id = :id")
    abstract suspend fun deleteSearchById(id: String)

    /** Writes the paper, its authors and its search row atomically. Returns false, writing nothing, if it is already saved. */
    @Transaction
    open suspend fun insertPaperWithAuthors(paper: PaperEntity, authors: List<PaperAuthorEntity>, search: PaperSearchEntity): Boolean {
        require(search.paperId == paper.id) { "The search row must belong to the paper" }
        if (insertPaper(paper) == -1L) return false
        insertAuthors(authors)
        insertSearch(search)
        return true
    }

    /** Deletes the paper (authors cascade) and its search row, and returns what was deleted, so it can be restored. */
    @Transaction
    open suspend fun deleteByOpenAlexId(openAlexId: String): PaperWithAuthors? {
        val existing = getByOpenAlexId(openAlexId) ?: return null
        deleteById(existing.paper.id)
        deleteSearchById(existing.paper.id)
        return existing
    }
}
