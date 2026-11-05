package com.etatech.hashiya.core.data.importer

import com.etatech.hashiya.core.data.mapping.asEntities
import com.etatech.hashiya.core.database.dao.CollectionDao
import com.etatech.hashiya.core.database.dao.ImportJobDao
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.database.model.ImportJobEntity
import com.etatech.hashiya.core.database.model.ImportedPaperRows
import com.etatech.hashiya.core.model.ImportCounts
import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportJob
import com.etatech.hashiya.core.model.ImportState
import com.etatech.hashiya.core.model.ReadingStatus
import com.etatech.hashiya.core.model.collectionNameKey
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomImportStore(
    private val jobs: ImportJobDao,
    private val papers: PaperDao,
    private val collections: CollectionDao,
    private val newId: () -> String
) : ImportStore {
    @Inject
    constructor(jobs: ImportJobDao, papers: PaperDao, collections: CollectionDao) :
        this(jobs, papers, collections, { UUID.randomUUID().toString() })

    override fun observeLatest(): Flow<ImportJob?> = jobs.observeLatest().map { it?.asImportJob() }

    override suspend fun job(id: Long): ImportJob? = jobs.get(id)?.asImportJob()

    override suspend fun running(): ImportJob? = jobs.running()?.asImportJob()

    override suspend fun create(job: ImportJob, createdAt: Long): Long = jobs.insert(job.asEntity(createdAt).copy(id = 0))

    override suspend fun replaceIf(expected: ImportState, job: ImportJob): Boolean {
        val createdAt = jobs.get(job.id)?.createdAt ?: return false
        return jobs.replaceIf(expected.storedValue, job.asEntity(createdAt))
    }

    override suspend fun delete(id: Long) = jobs.delete(id)

    override suspend fun libraryKeys(jobId: Long): List<LibraryKey> = jobs.libraryKeys(jobId).map {
        LibraryKey(it.openAlexId, it.doi, it.title, it.year, it.citeKey, it.importedByJob)
    }

    override suspend fun createCollection(name: String, createdAt: Long): Long? =
        collections.insertCollection(name.trim(), collectionNameKey(name), createdAt)

    /** Papers in a batch get consecutive save times, so the library and the next export keep the file's order. */
    override suspend fun saveBatch(jobId: Long, papers: List<PaperToSave>, collectionId: Long?, savedAt: Long, processed: Int): Int {
        val rows = papers.mapIndexed { index, toSave ->
            val entities = toSave.paper.asEntities(
                localId = newId(),
                savedAt = savedAt + index,
                status = ReadingStatus.ToRead,
                citeKey = toSave.citeKey,
                detailsFetched = true
            )
            ImportedPaperRows(entities.paper, entities.authors, entities.search)
        }
        return this.papers.insertImportBatch(jobId, rows, collectionId, savedAt, processed)
    }
}

internal val ImportState.storedValue: String
    get() = when (this) {
        ImportState.Analyzing -> "analyzing"
        ImportState.Ready -> "ready"
        ImportState.Importing -> "importing"
        ImportState.Done -> "done"
        ImportState.Failed -> "failed"
        ImportState.Cancelled -> "cancelled"
    }

private val ImportFailure.storedValue: String
    get() = when (this) {
        ImportFailure.Unsupported -> "unsupported"
        ImportFailure.TooLarge -> "too_large"
        ImportFailure.Unreadable -> "unreadable"
        ImportFailure.NothingToImport -> "nothing_to_import"
    }

private fun importState(stored: String): ImportState = ImportState.entries.firstOrNull { it.storedValue == stored } ?: ImportState.Failed

private fun importFailure(stored: String?): ImportFailure? = ImportFailure.entries.firstOrNull { it.storedValue == stored }

internal fun ImportJobEntity.asImportJob(): ImportJob {
    val state = importState(state)
    return ImportJob(
        id = id,
        fileName = fileName,
        state = state,
        counts = ImportCounts(found, matched, local, inLibrary, repeated, unreadable, notChecked),
        processed = processed,
        imported = imported,
        addToCollection = addToCollection,
        collectionName = collectionName,
        collectionId = collectionId,
        // An unknown stored state reads as Failed, so it needs a reason.
        failure = importFailure(failure) ?: if (state == ImportState.Failed) ImportFailure.Unreadable else null
    )
}

internal fun ImportJob.asEntity(createdAt: Long): ImportJobEntity = ImportJobEntity(
    id = id,
    fileName = fileName,
    createdAt = createdAt,
    state = state.storedValue,
    failure = failure?.storedValue,
    addToCollection = addToCollection,
    collectionName = collectionName,
    collectionId = collectionId,
    found = counts.found,
    matched = counts.matched,
    local = counts.local,
    inLibrary = counts.inLibrary,
    repeated = counts.repeated,
    unreadable = counts.unreadable,
    notChecked = counts.notChecked,
    processed = processed,
    imported = imported
)
