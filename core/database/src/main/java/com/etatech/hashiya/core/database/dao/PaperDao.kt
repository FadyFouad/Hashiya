package com.etatech.hashiya.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.etatech.hashiya.core.database.model.DeletedPaper
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperNotesEntity
import com.etatech.hashiya.core.database.model.PaperSearchEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.database.model.StatusCount
import com.etatech.hashiya.core.database.model.asEntity
import com.etatech.hashiya.core.database.model.notesSearchText
import com.etatech.hashiya.core.model.PaperNotes
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

    /** The Details screen's paper; emits null once it is deleted. */
    @Transaction
    @Query("SELECT * FROM papers WHERE open_alex_id = :openAlexId")
    abstract fun observeByOpenAlexId(openAlexId: String): Flow<PaperWithAuthors?>

    /** The paper's notes, or null when it has none (or isn't saved). */
    @Query(
        """
        SELECT paper_notes.* FROM paper_notes
        JOIN papers ON papers.id = paper_notes.paper_id
        WHERE papers.open_alex_id = :openAlexId
        """
    )
    abstract fun observeNotes(openAlexId: String): Flow<PaperNotesEntity?>

    /** Returns the number of papers changed: 0 when the paper isn't saved. The search index is not touched. */
    @Query("UPDATE papers SET reading_status = :status WHERE open_alex_id = :openAlexId")
    abstract suspend fun setStatus(openAlexId: String, status: String): Int

    // Building blocks of the transactions below; protected so a paper is never written without its authors, search row and notes.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertPaper(paper: PaperEntity): Long

    @Insert
    protected abstract suspend fun insertAuthors(authors: List<PaperAuthorEntity>)

    @Insert
    protected abstract suspend fun insertSearch(search: PaperSearchEntity)

    @Query("DELETE FROM papers WHERE id = :id")
    protected abstract suspend fun deleteById(id: String)

    // FTS rows don't cascade, so every paper deletion deletes its search row too.
    @Query("DELETE FROM paper_search WHERE paper_id = :id")
    protected abstract suspend fun deleteSearchById(id: String)

    @Query("SELECT id FROM papers WHERE open_alex_id = :openAlexId")
    protected abstract suspend fun getPaperId(openAlexId: String): String?

    @Query("SELECT * FROM paper_notes WHERE paper_id = :paperId")
    protected abstract suspend fun getNotes(paperId: String): PaperNotesEntity?

    @Upsert
    protected abstract suspend fun upsertNotes(notes: PaperNotesEntity)

    @Query("DELETE FROM paper_notes WHERE paper_id = :paperId")
    protected abstract suspend fun deleteNotes(paperId: String)

    @Query("UPDATE paper_search SET notes = :text WHERE paper_id = :paperId")
    protected abstract suspend fun setSearchNotes(paperId: String, text: String)

    /**
     * Writes the paper, its authors, its search row and (on a restore) its [notes] atomically.
     * Returns false, writing nothing, if it is already saved. [search] must already hold the notes' search text.
     */
    @Transaction
    open suspend fun insertPaperWithAuthors(
        paper: PaperEntity,
        authors: List<PaperAuthorEntity>,
        search: PaperSearchEntity,
        notes: PaperNotesEntity? = null
    ): Boolean {
        require(search.paperId == paper.id) { "The search row must belong to the paper" }
        require(notes == null || notes.paperId == paper.id) { "The notes must belong to the paper" }
        if (insertPaper(paper) == -1L) return false
        insertAuthors(authors)
        insertSearch(search)
        notes?.let { upsertNotes(it) }
        return true
    }

    /** Deletes the paper (authors and notes cascade) and its search row, and returns what was deleted, so it can be restored. */
    @Transaction
    open suspend fun deleteByOpenAlexId(openAlexId: String): DeletedPaper? {
        val existing = getByOpenAlexId(openAlexId) ?: return null
        val notes = getNotes(existing.paper.id)
        deleteById(existing.paper.id)
        deleteSearchById(existing.paper.id)
        return DeletedPaper(existing, notes)
    }

    /**
     * Stores [notes] (blank notes delete the row) and puts their text in the search index, atomically.
     * Returns false, writing nothing, if the paper isn't saved.
     */
    @Transaction
    open suspend fun saveNotes(openAlexId: String, notes: PaperNotes, updatedAt: Long): Boolean {
        val paperId = getPaperId(openAlexId) ?: return false
        if (notes.isEmpty) deleteNotes(paperId) else upsertNotes(notes.asEntity(paperId, updatedAt))
        setSearchNotes(paperId, notesSearchText(notes))
        return true
    }
}
