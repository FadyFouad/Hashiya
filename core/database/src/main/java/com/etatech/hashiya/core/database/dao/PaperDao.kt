package com.etatech.hashiya.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.etatech.hashiya.core.database.model.CollectionPaperEntity
import com.etatech.hashiya.core.database.model.DeletedPaper
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperNotesEntity
import com.etatech.hashiya.core.database.model.PaperSearchEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.database.model.PdfColumns
import com.etatech.hashiya.core.database.model.PdfStorageRow
import com.etatech.hashiya.core.database.model.StatusCount
import com.etatech.hashiya.core.database.model.asEntity
import com.etatech.hashiya.core.database.model.notesSearchText
import com.etatech.hashiya.core.model.PaperNotes
import kotlinx.coroutines.flow.Flow

@Dao
abstract class PaperDao {
    /** Newest saved first. [match] is an FTS MATCH expression, [status] a stored status, [collectionId] a collection; null means "any". */
    @Transaction
    @Query(
        """
        SELECT papers.* FROM papers
        WHERE (:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))
          AND (:status IS NULL OR papers.reading_status = :status)
          AND (:collectionId IS NULL OR papers.id IN (SELECT paper_id FROM collection_papers WHERE collection_id = :collectionId))
        ORDER BY papers.saved_at DESC
        """
    )
    abstract fun observeLibrary(match: String?, status: String?, collectionId: Long?): Flow<List<PaperWithAuthors>>

    /**
     * How many papers matching [match] (null = all) in [collectionId] (null = all) have each stored status. Statuses with none are missing.
     */
    @Query(
        """
        SELECT reading_status, COUNT(*) AS count FROM papers
        WHERE (:match IS NULL OR papers.id IN (SELECT paper_id FROM paper_search WHERE paper_search MATCH :match))
          AND (:collectionId IS NULL OR papers.id IN (SELECT paper_id FROM collection_papers WHERE collection_id = :collectionId))
        GROUP BY reading_status
        """
    )
    abstract fun observeStatusCounts(match: String?, collectionId: Long?): Flow<List<StatusCount>>

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

    /** The local id of a saved paper; PDF files are named after it. Null when the paper isn't saved. */
    @Query("SELECT id FROM papers WHERE open_alex_id = :openAlexId")
    abstract suspend fun paperIdFor(openAlexId: String): String?

    /** The paper's stored PDF; null when it has none or isn't saved. */
    @Query(
        """
        SELECT pdf_source AS source, pdf_size AS size, pdf_added_at AS addedAt, COALESCE(pdf_last_page, 0) AS lastPage
        FROM papers WHERE open_alex_id = :openAlexId AND pdf_source IS NOT NULL
        """
    )
    abstract fun observePdf(openAlexId: String): Flow<PdfColumns?>

    /** Records a newly stored PDF, starting on its first page. Does nothing when [paperId] isn't saved. */
    @Query(
        """
        UPDATE papers SET pdf_source = :source, pdf_size = :size, pdf_added_at = :addedAt, pdf_last_page = 0
        WHERE id = :paperId
        """
    )
    abstract suspend fun setPdf(paperId: String, source: String, size: Long, addedAt: Long)

    @Query("UPDATE papers SET pdf_source = NULL, pdf_size = NULL, pdf_added_at = NULL, pdf_last_page = NULL WHERE id = :paperId")
    abstract suspend fun clearPdf(paperId: String)

    @Query("UPDATE papers SET pdf_last_page = :page WHERE id = :paperId AND pdf_source IS NOT NULL")
    abstract suspend fun setPdfLastPage(paperId: String, page: Int)

    /** Every paper with a stored PDF, for the startup sweep of orphaned files. */
    @Query("SELECT id FROM papers WHERE pdf_source IS NOT NULL")
    abstract suspend fun pdfPaperIds(): List<String>

    @Query("SELECT id FROM papers WHERE pdf_source = 'downloaded'")
    abstract suspend fun downloadedPdfPaperIds(): List<String>

    /** Bytes and counts of stored PDFs by source; sources other than "downloaded" count as attached, like [pdfSourceOf]. */
    @Query(
        """
        SELECT
          COALESCE(SUM(CASE WHEN pdf_source = 'downloaded' THEN pdf_size END), 0) AS downloadedBytes,
          COUNT(CASE WHEN pdf_source = 'downloaded' THEN 1 END) AS downloadedCount,
          COALESCE(SUM(CASE WHEN pdf_source IS NOT NULL AND pdf_source != 'downloaded' THEN pdf_size END), 0) AS attachedBytes,
          COUNT(CASE WHEN pdf_source IS NOT NULL AND pdf_source != 'downloaded' THEN 1 END) AS attachedCount
        FROM papers
        """
    )
    abstract suspend fun pdfStorage(): PdfStorageRow

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

    @Query("SELECT * FROM collection_papers WHERE paper_id = :paperId")
    protected abstract suspend fun getCollectionLinks(paperId: String): List<CollectionPaperEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM papers WHERE cite_key = :citeKey)")
    protected abstract suspend fun citeKeyTaken(citeKey: String): Boolean

    @Query(
        """
        INSERT OR IGNORE INTO collection_papers (collection_id, paper_id, added_at)
        SELECT id, :paperId, :addedAt FROM collections WHERE id = :collectionId
        """
    )
    protected abstract suspend fun insertLinkIfCollectionExists(collectionId: Long, paperId: String, addedAt: Long)

    /**
     * Writes the paper, its authors, its search row and (on a restore) its [notes] and [collectionLinks] atomically.
     * Links to collections deleted meanwhile are skipped, and a cite key another paper took meanwhile is dropped (it is
     * reassigned on the next export). Returns false, writing nothing, if it is already saved. [search] must already hold the
     * notes' search text.
     */
    @Transaction
    open suspend fun insertPaperWithAuthors(
        paper: PaperEntity,
        authors: List<PaperAuthorEntity>,
        search: PaperSearchEntity,
        notes: PaperNotesEntity? = null,
        collectionLinks: List<CollectionPaperEntity> = emptyList()
    ): Boolean {
        require(search.paperId == paper.id) { "The search row must belong to the paper" }
        require(notes == null || notes.paperId == paper.id) { "The notes must belong to the paper" }
        require(collectionLinks.all { it.paperId == paper.id }) { "The collection links must belong to the paper" }
        // An already saved paper holds its own key, so its copy loses the key here, but the insert is ignored anyway.
        val key = paper.citeKey
        val row = if (key != null && citeKeyTaken(key)) paper.copy(citeKey = null) else paper
        if (insertPaper(row) == -1L) return false
        insertAuthors(authors)
        insertSearch(search)
        notes?.let { upsertNotes(it) }
        collectionLinks.forEach { insertLinkIfCollectionExists(it.collectionId, it.paperId, it.addedAt) }
        return true
    }

    /**
     * Deletes the paper (authors, notes and collection links cascade) and its search row, and returns what was deleted, so it can be
     * restored.
     */
    @Transaction
    open suspend fun deleteByOpenAlexId(openAlexId: String): DeletedPaper? {
        val existing = getByOpenAlexId(openAlexId) ?: return null
        val notes = getNotes(existing.paper.id)
        val links = getCollectionLinks(existing.paper.id)
        deleteById(existing.paper.id)
        deleteSearchById(existing.paper.id)
        return DeletedPaper(existing, notes, links)
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
