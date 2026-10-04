package com.etatech.hashiya.core.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.etatech.hashiya.core.data.FakeOpenAlexLookupDataSource
import com.etatech.hashiya.core.data.pdf.MAX_PDF_BYTES
import com.etatech.hashiya.core.data.pdf.PdfFileStore
import com.etatech.hashiya.core.data.pdf.PdfStoreGate
import com.etatech.hashiya.core.data.repository.RoomCitationRepository
import com.etatech.hashiya.core.data.repository.RoomLibraryRepository
import com.etatech.hashiya.core.database.HashiyaDatabase
import com.etatech.hashiya.core.model.Author
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperNotes
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
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

    private fun backup(database: HashiyaDatabase, pdfs: File = pdfDir, work: File = File(tmp.root, "work")) = ArchiveLibraryBackup(
        backupDao = database.backupDao(),
        fileStore = PdfFileStore(pdfs),
        gate = PdfStoreGate(),
        contentResolver = context.contentResolver,
        workDir = work,
        appVersion = "0.3.0 (Android)",
        now = { 1_790_000_000_000L },
        newId = { "restored-${++ids}" },
        io = Dispatchers.Unconfined
    )

    private fun paper(id: String, title: String = "Paper $id") = Paper(
        id, "10.1/$id", title,
        listOf(
            Author("Jane Doe", "A1"),
            Author("Omar", null)
        ),
        2020, "Nature", "Abstract", 3, true, "https://x/$id.pdf"
    )

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
            assertEquals(
                BackupManifest(1, "0.3.0 (Android)", "2026-09-21T14:13:20Z", papers = 1, collections = 1, includesPdfs = false),
                manifest
            )
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

        // Paper 2 has no OpenAlex id: it is skipped, so it is neither new nor existing.
        assertEquals(
            RestorePreview(
                exportedAt = 1_791_122_700_000L,
                papers = 3,
                collections = 2,
                pdfs = 1,
                newPapers = 1,
                existingPapers = 1,
                papersSkipped = 1
            ),
            ready.preview
        )
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

    @Test
    fun cancellingAnOpenLeavesNoCopy() = runTest {
        val bytes = fixture.readBytes()
        val source = Uri.parse("content://docs/cancelled.hashiya")
        lateinit var job: Job
        val cancelling = object : java.io.ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                job.cancel()
                return super.read(b, off, len)
            }
        }
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(source, cancelling)
        job = launch { backup.open(source) }
        job.join()

        assertTrue(job.isCancelled)
        assertEquals(emptyList<String>(), File(tmp.root, "work").list()!!.toList())
    }

    @Test
    fun leftoversFromAnEarlierProcessAreClearedOnFirstUse() = runTest {
        val work = File(tmp.root, "work").apply { mkdirs() }
        File(work, "export-stale.hashiya").writeText("old")
        File(work, "restore-stale.hashiya").writeText("old")

        val exported = backup.export(includePdfs = false)

        assertEquals(listOf(exported.file.name), work.list()!!.toList())
        backup.discard(exported)
    }

    private suspend fun ready(file: File, using: ArchiveLibraryBackup = backup) = using.open(Uri.fromFile(file)) as OpenResult.Ready

    @Test
    fun applyingTheFixtureRestoresEverything() = runTest {
        val result = backup.apply(ready(fixture).backup)

        assertEquals(
            RestoreResult(papersAdded = 2, notesAdded = 0, collectionsCreated = 2, pdfsAdded = 1, pdfsMissing = 0, papersSkipped = 1),
            result
        )
        val deep = db.paperDao().getByOpenAlexId("W2741809807")!!
        assertEquals("reading", deep.paper.readingStatus)
        assertEquals("lecun2015deep", deep.paper.citeKey)
        assertEquals(listOf("Yann LeCun", "Yoshua Bengio", "Geoffrey Hinton"), deep.authors.sortedBy { it.position }.map { it.name })
        assertEquals(4, deep.paper.pdfLastPage)
        assertTrue(File(pdfDir, "${deep.paper.id}.pdf").readText().startsWith("%PDF-1.4"))
        assertEquals(1, db.paperDao().observeLibrary("chapter*", null, null).first().size)
        assertEquals(
            mapOf("Thesis" to 1, "مراجعة" to 1),
            db.collectionDao().observeCollections().first().associate {
                it.name to
                    it.paperCount
            }
        )
        // No staged file is left behind.
        assertTrue(pdfDir.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun roundTripIntoAnEmptyLibrary() = runTest {
        library.save(paper("W1"))
        library.save(paper("W2", title = "Second"))
        library.saveNotes("W1", PaperNotes(summary = "S", thoughts = "T"))
        library.setStatus("W2", com.etatech.hashiya.core.model.ReadingStatus.Read)
        storePdf("local-1")
        val c = db.collectionDao().insertCollection("Thesis", "thesis", 7)!!
        db.collectionDao().addToCollection(c, "W2", 8)
        val exported = backup.export(includePdfs = true)

        val other = Room.inMemoryDatabaseBuilder(context, HashiyaDatabase::class.java).allowMainThreadQueries().build()
        val otherPdfs = File(tmp.root, "other-pdfs")
        val target = backup(other, otherPdfs, work = File(tmp.root, "other-work"))
        val result = target.apply(ready(exported.file, target).backup)

        assertEquals(2, result.papersAdded)
        assertEquals(1, result.pdfsAdded)
        val w1 = other.paperDao().getByOpenAlexId("W1")!!
        assertEquals("S", other.paperDao().observeNotes("W1").first()?.summary)
        assertEquals("read", other.paperDao().getByOpenAlexId("W2")!!.paper.readingStatus)
        assertEquals("%PDF-1.4 local-1", File(otherPdfs, "${w1.paper.id}.pdf").readText())
        assertEquals(mapOf("Thesis" to 1), other.collectionDao().observeCollections().first().associate { it.name to it.paperCount })
        other.close()
    }

    @Test
    fun restoringTwiceAddsNothing() = runTest {
        backup.apply(ready(fixture).backup)
        val second = backup.apply(ready(fixture).backup)

        assertEquals(0, second.papersAdded)
        assertEquals(1, second.papersSkipped)
        assertEquals(0, second.collectionsCreated)
        assertEquals(0, second.pdfsAdded)
        assertEquals(
            mapOf("Thesis" to 1, "مراجعة" to 1),
            db.collectionDao().observeCollections().first().associate {
                it.name to
                    it.paperCount
            }
        )
    }

    @Test
    fun theDevicesPdfIsKept() = runTest {
        library.save(Paper("W2741809807", null, "Mine", emptyList(), null, null, null, 0, false, null))
        storePdf("local-1", "%PDF-1.4 mine")

        val result = backup.apply(ready(fixture).backup)

        assertEquals(0, result.pdfsAdded)
        assertEquals("%PDF-1.4 mine", File(pdfDir, "local-1.pdf").readText())
        assertTrue(pdfDir.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    /** A format-1 archive holding [library] and the entries in [extra]. */
    private fun archive(name: String, library: String, vararg extra: Pair<String, java.io.InputStream>): File {
        val archive = File(tmp.root, name)
        java.util.zip.ZipOutputStream(archive.outputStream()).use { out ->
            fun put(entry: String, input: java.io.InputStream) {
                out.putNextEntry(java.util.zip.ZipEntry(entry))
                input.use { it.copyTo(out) }
                out.closeEntry()
            }
            put(MANIFEST_ENTRY, """{"format":1}""".byteInputStream())
            put(LIBRARY_ENTRY, library.byteInputStream())
            extra.forEach { (entry, input) -> put(entry, input) }
        }
        return archive
    }

    @Test
    fun pdfEntryNameMustMatchRef() = runTest {
        val archive = archive(
            "evil.hashiya",
            """{"papers":[{"ref":1,"openAlexId":"W1","title":"A","savedAt":1,""" +
                """"pdf":{"source":"attached","addedAt":1,"file":"pdfs/2.pdf"}},""" +
                """{"ref":2,"openAlexId":"W2","title":"B","savedAt":1}]}""",
            "pdfs/2.pdf" to "%PDF-1.4 not yours".byteInputStream()
        )

        val result = backup.apply(ready(archive).backup)

        assertEquals(0, result.pdfsAdded)
        assertEquals(1, result.pdfsMissing)
    }

    @Test
    fun aFailedMergeLeavesNoStagedPdfs() = runTest {
        val failing = ArchiveLibraryBackup(
            backupDao = db.backupDao(), fileStore = PdfFileStore(pdfDir), gate = PdfStoreGate(), contentResolver = context.contentResolver,
            workDir = File(tmp.root, "work"), appVersion = "x", now = { 1L }, newId = { "id-${++ids}" }, io = Dispatchers.Unconfined,
            merge = { _, _, _ -> throw android.database.sqlite.SQLiteException("disk I/O error") }
        )

        try {
            failing.apply(ready(fixture, failing).backup)
            org.junit.Assert.fail("Expected a BackupException")
        } catch (e: BackupException) {
            assertEquals(BackupFailure.WriteFailed, e.failure)
        }
        assertEquals(0, db.backupDao().paperCount())
        assertTrue(pdfDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aPaperWithNoOpenAlexIdIsSkippedAndTheLibraryStillLoads() = runTest {
        val blank = archive(
            "blank-id.hashiya",
            """{"papers":[{"ref":1,"openAlexId":"  ","title":"Blank","savedAt":1,""" +
                """"pdf":{"source":"attached","addedAt":1,"file":"pdfs/1.pdf"}}],""" +
                """"collections":[{"name":"Only blank","createdAt":1,"papers":[1]}]}""",
            "pdfs/1.pdf" to "%PDF-1.4 blank".byteInputStream()
        )

        val fromFixture = backup.apply(ready(fixture).backup)
        val fromBlank = backup.apply(ready(blank).backup)

        assertEquals(1, fromFixture.papersSkipped)
        assertEquals(0, fromBlank.papersAdded)
        assertEquals(1, fromBlank.papersSkipped)
        assertEquals(1, fromBlank.collectionsCreated)
        assertEquals(0, fromBlank.pdfsAdded + fromBlank.pdfsMissing)
        // Both read every row through the domain mapping, which rejects a paper with no OpenAlex id.
        assertEquals(setOf("W2741809807", "W3"), library.observeLibrary("", null, null).first().map { it.paper.openAlexId }.toSet())
        RoomCitationRepository(db.citationDao(), FakeOpenAlexLookupDataSource()).export(null)
        assertEquals(2, db.backupDao().paperCount())
        assertEquals(
            mapOf("Thesis" to 1, "مراجعة" to 1, "Only blank" to 0),
            db.collectionDao().observeCollections().first().associate { it.name to it.paperCount }
        )
        // The skipped paper's PDF was never staged.
        assertTrue(pdfDir.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }

    @Test
    fun anOversizedPdfEntryIsMissingNotOutOfSpace() = runTest {
        val roomy = ArchiveLibraryBackup(
            backupDao = db.backupDao(), fileStore = PdfFileStore(pdfDir), gate = PdfStoreGate(), contentResolver = context.contentResolver,
            workDir = File(tmp.root, "work"), appVersion = "x", now = { 1L }, newId = { "id-${++ids}" }, io = Dispatchers.Unconfined,
            // Room for any PDF the app accepts, but not for what the hostile entry declares.
            usableSpace = { MAX_PDF_BYTES }
        )
        val zeros = object : java.io.InputStream() {
            var left = MAX_PDF_BYTES + 1
            override fun read(): Int = if (left-- > 0) 0 else -1
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (left <= 0) return -1
                val n = minOf(len.toLong(), left).toInt()
                b.fill(0, off, off + n)
                left -= n
                return n
            }
        }
        val archive = archive(
            "oversized.hashiya",
            """{"papers":[{"ref":1,"openAlexId":"W1","title":"Huge","savedAt":1,""" +
                """"pdf":{"source":"attached","addedAt":1,"file":"pdfs/1.pdf"}},""" +
                """{"ref":2,"openAlexId":"W2","title":"Small","savedAt":1,"pdf":{"source":"attached","addedAt":1,"file":"pdfs/2.pdf"}}]}""",
            "pdfs/1.pdf" to zeros,
            "pdfs/2.pdf" to "%PDF-1.4 small".byteInputStream()
        )

        val result = roomy.apply(ready(archive, roomy).backup)

        assertEquals(2, result.papersAdded)
        assertEquals(1, result.pdfsAdded)
        assertEquals(1, result.pdfsMissing)
    }

    @Test
    fun aMergeThatThrowsAnythingFailsAsWriteFailed() = runTest {
        val failing = ArchiveLibraryBackup(
            backupDao = db.backupDao(), fileStore = PdfFileStore(pdfDir), gate = PdfStoreGate(), contentResolver = context.contentResolver,
            workDir = File(tmp.root, "work"), appVersion = "x", now = { 1L }, newId = { "id-${++ids}" }, io = Dispatchers.Unconfined,
            merge = { _, _, _ -> throw IllegalStateException("broken") }
        )

        val failure = runCatching { failing.apply(ready(fixture, failing).backup) }.exceptionOrNull()

        assertEquals(BackupFailure.WriteFailed, (failure as? BackupException)?.failure)
        assertTrue(pdfDir.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun aSaveThatThrowsAnythingFailsAsWriteFailed() = runTest {
        library.save(paper("W1"))
        val exported = backup.export(includePdfs = false)
        val destination = Uri.parse("content://docs/broken.hashiya")
        val broken = object : java.io.OutputStream() {
            override fun write(b: Int) = throw IllegalStateException("provider bug")
        }
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerOutputStream(destination, broken)

        val failure = runCatching { backup.save(exported, destination) }.exceptionOrNull()

        assertEquals(BackupFailure.WriteFailed, (failure as? BackupException)?.failure)
        backup.discard(exported)
    }

    @Test
    fun anOpenThatThrowsAnythingIsUnreadable() = runTest {
        val source = Uri.parse("content://docs/broken.hashiya")
        val broken = object : java.io.InputStream() {
            override fun read(): Int = throw IllegalStateException("provider bug")
        }
        org.robolectric.Shadows.shadowOf(context.contentResolver).registerInputStream(source, broken)

        assertEquals(OpenResult.Failed(OpenFailure.Unreadable), backup.open(source))
        assertEquals(emptyList<String>(), File(tmp.root, "work").list()!!.toList())
    }

    @Test
    fun cancellationAfterTheMergeStillMovesThePdfs() = runTest {
        var job: Job? = null
        val cancelling = ArchiveLibraryBackup(
            backupDao = db.backupDao(), fileStore = PdfFileStore(pdfDir), gate = PdfStoreGate(), contentResolver = context.contentResolver,
            workDir = File(tmp.root, "work"), appVersion = "x", now = { 1L }, newId = { "id-${++ids}" }, io = Dispatchers.Unconfined,
            merge = { papers, collections, time -> db.backupDao().merge(papers, collections, time).also { job?.cancel() } }
        )
        val prepared = ready(fixture, cancelling).backup

        job = launch { cancelling.apply(prepared) }
        job.join()

        val deep = db.paperDao().getByOpenAlexId("W2741809807")!!
        assertTrue(File(pdfDir, "${deep.paper.id}.pdf").readText().startsWith("%PDF-1.4"))
        assertTrue(pdfDir.listFiles().orEmpty().none { it.name.endsWith(".part") })
    }
}
