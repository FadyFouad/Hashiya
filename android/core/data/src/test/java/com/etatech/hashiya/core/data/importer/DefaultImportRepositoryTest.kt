package com.etatech.hashiya.core.data.importer

import app.cash.turbine.test
import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.ImportFileFormat
import com.etatech.hashiya.core.analytics.ImportResult
import com.etatech.hashiya.core.analytics.LibrarySize
import com.etatech.hashiya.core.analytics.MatchedShare
import com.etatech.hashiya.core.data.repository.MAX_IMPORT_FILE_BYTES
import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportState
import com.etatech.hashiya.core.testing.FakeAnalytics
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DefaultImportRepositoryTest {
    private val store = FakeImportStore()
    private val files = ImportFileStore(Files.createTempDirectory("imports").toFile())
    private val cache = ImportLookupCache()

    private class RecordingScheduler : ImportScheduler {
        val scheduled = mutableListOf<Long>()
        val cancelled = mutableListOf<Long>()

        override fun schedule(jobId: Long) {
            scheduled += jobId
        }

        override fun cancel(jobId: Long) {
            cancelled += jobId
        }
    }

    private val scheduler = RecordingScheduler()
    private val analytics = FakeAnalytics()
    private val repository = DefaultImportRepository(store, files, scheduler, cache, analytics, now = { 5 })

    private val bib = "@article{a, title = {A}}".toByteArray()

    @Test
    fun `start stores the decoded text, creates an analyzing job named after the file and schedules it`() = runTest {
        val job = repository.start("My Thesis.bib", byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + bib)
        assertEquals(ImportState.Analyzing, job.state)
        assertEquals("My Thesis.bib", job.fileName)
        assertEquals("My Thesis", job.collectionName)
        assertEquals("@article{a, title = {A}}", files.read(job.id))
        assertEquals(listOf(job.id), scheduler.scheduled)
        repository.observeCurrentJob().test { assertEquals(job.id, awaitItem()?.id) }
    }

    @Test
    fun `a file over the limit fails at once and schedules nothing`() = runTest {
        val job = repository.start("huge.bib", ByteArray(MAX_IMPORT_FILE_BYTES + 1))
        assertEquals(ImportState.Failed to ImportFailure.TooLarge, job.state to job.failure)
        assertEquals(
            listOf(AnalyticsEvent.Import(null, ImportResult.TooLarge, LibrarySize.Zero, MatchedShare.None, toCollection = false)),
            analytics.events
        )
        assertEquals(emptyList<Long>(), scheduler.scheduled)
        assertNull(files.read(job.id))
    }

    @Test
    fun `a running job is returned instead of starting another`() = runTest {
        val first = repository.start("a.bib", bib)
        val second = repository.start("b.bib", bib)
        assertEquals(first.id, second.id)
        assertEquals(listOf(first.id), scheduler.scheduled)
    }

    @Test
    fun `starting again dismisses a finished job and its file`() = runTest {
        val first = repository.start("a.bib", bib)
        store.force(first.copy(state = ImportState.Done))
        val second = repository.start("b.bib", bib)
        assertNull(store.job(first.id))
        assertNull(files.read(first.id))
        assertEquals("b.bib", store.job(second.id)?.fileName)
    }

    @Test
    fun `confirm moves a ready job to importing with the collection choice and schedules it`() = runTest {
        val job = repository.start("a.bib", bib)
        repository.confirm(job.id, addToCollection = true)
        assertEquals(ImportState.Analyzing, store.job(job.id)?.state)
        store.force(job.copy(state = ImportState.Ready))
        repository.confirm(job.id, addToCollection = false)
        assertEquals(ImportState.Importing to false, store.job(job.id)?.let { it.state to it.addToCollection })
        assertEquals(listOf(job.id, job.id), scheduler.scheduled)
    }

    @Test
    fun `cancel stops a running or ready job and keeps a finished one`() = runTest {
        val job = repository.start("a.bib", bib)
        cache[job.id] = DoiMatches()
        repository.cancel(job.id)
        assertEquals(ImportState.Cancelled, store.job(job.id)?.state)
        assertEquals(listOf(job.id), scheduler.cancelled)
        assertNull(cache[job.id])
        assertEquals(
            listOf(AnalyticsEvent.Import(ImportFileFormat.Bibtex, ImportResult.Cancelled, LibrarySize.Zero, MatchedShare.None, false)),
            analytics.events
        )
        store.force(job.copy(state = ImportState.Done))
        repository.cancel(job.id)
        assertEquals(ImportState.Done, store.job(job.id)?.state)
    }

    @Test
    fun `dismiss forgets a finished job but never a running one`() = runTest {
        val job = repository.start("a.bib", bib)
        repository.dismiss(job.id)
        assertEquals(ImportState.Analyzing, store.job(job.id)?.state)
        store.force(job.copy(state = ImportState.Failed, failure = ImportFailure.Unsupported))
        repository.dismiss(job.id)
        assertNull(store.job(job.id))
        assertNull(files.read(job.id))
    }

    @Test
    fun `collection names come from the file name`() {
        assertEquals("thesis", collectionNameFor("thesis.bib"))
        assertEquals("my.library", collectionNameFor("my.library.ris"))
        assertEquals(".bib", collectionNameFor(".bib"))
        assertEquals("Import", collectionNameFor("   .bib"))
        assertEquals("a".repeat(60), collectionNameFor("a".repeat(80) + ".bib"))
    }
}
