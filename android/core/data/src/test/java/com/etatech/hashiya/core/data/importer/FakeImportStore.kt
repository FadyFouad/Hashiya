package com.etatech.hashiya.core.data.importer

import com.etatech.hashiya.core.model.ImportJob
import com.etatech.hashiya.core.model.ImportState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** An in-memory [ImportStore] with the same rules as the Room one. */
internal class FakeImportStore : ImportStore {
    data class Saved(val jobId: Long?, val paper: com.etatech.hashiya.core.model.Paper, val citeKey: String?)

    private val jobs = MutableStateFlow<Map<Long, ImportJob>>(emptyMap())
    private var nextId = 1L
    val papers = mutableListOf<Saved>()
    val collections = mutableMapOf<String, Long>()
    val collectionLinks = mutableSetOf<Pair<Long, String>>()

    /** Called before each batch is saved, with the batch number from 1; throw to simulate the process dying. */
    var beforeBatch: (Int) -> Unit = {}
    private var batches = 0

    /** A paper saved before the import, not by it. */
    fun preSave(paper: com.etatech.hashiya.core.model.Paper, citeKey: String? = null) {
        papers += Saved(null, paper, citeKey)
    }

    override fun observeLatest(): Flow<ImportJob?> = jobs.map { all -> all.maxByOrNull { it.key }?.value }

    override suspend fun job(id: Long): ImportJob? = jobs.value[id]

    override suspend fun running(): ImportJob? = jobs.value.values.filter { it.isRunning }.maxByOrNull { it.id }

    override suspend fun create(job: ImportJob, createdAt: Long): Long {
        val id = nextId++
        jobs.value += id to job.copy(id = id)
        return id
    }

    override suspend fun replaceIf(expected: ImportState, job: ImportJob): Boolean {
        if (jobs.value[job.id]?.state != expected) return false
        jobs.value += job.id to job
        return true
    }

    /** Moves a job directly, as a cancel from another thread would. */
    fun force(job: ImportJob) {
        jobs.value += job.id to job
    }

    override suspend fun delete(id: Long) {
        jobs.value -= id
        papers.replaceAll { if (it.jobId == id) it.copy(jobId = null) else it }
    }

    override suspend fun libraryKeys(jobId: Long): List<LibraryKey> = papers.map {
        LibraryKey(it.paper.openAlexId, it.paper.doi, it.paper.title, it.paper.year, it.citeKey, importedByJob = it.jobId == jobId)
    }

    override suspend fun createCollection(name: String, createdAt: Long): Long? {
        if (collections.keys.any { it.trim().lowercase() == name.trim().lowercase() }) return null
        val id = 100L + collections.size
        collections[name] = id
        return id
    }

    override suspend fun saveBatch(jobId: Long, papers: List<PaperToSave>, collectionId: Long?, savedAt: Long, processed: Int): Int {
        if (papers.isNotEmpty()) beforeBatch(++batches)
        var saved = 0
        papers.forEach { toSave ->
            if (this.papers.none { it.paper.openAlexId == toSave.paper.openAlexId }) {
                val key = toSave.citeKey?.takeIf { key -> this.papers.none { it.citeKey == key } }
                this.papers += Saved(jobId, toSave.paper, key)
                saved++
            }
        }
        if (collectionId != null && collectionId in collections.values) {
            this.papers.filter { it.jobId == jobId }.forEach { collectionLinks += collectionId to it.paper.openAlexId }
        }
        val job = checkNotNull(jobs.value[jobId])
        jobs.value += jobId to job.copy(processed = processed, imported = this.papers.count { it.jobId == jobId })
        return saved
    }
}
