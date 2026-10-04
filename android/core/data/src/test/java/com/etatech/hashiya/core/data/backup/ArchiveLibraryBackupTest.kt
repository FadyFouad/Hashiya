package com.etatech.hashiya.core.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.data.pdf.PdfFileStore
import com.etatech.hashiya.core.data.pdf.PdfStoreGate
import com.etatech.hashiya.core.data.repository.RoomLibraryRepository
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperNotes
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArchiveLibraryBackupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: HashiyaDatabase
    private lateinit var library: RoomLibraryRepository
    private lateinit var pdfDir: File
    private lateinit var backup: ArchiveLibraryBackup
    private var ids = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, HashiyaDatabase::class.java).allowMainThreadQueries().build()
        library = RoomLibraryRepository(db.paperDao(), now = { 1_000L }, newId = { "local-${++ids}" })
        pdfDir = File(tmp.root, "pdfs")
        backup = backup(db)
    }

    @After
    fun tearDown() = db.close()

    private fun backup(database: HashiyaDatabase, pdfs: File = pdfDir) = ArchiveLibraryBackup(
        backupDao = database.backupDao(),
        fileStore = PdfFileStore(pdfs),
        gate = PdfStoreGate(),
        contentResolver = context.contentResolver,
        workDir = File(tmp.root, "work"),
        appVersion = "0.3.0 (Android)",
        now = { 1_790_000_000_000L },
        newId = { "restored-${++ids}" },
        io = Dispatchers.Unconfined
    )

    private fun paper(id: String, title: String = "Paper $id") =
        Paper(id, "10.1/$id", title, listOf(Author("Jane Doe", "A1"), Author("Omar", null)), 2020, "Nature", "Abstract", 3, true, "https://x/$id.pdf")

    private fun storePdf(localId: String, text: String = "%PDF-1.4 $localId") {
        pdfDir.mkdirs()
        File(pdfDir, "$localId.pdf").writeText(text)
        kotlinx.coroutines.runBlocking { db.paperDao().setPdf(localId, "downloaded", text.length.toLong(), 5) }
    }

    private fun ZipFile.text(name: String) = getInputStream(getEntry(name)).use { it.reader().readText() }

    @Test
    fun summaryCountsPapersCollectionsAndPdfs() = runTest {
        library.save(paper("W1"))
        library.save(paper("W2"))
        storePdf("local-1")
        db.collectionDao().insertCollection("C", "c", 1)

        assertEquals(BackupSummary(papers = 2, collections = 1, pdfCount = 1, pdfBytes = 16), backup.summary())
    }

    @Test
    fun exportWithoutPdfsWritesTheLibraryAndNoPdfEntries() = runTest {
        library.save(paper("W1"))
        library.saveNotes("W1", PaperNotes(summary = "My summary"))
        storePdf("local-1")
        val collection = db.collectionDao().insertCollection("Thesis", "thesis", 7)!!
        db.collectionDao().addToCollection(collection, "W1", 8)

        val exported = backup.export(includePdfs = false)

        assertEquals("Hashiya-library-2026-09-21.hashiya", exported.fileName)
        assertEquals(0, exported.missingPdfs)
        ZipFile(exported.file).use { zip ->
            assertNull(zip.getEntry("pdfs/1.pdf"))
            val manifest = backupJson.decodeFromString(BackupManifest.serializer(), zip.text(MANIFEST_ENTRY))
            assertEquals(BackupManifest(1, "0.3.0 (Android)", "2026-09-21T14:13:20Z", papers = 1, collections = 1, includesPdfs = false), manifest)
            val written = backupJson.decodeFromString(BackupLibrary.serializer(), zip.text(LIBRARY_ENTRY))
            val paper = written.papers.single()
            assertEquals(1, paper.ref)
            assertEquals("W1", paper.openAlexId)
            assertEquals(listOf(BackupAuthor("Jane Doe", "A1"), BackupAuthor("Omar", null)), paper.authors)
            assertEquals("My summary", paper.notes?.summary)
            assertEquals(BackupPdf("downloaded", 5, 0, null), paper.pdf)
            assertEquals(listOf(BackupCollection("Thesis", 7, listOf(1))), written.collections)
        }
    }

    @Test
    fun exportWithPdfsIncludesThemAndCountsMissingOnes() = runTest {
        library.save(paper("W1"))
        library.save(paper("W2"))
        storePdf("local-1")
        storePdf("local-2")
        File(pdfDir, "local-2.pdf").delete()

        val exported = backup.export(includePdfs = true)

        assertEquals(1, exported.missingPdfs)
        ZipFile(exported.file).use { zip ->
            assertEquals("%PDF-1.4 local-1", zip.text("pdfs/1.pdf"))
            val papers = backupJson.decodeFromString(BackupLibrary.serializer(), zip.text(LIBRARY_ENTRY)).papers
            assertEquals("pdfs/1.pdf", papers[0].pdf?.file)
            assertNull(papers[1].pdf?.file)
            assertTrue(backupJson.decodeFromString(BackupManifest.serializer(), zip.text(MANIFEST_ENTRY)).includesPdfs)
        }
    }

    @Test
    fun saveCopiesToTheDestinationAndDiscardDeletesTheTempFile() = runTest {
        library.save(paper("W1"))
        val exported = backup.export(includePdfs = false)
        val destination = File(tmp.root, "out.hashiya")

        backup.save(exported, Uri.fromFile(destination))
        backup.discard(exported)

        assertTrue(destination.length() > 0)
        assertFalse(exported.file.exists())
    }

    private fun twoStoredPdfs() = runBlocking {
        library.save(paper("W1"))
        library.save(paper("W2"))
        storePdf("local-1")
        storePdf("local-2")
    }

    @Test
    fun cancellingAnExportLeavesNoTempFile() = runTest {
        twoStoredPdfs()
        lateinit var job: Job
        job = launch { backup.export(includePdfs = true, onProgress = { job.cancel() }) }
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(emptyList<String>(), File(tmp.root, "work").list()!!.toList())
    }

    @Test
    fun aFailureThatIsNotAnIoErrorLeavesNoTempFileAndPropagates() = runTest {
        twoStoredPdfs()

        val failure = runCatching { backup.export(includePdfs = true, onProgress = { error("boom") }) }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertEquals(emptyList<String>(), File(tmp.root, "work").list()!!.toList())
    }

    private val fixture = File(System.getProperty("hashiya.testdata"), "backup/format-1.hashiya")

    @Test
    fun openPreviewsTheFixtureAgainstTheLibrary() = runTest {
        library.save(paper("W3"))

        val ready = backup.open(Uri.fromFile(fixture)) as OpenResult.Ready

        assertEquals(RestorePreview(exportedAt = 1_791_122_700_000L, papers = 3, collections = 2, pdfs = 1, newPapers = 2, existingPapers = 1), ready.preview)
        backup.discard(ready.backup)
        assertFalse(ready.backup.file.exists())
    }

    @Test
    fun openRejectsANonBackupAndKeepsNoCopy() = runTest {
        val text = tmp.newFile("notes.txt").apply { writeText("hello") }

        assertEquals(OpenResult.Failed(OpenFailure.NotABackup), backup.open(Uri.fromFile(text)))
        assertTrue(File(tmp.root, "work").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun openReportsAnUnreadableSource() = runTest {
        assertEquals(OpenResult.Failed(OpenFailure.Unreadable), backup.open(Uri.fromFile(File(tmp.root, "missing.hashiya"))))
    }
}
