package com.etatech.hashiya.core.data.importer

import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.ImportResult
import com.etatech.hashiya.core.data.repository.ImportRepository
import com.etatech.hashiya.core.data.repository.MAX_IMPORT_FILE_BYTES
import com.etatech.hashiya.core.importer.ImportParser
import com.etatech.hashiya.core.model.COLLECTION_NAME_MAX_LENGTH
import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportJob
import com.etatech.hashiya.core.model.ImportState
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class DefaultImportRepository(
    private val store: ImportStore,
    private val files: ImportFileStore,
    private val scheduler: ImportScheduler,
    private val cache: ImportLookupCache,
    private val analytics: Analytics,
    private val now: () -> Long
) : ImportRepository {
    @Inject
    constructor(store: ImportStore, files: ImportFileStore, scheduler: ImportScheduler, cache: ImportLookupCache, analytics: Analytics) :
        this(store, files, scheduler, cache, analytics, System::currentTimeMillis)

    override fun observeCurrentJob(): Flow<ImportJob?> = store.observeLatest()

    override suspend fun start(fileName: String, bytes: ByteArray): ImportJob {
        store.running()?.let { return it }
        store.observeLatest().first()?.let { dismiss(it.id) }
        val job = ImportJob(id = 0, fileName = fileName, state = ImportState.Analyzing, collectionName = collectionNameFor(fileName))
        if (bytes.size > MAX_IMPORT_FILE_BYTES) {
            val tooLarge = created(job.copy(state = ImportState.Failed, failure = ImportFailure.TooLarge))
            analytics.log(importEvent(tooLarge, ImportResult.TooLarge, format = null))
            return tooLarge
        }
        val text = ImportParser.decode(bytes)
        val id = store.create(job, now())
        return try {
            files.write(id, text)
            scheduler.schedule(id)
            job.copy(id = id)
        } catch (e: IOException) {
            val failed = job.copy(id = id, state = ImportState.Failed, failure = ImportFailure.Unreadable)
            if (store.replaceIf(ImportState.Analyzing, failed)) analytics.log(importEvent(failed, ImportResult.Unreadable, format = null))
            failed
        }
    }

    override suspend fun confirm(jobId: Long, addToCollection: Boolean) {
        val job = store.job(jobId)?.takeIf { it.state == ImportState.Ready } ?: return
        if (store.replaceIf(ImportState.Ready, job.copy(state = ImportState.Importing, addToCollection = addToCollection))) {
            scheduler.schedule(jobId)
        }
    }

    override suspend fun cancel(jobId: Long) {
        val job = store.job(jobId) ?: return
        if (job.state !in CANCELLABLE) return
        if (store.replaceIf(job.state, job.copy(state = ImportState.Cancelled))) {
            scheduler.cancel(jobId)
            cache.remove(jobId)
            // The format, for the event only: parsed off the main thread from the job's copy of the file.
            val format = withContext(Dispatchers.Default) { files.read(jobId)?.let(ImportParser::parse)?.format }
            analytics.log(importEvent(job, ImportResult.Cancelled, format))
        }
    }

    override suspend fun dismiss(jobId: Long) {
        val job = store.job(jobId) ?: return
        if (job.isRunning) return
        store.delete(jobId)
        files.delete(jobId)
        cache.remove(jobId)
    }

    private suspend fun created(job: ImportJob): ImportJob = job.copy(id = store.create(job, now()))

    private companion object {
        val CANCELLABLE = setOf(ImportState.Analyzing, ImportState.Ready, ImportState.Importing)
    }
}

/** The new collection's name: the file name without its extension, trimmed to a collection name's length. */
internal fun collectionNameFor(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    val base = if (dot > 0) fileName.substring(0, dot) else fileName
    return base.trim().take(COLLECTION_NAME_MAX_LENGTH).trim().ifEmpty { "Import" }
}
