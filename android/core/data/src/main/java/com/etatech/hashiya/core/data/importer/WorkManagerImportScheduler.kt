package com.etatech.hashiya.core.data.importer

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal fun importWorkName(jobId: Long) = "import-$jobId"

/**
 * One unique work chain per job. APPEND_OR_REPLACE queues the import phase behind an analysis run that is still finishing, and
 * starts it fresh otherwise. WorkManager keeps the work across process death and runs it again; the runner resumes from the
 * stored state. WorkManager is looked up on use, so building the graph never initialises it.
 */
internal class WorkManagerImportScheduler @Inject constructor(@ApplicationContext private val context: Context) : ImportScheduler {
    override fun schedule(jobId: Long) {
        val request = OneTimeWorkRequestBuilder<ImportWorker>()
            .setInputData(workDataOf(ImportWorker.KEY_JOB_ID to jobId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(importWorkName(jobId), ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    override fun cancel(jobId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(importWorkName(jobId))
    }
}
