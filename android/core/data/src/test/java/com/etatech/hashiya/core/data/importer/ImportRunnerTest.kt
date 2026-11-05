package com.etatech.hashiya.core.data.importer

import com.etatech.hashiya.core.analytics.AnalyticsEvent
import com.etatech.hashiya.core.analytics.ImportFileFormat
import com.etatech.hashiya.core.analytics.ImportResult
import com.etatech.hashiya.core.analytics.LibrarySize
import com.etatech.hashiya.core.analytics.MatchedShare
import com.etatech.hashiya.core.model.ImportCounts
import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportJob
import com.etatech.hashiya.core.model.ImportState
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.isLocal
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.NetworkFailure
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import com.etatech.hashiya.core.network.model.NetworkMeta
import com.etatech.hashiya.core.network.model.NetworkWork
import com.etatech.hashiya.core.network.model.NetworkWorksResponse
import com.etatech.hashiya.core.testing.FakeAnalytics
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportRunnerTest {
    private val store = FakeImportStore()
    private val files = ImportFileStore(Files.createTempDirectory("imports").toFile())
    private val cache = ImportLookupCache()
    private val analytics = FakeAnalytics()
    private var ids = 0

    /** OpenAlex knows these DOIs; every list request is recorded. */
    private class Source(var known: Set<String>, var failure: NetworkFailure? = null) : OpenAlexLookupDataSource {
        val requests = mutableListOf<String>()

        override suspend fun getWork(id: String): NetworkWork? = error("unused")

        override suspend fun findWorks(filter: String, perPage: Int): NetworkWorksResponse {
            requests += filter
            failure?.let { throw NetworkException(it) }
            val results = filter.removePrefix("doi:").split('|').filter { it in known }.map { doi ->
                NetworkWork(
                    id = "https://openalex.org/W${doi.hashCode().toUInt()}",
                    doi = "https://doi.org/$doi",
                    displayName = "OpenAlex: $doi"
                )
            }
            return NetworkWorksResponse(meta = NetworkMeta(count = results.size.toLong()), results = results)
        }
    }

    private val source = Source(known = emptySet())

    private fun runner() =
        ImportRunner(store, files, OpenAlexDoiMatcher(source, pause = {}), cache, analytics, now = { 1_000 }, newId = { "id${++ids}" })

    /** testdata/import at the repository root, beside android/. */
    private fun fixture(name: String): String {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "testdata/import").isDirectory) dir = dir.parentFile
        return File(checkNotNull(dir), "testdata/import/$name").readText()
    }

    private suspend fun job(text: String, addToCollection: Boolean = true, name: String = "thesis"): Long {
        val id = store.create(ImportJob(0, "$name.bib", ImportState.Analyzing, addToCollection = addToCollection, collectionName = name), 1)
        files.write(id, text)
        return id
    }

    private suspend fun analyzeAndConfirm(id: Long) {
        runner().run(id)
        val ready = checkNotNull(store.job(id))
        assertEquals(ImportState.Ready, ready.state)
        store.replaceIf(ImportState.Ready, ready.copy(state = ImportState.Importing))
        runner().run(id)
    }

    @Test
    fun `analysis counts what the file holds and saves nothing`() = runTest {
        source.known = setOf("10.1038/nature14539", "10.48550/arxiv.1706.03762")
        val id = job(fixture("zotero.bib"))
        runner().run(id)
        val job = checkNotNull(store.job(id))
        assertEquals(ImportState.Ready, job.state)
        assertEquals(ImportCounts(found = 4, matched = 2, local = 2), job.counts)
        assertTrue(store.papers.isEmpty())
        assertEquals(1, source.requests.size)
    }

    @Test
    fun `repeats, papers already saved and unreadable entries are counted`() = runTest {
        store.preSave(Paper("W9", "10.1145/3394486.3403111", "Saved", emptyList(), 2020, null, null, 0, false, null))
        val id = job(fixture("nasty.bib"))
        runner().run(id)
        val counts = checkNotNull(store.job(id)).counts
        assertEquals(ImportCounts(found = 8, matched = 0, local = 6, inLibrary = 1, repeated = 1, unreadable = 2), counts)
    }

    @Test
    fun `a matched work that is already saved counts as in the library`() = runTest {
        source.known = setOf("10.1038/nature14539")
        store.preSave(
            Paper("W${"10.1038/nature14539".hashCode().toUInt()}", null, "Deep learning", emptyList(), 2015, null, null, 0, false, null)
        )
        val id = job(fixture("zotero.bib"))
        runner().run(id)
        assertEquals(1, checkNotNull(store.job(id)).counts.inLibrary)
    }

    @Test
    fun `offline lookups are counted as not checked and kept from the file`() = runTest {
        source.failure = NetworkFailure.Connectivity
        val id = job(fixture("zotero.bib"))
        runner().run(id)
        assertEquals(ImportCounts(found = 4, local = 4, notChecked = 3), checkNotNull(store.job(id)).counts)
    }

    @Test
    fun `a file with nothing new fails as nothing to import`() = runTest {
        val text = "@article{a, title = {Same}, year = 2020}"
        store.preSave(Paper("W1", null, "Same", emptyList(), 2020, null, null, 0, false, null))
        val id = job(text)
        runner().run(id)
        val job = checkNotNull(store.job(id))
        assertEquals(ImportState.Failed to ImportFailure.NothingToImport, job.state to job.failure)
        assertEquals(
            listOf(AnalyticsEvent.Import(ImportFileFormat.Bibtex, ImportResult.NothingNew, LibrarySize.UpTo50, MatchedShare.None, false)),
            analytics.events
        )
    }

    @Test
    fun `a file that isn't BibTeX or RIS fails as unsupported, a missing one as unreadable`() = runTest {
        val id = job("just some notes")
        runner().run(id)
        assertEquals(ImportFailure.Unsupported, store.job(id)?.failure)
        val gone = job("TY  - JOUR\nTI  - T\nER  - ")
        files.delete(gone)
        runner().run(gone)
        assertEquals(ImportFailure.Unreadable, store.job(gone)?.failure)
        assertEquals(
            listOf(ImportResult.Unsupported, ImportResult.Unreadable),
            analytics.events.map {
                (it as AnalyticsEvent.Import).result
            }
        )
    }

    @Test
    fun `importing saves matched papers from OpenAlex and the rest from the file, with their cite keys, into the collection`() = runTest {
        source.known = setOf("10.1038/nature14539")
        val id = job(fixture("zotero.bib"))
        analyzeAndConfirm(id)
        val job = checkNotNull(store.job(id))
        assertEquals(ImportState.Done, job.state)
        assertEquals(4 to 4, job.processed to job.imported)
        val byKey = store.papers.associateBy { it.citeKey }
        assertEquals("OpenAlex: 10.1038/nature14539", byKey.getValue("lecun_deep_2015").paper.title)
        assertTrue(byKey.getValue("muller_lernverfahren_2019").paper.isLocal)
        assertEquals("Lernverfahren für große Sprachmodelle", byKey.getValue("muller_lernverfahren_2019").paper.title)
        assertEquals(mapOf("thesis" to 100L), store.collections)
        assertEquals(100L, job.collectionId)
        assertEquals(4, store.collectionLinks.size)
        // The import phase used the analysis's lookups.
        assertEquals(1, source.requests.size)
        assertEquals(
            listOf(
                AnalyticsEvent.Import(ImportFileFormat.Bibtex, ImportResult.Ok, LibrarySize.UpTo50, MatchedShare.Some, toCollection = true)
            ),
            analytics.events
        )
    }

    @Test
    fun `a taken collection name gets a number, and no collection when it isn't wanted`() = runTest {
        store.createCollection("Thesis", 1)
        val id = job("@article{a, title = {A}}")
        analyzeAndConfirm(id)
        assertEquals("thesis (2)", store.job(id)?.collectionName)
        val none = job("@article{b, title = {B}}", addToCollection = false)
        analyzeAndConfirm(none)
        assertNull(store.job(none)?.collectionId)
        assertEquals(2, store.collections.size)
    }

    @Test
    fun `a cite key another paper holds gets a suffix, and an unsafe one is dropped`() = runTest {
        store.preSave(Paper("W1", null, "Other", emptyList(), 2001, null, null, 0, false, null), citeKey = "smith2020")
        val id = job("@article{smith2020, title = {A}}\n@article{bad#key, title = {B}}", addToCollection = false)
        analyzeAndConfirm(id)
        val keys = store.papers.filter { it.jobId == id }.associate { it.paper.title to it.citeKey }
        assertEquals(mapOf("A" to "smith2020a", "B" to null), keys)
    }

    @Test
    fun `an import interrupted after a batch resumes without saving anything twice`() = runTest {
        val text = (1..120).joinToString("\n") { "@article{k$it, title = {Paper number $it}, doi = {10.1/$it}}" }
        source.known = (1..120).map { "10.1/$it" }.toSet()
        val id = job(text)
        runner().run(id)
        val ready = checkNotNull(store.job(id))
        store.replaceIf(ImportState.Ready, ready.copy(state = ImportState.Importing))
        store.beforeBatch = { if (it == 2) error("process died") }
        runCatching { runner().run(id) }
        assertEquals(50, store.papers.size)
        // A new process: no cached lookups, no failure.
        cache.remove(id)
        store.beforeBatch = {}
        runner().run(id)
        val job = checkNotNull(store.job(id))
        assertEquals(ImportState.Done, job.state)
        assertEquals(120, store.papers.size)
        assertEquals(120 to 120, job.processed to job.imported)
        assertEquals(120, store.papers.map { it.paper.openAlexId }.distinct().size)
        assertEquals(120, store.collectionLinks.size)
        // One event for the job, from the run that finished it.
        assertEquals(1, analytics.events.size)
        // The resumed run asked OpenAlex only about the 70 papers it still had to save.
        assertEquals(listOf(50, 50, 20, 50, 20), source.requests.map { it.split('|').size })
    }

    @Test
    fun `cancelling stops between batches and keeps what is saved`() = runTest {
        val text = (1..120).joinToString("\n") { "@article{k$it, title = {Paper number $it}}" }
        val id = job(text, addToCollection = false)
        runner().run(id)
        val ready = checkNotNull(store.job(id))
        store.replaceIf(ImportState.Ready, ready.copy(state = ImportState.Importing))
        store.beforeBatch = { if (it == 1) store.force(ready.copy(state = ImportState.Cancelled)) }
        runner().run(id)
        assertEquals(ImportState.Cancelled, store.job(id)?.state)
        assertEquals(50, store.papers.size)
        // The repository's cancel sends the event; the runner that noticed it sends nothing.
        assertEquals(emptyList<AnalyticsEvent>(), analytics.events)
    }

    @Test
    fun `fail marks a running job unreadable and leaves a finished one alone`() = runTest {
        val id = job("@article{a, title = {A}}")
        runner().fail(id)
        assertEquals(ImportState.Failed to ImportFailure.Unreadable, store.job(id)?.let { it.state to it.failure })
        assertEquals(listOf(ImportResult.Unreadable), analytics.events.map { (it as AnalyticsEvent.Import).result })
    }

    @Test
    fun `a job in any other state is left alone`() = runTest {
        val id = job("@article{a, title = {A}}")
        store.force(checkNotNull(store.job(id)).copy(state = ImportState.Ready))
        runner().run(id)
        assertEquals(ImportState.Ready, store.job(id)?.state)
        assertTrue(source.requests.isEmpty())
    }
}
