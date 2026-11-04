package com.etatech.hashiya.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.etatech.hashiya.core.database.model.ImportJobEntity
import com.etatech.hashiya.core.database.model.LibraryKeyRow
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ImportJobDao {
    @Insert
    abstract suspend fun insert(job: ImportJobEntity): Long

    @Update
    abstract suspend fun update(job: ImportJobEntity)

    @Query("SELECT * FROM import_jobs WHERE id = :id")
    abstract suspend fun get(id: Long): ImportJobEntity?

    /** The newest job; null when there is none (every job is deleted once the user dismisses it). */
    @Query("SELECT * FROM import_jobs ORDER BY id DESC LIMIT 1")
    abstract fun observeLatest(): Flow<ImportJobEntity?>

    @Query("SELECT * FROM import_jobs WHERE state IN ('analyzing', 'importing') ORDER BY id DESC LIMIT 1")
    abstract suspend fun running(): ImportJobEntity?

    /** Moves the job to [state] only from [from]; returns false when it had moved on (a cancel racing the worker). */
    @Query("UPDATE import_jobs SET state = :state WHERE id = :id AND state = :from")
    protected abstract suspend fun moveStateRows(id: Long, from: String, state: String): Int

    suspend fun moveState(id: Long, from: String, state: String): Boolean = moveStateRows(id, from, state) > 0

    /** Replaces the job only while it is still in [expected]; false when it moved on (a cancel racing the worker). */
    @Transaction
    open suspend fun replaceIf(expected: String, job: ImportJobEntity): Boolean {
        if (get(job.id)?.state != expected) return false
        update(job)
        return true
    }

    /** Deletes the job; its import_job_papers rows cascade, its papers stay. */
    @Query("DELETE FROM import_jobs WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query(
        """
        SELECT papers.id, papers.open_alex_id, papers.doi, papers.title, papers.year, papers.cite_key,
            EXISTS(SELECT 1 FROM import_job_papers WHERE job_id = :jobId AND paper_id = papers.id) AS imported_by_job
        FROM papers
        """
    )
    abstract suspend fun libraryKeys(jobId: Long): List<LibraryKeyRow>
}
