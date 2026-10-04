package com.etatech.hashiya.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.etatech.hashiya.core.database.model.CollectionEntity
import com.etatech.hashiya.core.database.model.CollectionPaperEntity
import com.etatech.hashiya.core.database.model.IncomingCollection
import com.etatech.hashiya.core.database.model.IncomingPaper
import com.etatech.hashiya.core.database.model.LibrarySnapshot
import com.etatech.hashiya.core.database.model.MergeOutcome
import com.etatech.hashiya.core.database.model.PaperAuthorEntity
import com.etatech.hashiya.core.database.model.PaperEntity
import com.etatech.hashiya.core.database.model.PaperNotesEntity
import com.etatech.hashiya.core.database.model.PaperSearchEntity
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.database.model.PdfTotals
import com.etatech.hashiya.core.database.model.asPaperNotes
import com.etatech.hashiya.core.database.model.notesSearchText
import com.etatech.hashiya.core.database.model.searchEntityFor

/** Reads the library for an export and merges a backup into it. The device wins every conflict. */
@Dao
abstract class BackupDao {
    @Transaction
    open suspend fun snapshot(): LibrarySnapshot = LibrarySnapshot(allPapers(), allNotes(), allCollections(), allLinks())

    @Query("SELECT COUNT(*) FROM papers")
    abstract suspend fun paperCount(): Int

    @Query("SELECT COUNT(*) FROM collections")
    abstract suspend fun collectionCount(): Int

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(pdf_size), 0) AS bytes FROM papers WHERE pdf_source IS NOT NULL")
    abstract suspend fun pdfTotals(): PdfTotals

    /**
     * The local id of the saved paper a backup paper matches: by [openAlexId] when it has one, otherwise by [doi] (already
     * normalized). DOIs aren't unique here (a preprint and its published version can both be saved), so a paper with an OpenAlex
     * id never matches by DOI.
     */
    open suspend fun matchFor(openAlexId: String?, doi: String?): String? = when {
        openAlexId != null -> idForOpenAlexId(openAlexId)
        doi != null -> idForDoi(doi)
        else -> null
    }

    /** Adds what the library lacks and keeps everything it has, atomically. See the spec's merge rules. */
    @Transaction
    open suspend fun merge(papers: List<IncomingPaper>, collections: List<IncomingCollection>, now: Long): MergeOutcome {
        val localIds = mutableMapOf<Int, String>()
        val pdfTargets = mutableMapOf<Int, String>()
        var added = 0
        var matched = 0
        var notesAdded = 0
        var collectionsCreated = 0
        for (incoming in papers) {
            val paper = incoming.paper
            val existing = matchFor(paper.openAlexId, paper.doi)
            if (existing == null) {
                val key = paper.citeKey
                insertPaper(if (key != null && citeKeyTaken(key)) paper.copy(citeKey = null) else paper)
                insertAuthors(incoming.authors)
                insertSearch(
                    searchEntityFor(
                        paper.id,
                        paper.title,
                        incoming.authors.sortedBy { it.position }.map { it.name },
                        paper.abstract,
                        paper.venue,
                        incoming.notes?.asPaperNotes()
                    )
                )
                incoming.notes?.let { upsertNotes(it) }
                localIds[incoming.ref] = paper.id
                if (paper.pdfSource != null) pdfTargets[incoming.ref] = paper.id
                added++
            } else {
                matched++
                localIds[incoming.ref] = existing
                val notes = incoming.notes
                if (notes != null && !hasNotes(existing)) {
                    upsertNotes(notes.copy(paperId = existing))
                    setSearchNotes(existing, notesSearchText(notes.asPaperNotes()))
                    notesAdded++
                }
                val source = paper.pdfSource
                if (source != null &&
                    setPdfIfNone(existing, source, paper.pdfSize ?: 0, paper.pdfAddedAt ?: now, paper.pdfLastPage ?: 0) > 0
                ) {
                    pdfTargets[incoming.ref] = existing
                }
            }
        }
        for (collection in collections) {
            val id = idForNameKey(collection.nameKey)
                ?: insertCollection(CollectionEntity(name = collection.name, nameKey = collection.nameKey, createdAt = collection.createdAt))
                    .also { collectionsCreated++ }
            collection.refs.mapNotNull(localIds::get).distinct().forEach { link(id, it, now) }
        }
        return MergeOutcome(added, matched, notesAdded, collectionsCreated, pdfTargets)
    }

    @Transaction
    @Query("SELECT * FROM papers ORDER BY saved_at")
    protected abstract suspend fun allPapers(): List<PaperWithAuthors>

    @Query("SELECT * FROM paper_notes")
    protected abstract suspend fun allNotes(): List<PaperNotesEntity>

    @Query("SELECT * FROM collections ORDER BY name_key")
    protected abstract suspend fun allCollections(): List<CollectionEntity>

    @Query("SELECT * FROM collection_papers ORDER BY added_at")
    protected abstract suspend fun allLinks(): List<CollectionPaperEntity>

    @Query("SELECT id FROM papers WHERE open_alex_id = :openAlexId")
    protected abstract suspend fun idForOpenAlexId(openAlexId: String): String?

    @Query("SELECT id FROM papers WHERE doi = :doi ORDER BY saved_at LIMIT 1")
    protected abstract suspend fun idForDoi(doi: String): String?

    @Query("SELECT EXISTS(SELECT 1 FROM paper_notes WHERE paper_id = :paperId)")
    protected abstract suspend fun hasNotes(paperId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM papers WHERE cite_key = :citeKey)")
    protected abstract suspend fun citeKeyTaken(citeKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertPaper(paper: PaperEntity)

    @Insert
    protected abstract suspend fun insertAuthors(authors: List<PaperAuthorEntity>)

    @Insert
    protected abstract suspend fun insertSearch(search: PaperSearchEntity)

    @Upsert
    protected abstract suspend fun upsertNotes(notes: PaperNotesEntity)

    @Query("UPDATE paper_search SET notes = :text WHERE paper_id = :paperId")
    protected abstract suspend fun setSearchNotes(paperId: String, text: String)

    @Query(
        """
        UPDATE papers SET pdf_source = :source, pdf_size = :size, pdf_added_at = :addedAt, pdf_last_page = :lastPage
        WHERE id = :paperId AND pdf_source IS NULL
        """
    )
    protected abstract suspend fun setPdfIfNone(paperId: String, source: String, size: Long, addedAt: Long, lastPage: Int): Int

    @Query("SELECT id FROM collections WHERE name_key = :nameKey")
    protected abstract suspend fun idForNameKey(nameKey: String): Long?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertCollection(collection: CollectionEntity): Long

    @Query("INSERT OR IGNORE INTO collection_papers (collection_id, paper_id, added_at) VALUES (:collectionId, :paperId, :addedAt)")
    protected abstract suspend fun link(collectionId: Long, paperId: String, addedAt: Long)
}
