package com.etatech.hashiya.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import com.etatech.hashiya.core.database.model.PaperWithAuthors

@Dao
abstract class CitationDao {
    /** Every saved paper, or those in [collectionId], oldest saved first: the order cite keys are assigned in. */
    @Transaction
    @Query(
        """
        SELECT * FROM papers
        WHERE (:collectionId IS NULL OR id IN (SELECT paper_id FROM collection_papers WHERE collection_id = :collectionId))
        ORDER BY saved_at ASC, rowid ASC
        """
    )
    abstract suspend fun getPapers(collectionId: Long?): List<PaperWithAuthors>

    @Transaction
    @Query("SELECT * FROM papers WHERE open_alex_id = :openAlexId")
    abstract suspend fun getPaper(openAlexId: String): PaperWithAuthors?

    @Query(
        """
        UPDATE papers SET work_type = :workType, source_type = :sourceType, publisher = :publisher, volume = :volume,
            issue = :issue, first_page = :firstPage, last_page = :lastPage, details_fetched = 1
        WHERE id = :paperId
        """
    )
    abstract suspend fun updatePublicationDetails(
        paperId: String,
        workType: String?,
        sourceType: String?,
        publisher: String?,
        volume: String?,
        issue: String?,
        firstPage: String?,
        lastPage: String?
    )

    /** For a paper OpenAlex no longer has: asking again would never help. */
    @Query("UPDATE papers SET details_fetched = 1 WHERE id = :paperId")
    abstract suspend fun markDetailsFetched(paperId: String)

    @Query("SELECT cite_key FROM papers WHERE cite_key IS NOT NULL")
    abstract suspend fun allCiteKeys(): List<String>

    // Only a paper without a key gets one: a stored key is never changed.
    @Query("UPDATE papers SET cite_key = :citeKey WHERE id = :paperId AND cite_key IS NULL")
    protected abstract suspend fun setCiteKey(paperId: String, citeKey: String)

    /** Stores every key (paper id → key) or none: a key another paper holds throws SQLiteConstraintException. */
    @Transaction
    open suspend fun assignCiteKeys(keys: Map<String, String>) {
        keys.forEach { (paperId, key) -> setCiteKey(paperId, key) }
    }
}
