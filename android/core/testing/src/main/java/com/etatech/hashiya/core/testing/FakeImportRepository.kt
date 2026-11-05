package com.etatech.hashiya.core.testing

import com.etatech.hashiya.core.data.repository.ImportRepository
import com.etatech.hashiya.core.data.repository.MAX_IMPORT_FILE_BYTES
import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportJob
import com.etatech.hashiya.core.model.ImportState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** An import repository whose job a test sets directly; every call is recorded. */
class FakeImportRepository : ImportRepository {
    val job = MutableStateFlow<ImportJob?>(null)

    /** File name and size of every [start] call. */
    val starts = mutableListOf<Pair<String, Int>>()
    val confirms = mutableListOf<Pair<Long, Boolean>>()
    val cancels = mutableListOf<Long>()
    val dismissals = mutableListOf<Long>()

    override fun observeCurrentJob(): Flow<ImportJob?> = job

    override suspend fun start(fileName: String, bytes: ByteArray): ImportJob {
        starts += fileName to bytes.size
        job.value?.takeIf { it.isRunning }?.let { return it }
        val started = if (bytes.size > MAX_IMPORT_FILE_BYTES) {
            ImportJob(id = starts.size.toLong(), fileName = fileName, state = ImportState.Failed, failure = ImportFailure.TooLarge)
        } else {
            ImportJob(
                id = starts.size.toLong(),
                fileName = fileName,
                state = ImportState.Analyzing,
                collectionName = fileName.substringBeforeLast('.')
            )
        }
        job.value = started
        return started
    }

    override suspend fun confirm(jobId: Long, addToCollection: Boolean) {
        confirms += jobId to addToCollection
        job.value = job.value?.takeIf { it.id == jobId && it.state == ImportState.Ready }
            ?.copy(state = ImportState.Importing, addToCollection = addToCollection) ?: job.value
    }

    override suspend fun cancel(jobId: Long) {
        cancels += jobId
        job.value = job.value?.takeIf { it.id == jobId }?.copy(state = ImportState.Cancelled) ?: job.value
    }

    override suspend fun dismiss(jobId: Long) {
        dismissals += jobId
        if (job.value?.id == jobId && job.value?.isRunning == false) job.value = null
    }
}
