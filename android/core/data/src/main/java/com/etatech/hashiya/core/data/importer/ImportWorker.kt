package com.etatech.hashiya.core.data.importer

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlin.coroutines.cancellation.CancellationException

/** Gives the worker its runner without a custom WorkerFactory, so the app keeps WorkManager's default setup. */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface ImportWorkerEntryPoint {
    fun importRunner(): ImportRunner
}

/** Runs one phase of an import job in the background (spec §7.5). Public, as WorkManager's lint requires of workers. */
class ImportWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val jobId = inputData.getLong(KEY_JOB_ID, -1L).takeIf { it >= 0 } ?: return Result.failure()
        val runner = try {
            EntryPointAccessors.fromApplication(applicationContext, ImportWorkerEntryPoint::class.java).importRunner()
        } catch (e: IllegalStateException) {
            // Only outside the Hilt app (unit tests): there is nothing to run the job with.
            return Result.failure()
        }
        return try {
            runner.run(jobId)
            Result.success()
        } catch (e: CancellationException) {
            // Cancelled from Import's Cancel, or stopped by the system: the job's state already says which.
            throw e
        } catch (e: Exception) {
            runner.fail(jobId)
            Result.failure()
        }
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
    }
}
