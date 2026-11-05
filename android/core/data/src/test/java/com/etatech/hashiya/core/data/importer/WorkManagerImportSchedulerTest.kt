package com.etatech.hashiya.core.data.importer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkManagerImportSchedulerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun scheduleEnqueuesTheImportWorkerAsTheJobsUniqueWork() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())

        WorkManagerImportScheduler(context).schedule(7)

        // The worker may already have run (and failed: this test app has no Hilt graph); either way it is the job's work.
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork(importWorkName(7)).get()
        assertEquals(1, work.size)
        assertTrue(ImportWorker::class.java.name in work.single().tags)
    }
}
