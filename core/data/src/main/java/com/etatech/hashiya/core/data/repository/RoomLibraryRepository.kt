package com.etatech.hashiya.core.data.repository

import com.etatech.hashiya.core.data.mapping.asEntities
import com.etatech.hashiya.core.data.mapping.asPaper
import com.etatech.hashiya.core.data.mapping.readingStatusOf
import com.etatech.hashiya.core.data.mapping.storedValue
import com.etatech.hashiya.core.data.search.ftsMatch
import com.etatech.hashiya.core.database.dao.PaperDao
import com.etatech.hashiya.core.database.model.CollectionPaperEntity
import com.etatech.hashiya.core.database.model.asEntity
import com.etatech.hashiya.core.database.model.asPaperNotes
import com.etatech.hashiya.core.model.LibraryPaper
import com.etatech.hashiya.core.model.Paper
import com.etatech.hashiya.core.model.PaperNotes
import com.etatech.hashiya.core.model.ReadingStatus
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class RoomLibraryRepository(private val paperDao: PaperDao, private val now: () -> Long, private val newId: () -> String) :
    LibraryRepository {
    @Inject
    constructor(paperDao: PaperDao) : this(paperDao, System::currentTimeMillis, { UUID.randomUUID().toString() })

    override fun observeLibrary(query: String, status: ReadingStatus?, collectionId: Long?): Flow<List<LibraryPaper>> =
        paperDao.observeLibrary(ftsMatch(query), status?.storedValue, collectionId).map { rows ->
            rows.map { LibraryPaper(it.asPaper(), readingStatusOf(it.paper.readingStatus)) }
        }

    override fun observeStatusCounts(query: String, collectionId: Long?): Flow<Map<ReadingStatus, Int>> =
        paperDao.observeStatusCounts(ftsMatch(query), collectionId).map { rows ->
            // Unknown stored values read as To read, so they are counted there too.
            val counts = ReadingStatus.entries.associateWith { 0 }.toMutableMap()
            rows.forEach { row -> counts.merge(readingStatusOf(row.readingStatus), row.count, Int::plus) }
            counts
        }

    override fun observeSavedIds(): Flow<Set<String>> = paperDao.observeSavedOpenAlexIds().map { it.toSet() }

    override fun observePaper(openAlexId: String): Flow<LibraryPaper?> = paperDao.observeByOpenAlexId(openAlexId).map { row ->
        row?.let { LibraryPaper(it.asPaper(), readingStatusOf(it.paper.readingStatus)) }
    }

    override fun observeNotes(openAlexId: String): Flow<PaperNotes> =
        paperDao.observeNotes(openAlexId).map { it?.asPaperNotes() ?: PaperNotes() }

    override suspend fun save(paper: Paper) {
        val entities = paper.asEntities(localId = newId(), savedAt = now(), status = ReadingStatus.ToRead)
        paperDao.insertPaperWithAuthors(entities.paper, entities.authors, entities.search)
    }

    override suspend fun setStatus(openAlexId: String, status: ReadingStatus) {
        paperDao.setStatus(openAlexId, status.storedValue)
    }

    override suspend fun saveNotes(openAlexId: String, notes: PaperNotes) {
        paperDao.saveNotes(openAlexId, notes, updatedAt = now())
    }

    override suspend fun remove(openAlexId: String): RemovedPaper? = paperDao.deleteByOpenAlexId(openAlexId)?.let { deleted ->
        val row = deleted.paper
        RemovedPaper(
            paper = row.asPaper(),
            localId = row.paper.id,
            savedAt = row.paper.savedAt,
            status = readingStatusOf(row.paper.readingStatus),
            notes = deleted.notes?.asPaperNotes() ?: PaperNotes(),
            collectionIds = deleted.collectionLinks.map { it.collectionId }.toSet(),
            citeKey = row.paper.citeKey,
            detailsFetched = row.paper.detailsFetched,
            collectionLinksAddedAt = deleted.collectionLinks.associate { it.collectionId to it.addedAt }
        )
    }

    override suspend fun restore(removed: RemovedPaper) {
        val entities = removed.paper.asEntities(
            localId = removed.localId,
            savedAt = removed.savedAt,
            status = removed.status,
            notes = removed.notes,
            citeKey = removed.citeKey,
            detailsFetched = removed.detailsFetched
        )
        // Nothing reads updated_at yet, so a restore doesn't need the original value.
        val notes = removed.notes.takeUnless { it.isEmpty }?.asEntity(removed.localId, updatedAt = now())
        val links = removed.collectionIds.map { id ->
            CollectionPaperEntity(collectionId = id, paperId = removed.localId, addedAt = removed.collectionLinksAddedAt[id] ?: now())
        }
        paperDao.insertPaperWithAuthors(entities.paper, entities.authors, entities.search, notes, links)
    }
}
