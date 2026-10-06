package com.etatech.hashiya.core.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.etatech.hashiya.core.bibtex.BibTeX
import com.etatech.hashiya.core.bibtex.CitablePaper
import com.etatech.hashiya.core.bibtex.CiteKeys
import com.etatech.hashiya.core.citation.Apa
import com.etatech.hashiya.core.citation.Ieee
import com.etatech.hashiya.core.citation.Rendering
import com.etatech.hashiya.core.data.mapping.asPaper
import com.etatech.hashiya.core.data.mapping.asPublicationDetails
import com.etatech.hashiya.core.database.dao.CitationDao
import com.etatech.hashiya.core.database.model.PaperWithAuthors
import com.etatech.hashiya.core.model.CitationStyle
import com.etatech.hashiya.core.network.NetworkException
import com.etatech.hashiya.core.network.OpenAlexLookupDataSource
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val MAX_CONCURRENT_REFETCHES = 4

/** Refetches details papers saved before v4 lack, once each; assigns cite keys once; then builds the BibTeX text. */
internal class RoomCitationRepository @Inject constructor(
    private val citationDao: CitationDao,
    private val openAlex: OpenAlexLookupDataSource
) : CitationRepository {
    override suspend fun entry(openAlexId: String, style: CitationStyle): CitationResult? {
        val stored = citationDao.getPaper(openAlexId) ?: return null
        refetch(listOf(stored))
        assignMissingKeys()
        // Null when the paper was removed while its details were being fetched.
        val row = citationDao.getPaper(openAlexId) ?: return null
        val paper = row.asPaper()
        val complete = row.hasDetails()
        return when (style) {
            CitationStyle.Bibtex -> row.citable()?.let { CitationResult(BibTeX.entry(it), complete = complete) }

            CitationStyle.Apa, CitationStyle.Ieee -> {
                val citation = if (style == CitationStyle.Apa) Apa.format(paper) else Ieee.format(paper)
                CitationResult(citation.plain, html = Rendering.html(citation), complete = complete)
            }
        }
    }

    override suspend fun export(collectionId: Long?, style: CitationStyle): CitationResult {
        refetch(citationDao.getPapers(collectionId))
        assignMissingKeys()
        // Read again: papers removed meanwhile drop out. One saved after the keys were assigned has none yet and is left out too.
        val rows = citationDao.getPapers(collectionId).filter { it.paper.citeKey != null }
        val complete = rows.all { it.hasDetails() }
        return when (style) {
            CitationStyle.Bibtex -> CitationResult(BibTeX.file(rows.mapNotNull { it.citable() }), complete = complete)

            CitationStyle.Apa, CitationStyle.Ieee -> {
                val papers = rows.map { it.asPaper() }
                val list = if (style == CitationStyle.Apa) Apa.list(papers) else Ieee.list(papers)
                CitationResult(
                    text = list.joinToString("\n\n") { it.plain },
                    rtf = Rendering.rtf(list, hangingIndent = style == CitationStyle.Apa),
                    complete = complete
                )
            }
        }
    }

    /** Fetches the details papers saved before v4 lack, at most [MAX_CONCURRENT_REFETCHES] at a time. */
    private suspend fun refetch(rows: List<PaperWithAuthors>) {
        val missing = rows.filter { !it.hasDetails() }
        if (missing.isEmpty()) return
        val permits = Semaphore(MAX_CONCURRENT_REFETCHES)
        coroutineScope {
            missing.map { row ->
                async { permits.withPermit { refetchOne(row) } }
            }.awaitAll()
        }
    }

    /** A failed request leaves details_fetched at 0, so the next export or copy asks again. */
    private suspend fun refetchOne(row: PaperWithAuthors) {
        val openAlexId = row.paper.openAlexId ?: return
        val work = try {
            openAlex.getWork(openAlexId)
        } catch (e: NetworkException) {
            return
        }
        // Both updates match no row if the paper was removed meanwhile.
        if (work == null) {
            citationDao.markDetailsFetched(row.paper.id)
        } else {
            val details = work.asPublicationDetails()
            citationDao.updatePublicationDetails(
                paperId = row.paper.id,
                workType = details.workType,
                sourceType = details.sourceType,
                publisher = details.publisher,
                volume = details.volume,
                issue = details.issue,
                firstPage = details.firstPage,
                lastPage = details.lastPage
            )
        }
    }

    /**
     * Gives every keyless saved paper a key, oldest saved first, in one transaction. Keys are assigned across the whole library,
     * not just the exported papers, so a key never depends on which collection was exported first. A clash with a key another
     * export stored at the same moment retries once against the fresh set.
     */
    private suspend fun assignMissingKeys() {
        repeat(2) { attempt ->
            val keyless = citationDao.getPapers(null).filter { it.paper.citeKey == null }
            if (keyless.isEmpty()) return
            val keys = CiteKeys.assign(keyless.map { it.asPaper() }, citationDao.allCiteKeys().toSet())
            try {
                citationDao.assignCiteKeys(keyless.map { it.paper.id }.zip(keys).toMap())
                return
            } catch (e: SQLiteConstraintException) {
                if (attempt == 1) throw e
            }
        }
    }

    private fun PaperWithAuthors.citable(): CitablePaper? = paper.citeKey?.let { CitablePaper(asPaper(), it) }

    /** Whether the stored details are as complete as they will get. A paper with no OpenAlex id has nothing to refetch. */
    private fun PaperWithAuthors.hasDetails(): Boolean = paper.detailsFetched || paper.openAlexId == null
}
