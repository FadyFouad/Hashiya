package com.etatech.hashiya.core.data.importer

/** Runs [ImportRunner.run] for a job in the background, surviving the app leaving the screen and process death. */
internal interface ImportScheduler {
    fun schedule(jobId: Long)

    fun cancel(jobId: Long)
}
