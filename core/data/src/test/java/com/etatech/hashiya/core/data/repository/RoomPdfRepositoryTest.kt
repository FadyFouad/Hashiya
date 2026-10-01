package com.etatech.hashiya.core.data.repository

import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.data.pdf.PdfFileStore
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperPdf
import com.etatech.hashiya.core.model.PdfSource
import com.etatech.hashiya.core.model.PdfStorage
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.PdfDownloadDataSource
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomPdfRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: HashiyaDatabase
    private lateinit var library: RoomLibraryRepository
    private lateinit var dir: File
    private lateinit var scope: CoroutineScope
    private lateinit var repository: RoomPdfRepository
    private val downloader = FakePdfDownloadDataSource()
    private var clock = 0L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), HashiyaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var ids = 0
        library = RoomLibraryRepository(db.paperDao(), now = { ++clock }, newId = { "local-${++ids}" })
        dir = File(tmp.root, "pdfs")
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        repository = repository()
    }

    @After
    fun tearDown() {
        // Waits for the repository's own coroutines to stop, so none of them touches the closed database or fails in a later test.
        runBlocking { scope.coroutineContext.job.cancelAndJoin() }
        db.close()
    }

    private fun repository(maxBytes: Long = 1024 * 1024) = RoomPdfRepository(
        paperDao = db.paperDao(),
        fileStore = PdfFileStore(dir),
        downloader = downloader,
        contentResolver = ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver,
        scope = scope,
        now = { 1_000L },
        maxBytes = maxBytes,
        io = Dispatchers.IO
    )

    private fun paper(id: String, pdfUrl: String? = "https://arxiv.org/pdf/$id") =
        Paper(id, null, "Deep nets", listOf(Author("Jane Doe", null)), 2020, "Nature", null, 0, pdfUrl != null, pdfUrl)

    /** Waits in real time: the repository works on real threads, which the test scheduler's virtual time doesn't follow. */
    private suspend fun <T> awaitValue(flow: Flow<T>, predicate: (T) -> Boolean): T =
        withContext(Dispatchers.Default) { withTimeout(5_000) { flow.first(predicate) } }

    private suspend fun awaitTrue(condition: () -> Boolean) = withContext(Dispatchers.Default) {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }

    private suspend fun awaitStored(openAlexId: String): PaperPdf = awaitValue(repository.observePdf(openAlexId)) { it != null }!!

    private suspend fun awaitFailure(openAlexId: String): DownloadState? =
        awaitValue(repository.observeDownload(openAlexId)) { it is DownloadState.Failed }

    private fun filesInDir(): List<String> = dir.listFiles().orEmpty().map { it.name }.sorted()

    private fun fileUri(name: String, bytes: ByteArray): Uri = Uri.fromFile(File(tmp.root, name).apply { writeBytes(bytes) })

    @Test
    fun downloadsTheOpenAccessPdfAndRecordsIt() = runTest {
        library.save(paper("W1"))

        repository.download("W1")

        assertEquals(PaperPdf(PdfSource.Downloaded, sizeBytes = PDF.size.toLong(), addedAt = 1_000L, lastPage = 0), awaitStored("W1"))
        assertEquals(listOf("https://arxiv.org/pdf/W1"), downloader.urls)
        assertArrayEquals(PDF, repository.pdfFile("W1")!!.readBytes())
        assertNull(awaitValue(repository.observeDownload("W1")) { it == null })
        assertEquals(listOf("local-1.pdf"), filesInDir())
    }

    @Test
    fun anHttpLinkIsDownloadedOverHttps() = runTest {
        library.save(paper("W1", pdfUrl = "http://arxiv.org/pdf/1612.03928"))
        library.save(paper("W2", pdfUrl = "HTTP://jsrse.edu.iq:8080/download/285/301"))

        repository.download("W1")
        awaitStored("W1")
        repository.download("W2")
        awaitStored("W2")

        assertEquals(listOf("https://arxiv.org/pdf/1612.03928", "https://jsrse.edu.iq:8080/download/285/301"), downloader.urls)
    }

    @Test
    fun showsRunningWhileTheRequestIsOpen() = runTest {
        library.save(paper("W1"))
        downloader.gate = CompletableDeferred()

        repository.download("W1")

        assertEquals(DownloadState.Running(bytes = 0, totalBytes = null), awaitValue(repository.observeDownload("W1")) { it != null })
        downloader.gate!!.complete(Unit)
        awaitStored("W1")
        assertNull(awaitValue(repository.observeDownload("W1")) { it == null })
    }

    @Test
    fun anHtmlBodyIsNotAPdf() = runTest {
        library.save(paper("W1"))
        downloader.body = HTML

        repository.download("W1")

        assertEquals(DownloadState.Failed(DownloadFailure.NotPdf), awaitFailure("W1"))
        assertNull(repository.observePdf("W1").first())
        assertEquals(emptyList<String>(), filesInDir())
    }

    @Test
    fun aPdfOverTheLimitIsTooLarge() = runTest {
        repository = repository(maxBytes = 16)
        library.save(paper("W1"))

        repository.download("W1")

        assertEquals(DownloadState.Failed(DownloadFailure.TooLarge), awaitFailure("W1"))
        assertEquals(emptyList<String>(), filesInDir())
    }

    @Test
    fun beingOfflineIsReported() = runTest {
        library.save(paper("W1"))
        downloader.failure = NetworkException(NetworkFailure.Connectivity)

        repository.download("W1")

        assertEquals(DownloadState.Failed(DownloadFailure.Offline), awaitFailure("W1"))
    }

    @Test
    fun anErrorStatusIsAnHttpFailure() = runTest {
        library.save(paper("W1"))
        downloader.failure = NetworkException(NetworkFailure.Http(code = 403, usedUserKey = false))

        repository.download("W1")

        assertEquals(DownloadState.Failed(DownloadFailure.Http), awaitFailure("W1"))
    }

    @Test
    fun aPaperWithoutALinkReportsNoLink() = runTest {
        library.save(paper("W1", pdfUrl = null))

        repository.download("W1")

        assertEquals(DownloadState.Failed(DownloadFailure.NoLink), awaitFailure("W1"))
        assertEquals(emptyList<String>(), downloader.urls)
    }

    @Test
    fun aSecondTapWhileDownloadingStartsNothing() = runTest {
        library.save(paper("W1"))
        downloader.gate = CompletableDeferred()

        repository.download("W1")
        downloader.started.await()
        repository.download("W1")
        downloader.gate!!.complete(Unit)
        awaitStored("W1")

        assertEquals(1, downloader.urls.size)
    }

    @Test
    fun aFailedDownloadCanBeTriedAgain() = runTest {
        library.save(paper("W1"))
        downloader.failure = NetworkException(NetworkFailure.Connectivity)
        repository.download("W1")
        awaitFailure("W1")

        downloader.failure = null
        repository.download("W1")

        awaitStored("W1")
        assertNull(awaitValue(repository.observeDownload("W1")) { it == null })
    }

    @Test
    fun cancellingClearsTheStateAndLeavesNoFile() = runTest {
        library.save(paper("W1"))
        downloader.gate = CompletableDeferred()
        repository.download("W1")
        downloader.started.await()

        repository.cancelDownload("W1")

        assertNull(awaitValue(repository.observeDownload("W1")) { it == null })
        awaitTrue { downloader.cancelled }
        assertNull(repository.observePdf("W1").first())
        assertEquals(emptyList<String>(), filesInDir())
    }

    @Test
    fun removingAPaperCancelsItsDownload() = runTest {
        library.save(paper("W1"))
        downloader.gate = CompletableDeferred()
        repository.download("W1")
        downloader.started.await()

        library.remove("W1")

        awaitTrue { downloader.cancelled }
        assertNull(awaitValue(repository.observeDownload("W1")) { it == null })
        assertEquals(emptyList<String>(), filesInDir())
    }

    @Test
    fun attachStoresTheFileAsAttached() = runTest {
        library.save(paper("W1", pdfUrl = null))

        assertEquals(AttachResult.Done, repository.attach("W1", fileUri("chosen.pdf", PDF)))

        assertEquals(PaperPdf(PdfSource.Attached, sizeBytes = PDF.size.toLong(), addedAt = 1_000L), awaitStored("W1"))
        assertArrayEquals(PDF, repository.pdfFile("W1")!!.readBytes())
    }

    @Test
    fun attachingANonPdfKeepsTheCurrentPdf() = runTest {
        library.save(paper("W1"))
        repository.download("W1")
        awaitStored("W1")

        assertEquals(AttachResult.NotPdf, repository.attach("W1", fileUri("page.html", HTML)))

        assertEquals(PdfSource.Downloaded, repository.observePdf("W1").first()!!.source)
        assertArrayEquals(PDF, repository.pdfFile("W1")!!.readBytes())
    }

    @Test
    fun attachingAFileOverTheLimitIsTooLarge() = runTest {
        repository = repository(maxBytes = 16)
        library.save(paper("W1"))

        assertEquals(AttachResult.TooLarge, repository.attach("W1", fileUri("big.pdf", PDF)))
        assertNull(repository.observePdf("W1").first())
    }

    @Test
    fun attachingAnUnreadableUriFails() = runTest {
        library.save(paper("W1"))

        assertEquals(AttachResult.Unreadable, repository.attach("W1", Uri.fromFile(File(tmp.root, "missing.pdf"))))
        assertNull(repository.observePdf("W1").first())
    }

    @Test
    fun attachingToAnUnsavedPaperFails() = runTest {
        assertEquals(AttachResult.Unreadable, repository.attach("W9", fileUri("chosen.pdf", PDF)))
        assertEquals(emptyList<String>(), filesInDir())
    }

    @Test
    fun replacingAPdfStartsAgainOnTheFirstPage() = runTest {
        library.save(paper("W1"))
        repository.download("W1")
        awaitStored("W1")
        repository.setLastPage("W1", 7)

        repository.attach("W1", fileUri("better.pdf", PDF + "% publisher version".toByteArray()))

        val pdf = awaitValue(repository.observePdf("W1")) { it?.source == PdfSource.Attached }!!
        assertEquals(0, pdf.lastPage)
    }

    @Test
    fun setLastPageIsStored() = runTest {
        library.save(paper("W1"))
        repository.download("W1")
        awaitStored("W1")

        repository.setLastPage("W1", 12)

        assertEquals(12, awaitValue(repository.observePdf("W1")) { it?.lastPage == 12 }!!.lastPage)
    }

    @Test
    fun removeClearsTheRowAndDeletesTheFile() = runTest {
        library.save(paper("W1"))
        repository.download("W1")
        awaitStored("W1")

        repository.remove("W1")

        assertNull(repository.observePdf("W1").first())
        assertNull(repository.pdfFile("W1"))
        assertEquals(emptyList<String>(), filesInDir())
    }

    @Test
    fun pdfFileIsNullWithoutAPdf() = runTest {
        library.save(paper("W1"))

        assertNull(repository.pdfFile("W1"))
        assertNull(repository.pdfFile("W9"))
    }

    @Test
    fun undoKeepsTheFileAndDiscardDeletesIt() = runTest {
        library.save(paper("W1"))
        repository.download("W1")
        val stored = awaitStored("W1")

        val removed = library.remove("W1")!!
        assertEquals(stored, removed.pdf)
        assertEquals(listOf("local-1.pdf"), filesInDir())

        library.restore(removed)
        assertEquals(stored, repository.observePdf("W1").first())
        repository.discardRemoved(removed)
        assertEquals(listOf("local-1.pdf"), filesInDir())

        val removedAgain = library.remove("W1")!!
        repository.discardRemoved(removedAgain)
        assertEquals(emptyList<String>(), filesInDir())
    }

    @Test
    fun discardingAPaperSavedAgainUnderANewIdDeletesTheOldFile() = runTest {
        library.save(paper("W1"))
        repository.download("W1")
        awaitStored("W1")
        val removed = library.remove("W1")!!
        library.save(paper("W1"))

        repository.discardRemoved(removed)

        assertFalse(File(dir, "local-1.pdf").exists())
    }

    @Test
    fun deleteDownloadedKeepsAttachedFiles() = runTest {
        library.save(paper("W1"))
        library.save(paper("W2"))
        repository.download("W1")
        awaitStored("W1")
        repository.attach("W2", fileUri("mine.pdf", PDF))

        repository.deleteDownloaded()

        assertNull(repository.observePdf("W1").first())
        assertEquals(PdfSource.Attached, repository.observePdf("W2").first()!!.source)
        assertEquals(listOf("local-2.pdf"), filesInDir())
    }

    @Test
    fun storageSumsBySource() = runTest {
        library.save(paper("W1"))
        library.save(paper("W2"))
        repository.download("W1")
        awaitStored("W1")
        repository.attach("W2", fileUri("mine.pdf", PDF + PDF))

        assertEquals(
            PdfStorage(downloadedBytes = PDF.size.toLong(), downloadedCount = 1, attachedBytes = 2L * PDF.size, attachedCount = 1),
            repository.storage()
        )
    }

    @Test
    fun sweepOrphansDeletesFilesWithoutARow() = runTest {
        library.save(paper("W1"))
        repository.download("W1")
        awaitStored("W1")
        File(dir, "ghost.pdf").writeBytes(PDF)
        File(dir, "local-1-99.part").writeText("half a download")

        repository.sweepOrphans()

        assertEquals(listOf("local-1.pdf"), filesInDir())
    }

    private class FakePdfDownloadDataSource : PdfDownloadDataSource {
        @Volatile var body: ByteArray = PDF

        @Volatile var failure: NetworkException? = null

        /** When set, every download waits for it before reading, so a test can see one running or cancel it. */
        @Volatile var gate: CompletableDeferred<Unit>? = null

        val started = CompletableDeferred<Unit>()

        @Volatile var cancelled = false

        private val requested = mutableListOf<String>()
        val urls: List<String> get() = synchronized(requested) { requested.toList() }

        override suspend fun <T> download(url: String, consume: (body: InputStream, contentLength: Long?) -> T): T {
            synchronized(requested) { requested += url }
            started.complete(Unit)
            try {
                gate?.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw e
            }
            failure?.let { throw it }
            return consume(ByteArrayInputStream(body), body.size.toLong())
        }
    }

    private companion object {
        val PDF = "%PDF-1.7\n1 0 obj << /Type /Catalog >> endobj\n%%EOF\n".toByteArray()
        val HTML = "<!DOCTYPE html><html><body>Sign in to read this article</body></html>".toByteArray()
    }
}
