package com.etatech.hashiya.core.data.importer

import com.etatech.hashiya.core.analytics.Analytics
import com.etatech.hashiya.core.analytics.ImportResult
import com.etatech.hashiya.core.importer.ExistingLibrary
import com.etatech.hashiya.core.importer.ImportCiteKeys
import com.etatech.hashiya.core.importer.ImportParseResult
import com.etatech.hashiya.core.importer.ImportParser
import com.etatech.hashiya.core.importer.ImportPlanner
import com.etatech.hashiya.core.importer.ImportedEntry
import com.etatech.hashiya.core.importer.PlannedEntry
import com.etatech.hashiya.core.importer.titleKey
import com.etatech.hashiya.core.importer.toLocalPaper
import com.etatech.hashiya.core.model.COLLECTION_NAME_MAX_LENGTH
import com.etatech.hashiya.core.model.ImportCounts
import com.etatech.hashiya.core.model.ImportFailure
import com.etatech.hashiya.core.model.ImportJob
import com.etatech.hashiya.core.model.ImportState
import com.etatech.hashiya.core.model.Paper
import java.util.UUID
import javax.inject.Inject

/** Papers saved per transaction. */
internal const val IMPORT_BATCH_SIZE = 50

/** How many "name (n)" variants to try before giving up on the collection. */
private const val MAX_COLLECTION_NAME_TRIES = 100

/**
 * Runs one phase of a job, chosen by its stored state (spec §7.5). Safe to run again at any point: the import phase plans against
 * the library as it is, so papers it saved before an interruption count as imported and nothing is saved twice.
 */
internal class ImportRunner(
    private val store: ImportStore,
    private val files: ImportFileStore,
    private val matcher: OpenAlexDoiMatcher,
    private val cache: ImportLookupCache,
    private val analytics: Analytics,
    private val now: () -> Long,
    private val newId: () -> String
) {
    @Inject
    constructor(store: ImportStore, files: ImportFileStore, matcher: OpenAlexDoiMatcher, cache: ImportLookupCache, analytics: Analytics) :
        this(store, files, matcher, cache, analytics, System::currentTimeMillis, { UUID.randomUUID().toString() })

    suspend fun run(jobId: Long) {
        val job = store.job(jobId) ?: return
        when (job.state) {
            ImportState.Analyzing -> analyze(job)
            ImportState.Importing -> import(job)
            ImportState.Ready, ImportState.Done, ImportState.Failed, ImportState.Cancelled -> Unit
        }
    }

    /** After an unexpected error: a running job fails as Unreadable, keeping whatever it saved. */
    suspend fun fail(jobId: Long) {
        val job = store.job(jobId) ?: return
        if (!job.isRunning) return
        if (store.replaceIf(job.state, job.copy(state = ImportState.Failed, failure = ImportFailure.Unreadable))) {
            analytics.log(importEvent(job, ImportResult.Unreadable, format = null))
        }
    }

    private suspend fun analyze(job: ImportJob) {
        val parsed = parse(job) ?: return
        val keys = store.libraryKeys(job.id)
        val plan = ImportPlanner.plan(parsed.entries, keys.existingLibrary(includeImported = false))
        val matches = matcher.match(plan.lookups())
        cache[job.id] = matches
        val outcomes = resolve(plan, matches, keys)
        val counts = ImportCounts(
            found = parsed.entries.size,
            matched = outcomes.count { it is Outcome.Matched },
            local = outcomes.count { it is Outcome.Local },
            inLibrary = outcomes.count { it is Outcome.InLibrary },
            repeated = outcomes.count { it is Outcome.Repeated },
            unreadable = parsed.issues.size,
            notChecked = outcomes.count { it is Outcome.Local && it.notChecked }
        )
        val next = if (counts.toImport == 0) {
            job.copy(state = ImportState.Failed, failure = ImportFailure.NothingToImport, counts = counts)
        } else {
            job.copy(state = ImportState.Ready, counts = counts)
        }
        if (store.replaceIf(ImportState.Analyzing, next) && next.state == ImportState.Failed) {
            analytics.log(importEvent(next, ImportResult.NothingNew, parsed.format))
        }
    }

    private suspend fun import(started: ImportJob) {
        val parsed = parse(started) ?: return
        val job = withCollection(started) ?: return
        val keys = store.libraryKeys(job.id)
        val plan = ImportPlanner.plan(parsed.entries, keys.existingLibrary(includeImported = true))
        val lookups = plan.lookups()
        val cached = cache[job.id]
        val matches = if (cached != null && lookups.all { it in cached.found || it in cached.notFound || it in cached.notChecked }) {
            cached
        } else {
            matcher.match(lookups).also { cache[job.id] = it }
        }
        val outcomes = resolve(plan, matches, keys)
        val takenKeys = keys.mapNotNull { it.citeKey }.toMutableSet()
        var processed = outcomes.count { it is Outcome.AlreadyImported }
        val toSave = outcomes.filter { it is Outcome.Matched || it is Outcome.Local }
        if (toSave.isEmpty()) {
            store.saveBatch(job.id, emptyList(), job.collectionId, now(), processed)
        }
        for (batch in toSave.chunked(IMPORT_BATCH_SIZE)) {
            // Cancel moves the job on; stop between batches, keeping what is saved.
            if (store.job(job.id)?.state != ImportState.Importing) return
            val papers = batch.map { outcome ->
                val key = ImportCiteKeys.sanitize(outcome.entry.citeKey)?.let { ImportCiteKeys.resolve(it, takenKeys) }
                key?.let { takenKeys += it }
                val paper = when (outcome) {
                    is Outcome.Matched -> outcome.paper
                    else -> outcome.entry.toLocalPaper(newId())
                }
                PaperToSave(paper, key)
            }
            processed += batch.size
            store.saveBatch(job.id, papers, job.collectionId, now(), processed)
        }
        val saved = store.job(job.id) ?: return
        val done = saved.copy(state = ImportState.Done)
        if (store.replaceIf(ImportState.Importing, done)) analytics.log(importEvent(done, ImportResult.Ok, parsed.format))
        cache.remove(job.id)
    }

    /** The parsed file, or null after marking the job failed. */
    private suspend fun parse(job: ImportJob): ImportParseResult? {
        val text = files.read(job.id)
        val parsed = text?.let(ImportParser::parse)
        if (parsed == null) {
            val failure = if (text == null) ImportFailure.Unreadable else ImportFailure.Unsupported
            if (store.replaceIf(job.state, job.copy(state = ImportState.Failed, failure = failure))) {
                val result = if (text == null) ImportResult.Unreadable else ImportResult.Unsupported
                analytics.log(importEvent(job, result, format = null))
            }
        }
        return parsed
    }

    /** Creates the collection on the first run when asked to; null when the job moved on meanwhile. */
    private suspend fun withCollection(job: ImportJob): ImportJob? {
        if (!job.addToCollection || job.collectionId != null) return job
        val base = job.collectionName.trim().take(COLLECTION_NAME_MAX_LENGTH).ifEmpty { "Import" }
        for (n in 1..MAX_COLLECTION_NAME_TRIES) {
            val name = if (n == 1) base else "${base.take(COLLECTION_NAME_MAX_LENGTH - " ($n)".length).trimEnd()} ($n)"
            val id = store.createCollection(name, now()) ?: continue
            val updated = job.copy(collectionId = id, collectionName = name)
            return if (store.replaceIf(ImportState.Importing, updated)) updated else null
        }
        // Every name taken: import without a collection rather than not at all.
        val updated = job.copy(addToCollection = false)
        return if (store.replaceIf(ImportState.Importing, updated)) updated else null
    }

    private sealed interface Outcome {
        val entry: ImportedEntry

        data class Matched(override val entry: ImportedEntry, val paper: Paper) : Outcome

        data class Local(override val entry: ImportedEntry, val notChecked: Boolean) : Outcome

        data class InLibrary(override val entry: ImportedEntry) : Outcome

        data class AlreadyImported(override val entry: ImportedEntry) : Outcome

        data class Repeated(override val entry: ImportedEntry) : Outcome
    }

    /**
     * What happens to each entry once its DOI is looked up. A match whose work is already saved is in the library; two entries
     * matching the same work are one paper.
     */
    private fun resolve(plan: List<PlannedEntry>, matches: DoiMatches, keys: List<LibraryKey>): List<Outcome> {
        val savedWorks = keys.filterNot { it.importedByJob }.mapNotNull { it.openAlexId }.toSet()
        val importedWorks = keys.filter { it.importedByJob }.mapNotNull { it.openAlexId }.toSet()
        val matchedWorks = mutableSetOf<String>()
        return plan.map { planned ->
            val entry = planned.entry
            when (planned) {
                is PlannedEntry.Repeated -> Outcome.Repeated(entry)

                is PlannedEntry.InLibrary -> Outcome.InLibrary(entry)

                is PlannedEntry.AlreadyImported -> Outcome.AlreadyImported(entry)

                is PlannedEntry.ToImport -> {
                    val paper = entry.doi?.let { matches.found[it] }
                    when {
                        paper == null -> Outcome.Local(entry, notChecked = entry.doi != null && entry.doi in matches.notChecked)
                        paper.openAlexId in importedWorks -> Outcome.AlreadyImported(entry)
                        paper.openAlexId in savedWorks -> Outcome.InLibrary(entry)
                        !matchedWorks.add(paper.openAlexId) -> Outcome.Repeated(entry)
                        else -> Outcome.Matched(entry, paper)
                    }
                }
            }
        }
    }

    private fun List<PlannedEntry>.lookups(): List<String> = filterIsInstance<PlannedEntry.ToImport>().mapNotNull { it.entry.doi }

    private fun List<LibraryKey>.existingLibrary(includeImported: Boolean): ExistingLibrary {
        val imported = if (includeImported) filter { it.importedByJob } else emptyList()
        return ExistingLibrary(
            dois = mapNotNull { it.doi }.toSet(),
            titleKeys = mapNotNull { titleKey(it.title, it.year) }.toSet(),
            importedDois = imported.mapNotNull { it.doi }.toSet(),
            importedTitleKeys = imported.mapNotNull { titleKey(it.title, it.year) }.toSet()
        )
    }
}
